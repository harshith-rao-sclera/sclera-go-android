package com.example.sclerago.di

import com.example.sclerago.data.remote.AuthApi
import com.example.sclerago.data.remote.ScleraApiService
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
}
