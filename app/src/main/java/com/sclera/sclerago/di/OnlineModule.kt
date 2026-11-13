package com.sclera.sclerago.di

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.local.EncryptedFileCache
import com.sclera.sclerago.data.online.AccessStore
import com.sclera.sclerago.data.online.CurrentAccessRepositoryImpl
import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.AccessTokenProvider
import com.sclera.sclerago.data.remote.adc.AdcApiService
import com.sclera.sclerago.data.remote.adc.AdcConfig
import com.sclera.sclerago.data.remote.adc.AdcJobsService
import com.sclera.sclerago.data.remote.adc.forAdc
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.repository.AuthRepositoryImpl
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The OkHttp client for the asset data collection API (the shared client plus the Sclera token handling). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdcClient

/** The online ADC API: its HTTP client and service, and the repositories over it. Everything that holds user data is wiped on sign-out. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnlineModule {
    @Binds abstract fun properties(impl: PropertyRepositoryImpl): PropertyRepository
    @Binds abstract fun onboarding(impl: OnboardingRepositoryImpl): OnboardingRepository
    @Binds abstract fun access(impl: AccessRepositoryImpl): AccessRepository
    @Binds abstract fun currentAccess(impl: CurrentAccessRepositoryImpl): CurrentAccessRepository
    @Binds abstract fun cache(impl: EncryptedFileCache): EncryptedCache

    @Binds @IntoSet abstract fun clearCache(impl: EncryptedFileCache): UserDataCleaner
    @Binds @IntoSet abstract fun clearProperties(impl: PropertyStore): UserDataCleaner
    @Binds @IntoSet abstract fun clearAccess(impl: AccessStore): UserDataCleaner
    @Binds @IntoSet abstract fun clearCurrentAccess(impl: CurrentAccessRepositoryImpl): UserDataCleaner

    companion object {
        @Provides
        fun tokens(auth: AuthRepositoryImpl): AccessTokenProvider = auth

        @Provides
        @Singleton
        @AdcClient
        fun adcClient(base: OkHttpClient, tokens: AccessTokenProvider): OkHttpClient = base.forAdc(tokens)

        @Provides
        @Singleton
        fun adcService(@AdcClient client: OkHttpClient): AdcApiService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcApiService::class.java)

        @Provides
        @Singleton
        fun adcJobsService(@AdcClient client: OkHttpClient): AdcJobsService =
            Retrofit.Builder().baseUrl(AdcConfig.API_URL).client(client).build().create(AdcJobsService::class.java)
    }
}
