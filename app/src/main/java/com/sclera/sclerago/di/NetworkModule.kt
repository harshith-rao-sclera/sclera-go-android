package com.sclera.sclerago.di

import com.sclera.sclerago.data.remote.AdcApi
import com.sclera.sclerago.data.remote.AdcApiService
import com.sclera.sclerago.data.remote.AuthApi
import com.sclera.sclerago.data.remote.ScleraApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** Retrofit / OkHttp for the Sclera APIs. [AuthApi] is built from its @Inject constructor. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = AuthApi.okHttpClient()

    @Provides
    @Singleton
    fun provideScleraApiService(client: OkHttpClient): ScleraApiService = AuthApi.service(client = client)

    /** The ADC API (user details, properties) over the same client. */
    @Provides
    @Singleton
    fun provideAdcApiService(client: OkHttpClient): AdcApiService = AdcApi.service(client = client)
}
