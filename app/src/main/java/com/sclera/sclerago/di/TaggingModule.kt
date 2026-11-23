package com.sclera.sclerago.di

import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.data.remote.tagging.TagService
import com.sclera.sclerago.data.offline.AssetTagFlagStore
import com.sclera.sclerago.data.offline.GlobalQrTagStore
import com.sclera.sclerago.data.offline.AdcAssetTagFlagStore
import com.sclera.sclerago.data.offline.AdcTagHistory
import com.sclera.sclerago.data.offline.QrTagStore
import com.sclera.sclerago.data.repository.ConnectionAwareTaggingRepository
import com.sclera.sclerago.data.repository.TagServerInfoImpl
import com.sclera.sclerago.domain.repository.QrDirectionSource
import com.sclera.sclerago.domain.repository.TagHistory
import com.sclera.sclerago.domain.repository.TagServerInfo
import com.sclera.sclerago.domain.repository.TaggingRepository
import com.sclera.sclerago.services.tagging.AndroidQrDirectionMeterFactory
import com.sclera.sclerago.services.tagging.PlaceholderQrDirectionSource
import com.sclera.sclerago.services.tagging.QrDirectionMeterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

/** Tagging: the Sclera server that holds the physical tags (`/vdms/api`, the host that serves the sign-in, not the `/v1` API). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaggingModule {
    @Binds abstract fun tagging(impl: ConnectionAwareTaggingRepository): TaggingRepository
    @Binds abstract fun qrTags(impl: GlobalQrTagStore): QrTagStore
    @Binds abstract fun tagFlags(impl: AdcAssetTagFlagStore): AssetTagFlagStore
    @Binds abstract fun tagHistory(impl: AdcTagHistory): TagHistory

    @Binds abstract fun server(impl: TagServerInfoImpl): TagServerInfo

    /** The direction for a QR code scanned on a device that cannot measure one (no rotation-vector sensor); others carry their measured direction. */
    @Binds abstract fun qrDirection(impl: PlaceholderQrDirectionSource): QrDirectionSource

    @Binds abstract fun directionMeters(impl: AndroidQrDirectionMeterFactory): QrDirectionMeterFactory

    companion object {
        @Provides
        @Singleton
        fun tagService(@AdcClient client: OkHttpClient): TagService =
            Retrofit.Builder().baseUrl(TAG_API_URL).client(client).build().create(TagService::class.java)

        /** The server's tag API, https, ending in `/`. */
        const val TAG_API_URL = AuthConfig.APP_URL + "/vdms/api/"
    }
}
