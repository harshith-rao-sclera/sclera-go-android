package com.sclera.sclerago.di

import android.content.Context
import com.sclera.sclerago.data.local.AndroidNetworkMonitor
import com.sclera.sclerago.data.local.AuthStore
import com.sclera.sclerago.data.local.SecretCipher
import com.sclera.sclerago.data.local.keystoreSecretCipher
import com.sclera.sclerago.data.remote.AdcApi
import com.sclera.sclerago.data.remote.AuthApi
import com.sclera.sclerago.data.remote.AuthorizedCaller
import com.sclera.sclerago.data.repository.AuthRepositoryImpl
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.di.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** App-wide data-layer bindings. Use cases and ViewModels are built by Hilt from their @Inject constructors. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** One session per process; the saved session is restored as soon as the repository is created. */
    @Provides
    @Singleton
    fun provideAuthRepositoryImpl(
        @ApplicationContext context: Context,
        api: AuthApi,
        adc: AdcApi,
        cipher: SecretCipher,
        @MainDispatcher dispatcher: CoroutineDispatcher,
    ): AuthRepositoryImpl =
        AuthRepositoryImpl(AuthStore(context, cipher), api, adc, dispatcher).also { it.restore() }

    @Provides
    fun provideAuthRepository(impl: AuthRepositoryImpl): AuthRepository = impl

    /** API calls with the same session's token, so every caller shares one token and one refresh. */
    @Provides
    fun provideAuthorizedCaller(impl: AuthRepositoryImpl): AuthorizedCaller = impl

    /** Encrypts what the app keeps on the device; the key never leaves the Android Keystore. */
    @Provides
    @Singleton
    fun provideSecretCipher(): SecretCipher = keystoreSecretCipher()

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor = AndroidNetworkMonitor(context)
}
