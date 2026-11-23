package com.sclera.sclerago.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.sclera.sclerago.data.local.ProfilePhotoCache
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The OkHttp client for people's profile photos only. It is built on its own, never from the API's clients, so the
 * photo hosts never see the user's access token or any other header the API calls carry.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PhotoClient

/**
 * People's profile photos (the API's `imageUrl`), loaded with Coil: one [ImageLoader] for the app (the Compose side gets
 * it through [com.sclera.sclerago.ScleraApplication]), with a memory cache and a small disk cache in the app's private
 * cache folder, both wiped on sign-out ([ProfilePhotoCache]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoModule {
    @Binds @IntoSet abstract fun clearPhotos(impl: ProfilePhotoCache): UserDataCleaner

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val CALL_TIMEOUT_SECONDS = 30L
        private const val DISK_CACHE_BYTES = 20L * 1024 * 1024
        private const val MEMORY_CACHE_PERCENT = 0.1
        private const val CACHE_DIR = "profile_photos"

        /**
         * HTTPS only (every request, redirects included, is checked as it goes out; an https → http redirect is not
         * followed), no cookies, no logging, no headers added, and its own timeouts.
         */
        @Provides
        @Singleton
        @PhotoClient
        fun photoClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cookieJar(CookieJar.NO_COOKIES)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (!chain.request().url.isHttps) throw IOException("Refused a profile photo request that is not HTTPS")
                chain.proceed(chain.request())
            }
            .build()

        @Provides
        @Singleton
        fun imageLoader(@ApplicationContext context: Context, @PhotoClient client: OkHttpClient): ImageLoader =
            ImageLoader.Builder(context)
                // Only the fetcher below: Coil's own (found through its service loader) would use a plain OkHttp client
                // without the HTTPS-only check.
                .serviceLoaderEnabled(false)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.cacheDir, CACHE_DIR).toOkioPath())
                        .maxSizeBytes(DISK_CACHE_BYTES)
                        .build()
                }
                .build()
    }
}
