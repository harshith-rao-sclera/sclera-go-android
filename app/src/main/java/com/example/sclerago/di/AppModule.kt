package com.example.sclerago.di

import android.content.Context
import com.example.sclerago.data.local.AndroidNetworkMonitor
import com.example.sclerago.data.local.AuthStore
import com.example.sclerago.data.repository.AuthRepositoryImpl
import com.example.sclerago.domain.repository.AuthRepository
import com.example.sclerago.domain.repository.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** App-wide data-layer bindings. Use cases and ViewModels are built by Hilt from their @Inject constructors. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** One session per process; the saved session is restored as soon as the repository is created. */
    @Provides
    @Singleton
    fun provideAuthRepository(@ApplicationContext context: Context): AuthRepository =
        AuthRepositoryImpl(AuthStore(context)).also { it.restore() }

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor = AndroidNetworkMonitor(context)
}
