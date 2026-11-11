package com.example.sclerago.di

import android.content.Context
import com.example.sclerago.data.local.AndroidNetworkMonitor
import com.example.sclerago.data.local.AuthStore
import com.example.sclerago.data.repository.AuthRepositoryImpl
import com.example.sclerago.domain.repository.AuthRepository
import com.example.sclerago.domain.repository.NetworkMonitor
import com.example.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.example.sclerago.domain.usecase.auth.ClearLoginCookiesUseCase
import com.example.sclerago.domain.usecase.auth.CompleteLoginUseCase
import com.example.sclerago.domain.usecase.auth.IsOnlineUseCase
import com.example.sclerago.domain.usecase.auth.LogoutUseCase
import com.example.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.sclerago.domain.usecase.auth.ValidateEmailUseCase

/** Manual dependency injection: one graph per process, built from the application context. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // ---- data
    val authRepository: AuthRepository by lazy { AuthRepositoryImpl(AuthStore(appContext)).also { it.restore() } }
    val networkMonitor: NetworkMonitor by lazy { AndroidNetworkMonitor(appContext) }

    // ---- use cases
    val observeAuthState get() = ObserveAuthStateUseCase(authRepository)
    val completeLogin get() = CompleteLoginUseCase(authRepository)
    val clearLoginCookies get() = ClearLoginCookiesUseCase(authRepository)
    val checkSessionOnForeground get() = CheckSessionOnForegroundUseCase(authRepository)
    val logout get() = LogoutUseCase(authRepository)
    val isOnline get() = IsOnlineUseCase(networkMonitor)
    val validateEmail get() = ValidateEmailUseCase()
}

/** Holds the process-wide [AppContainer]. */
object Di {
    lateinit var container: AppContainer
        private set

    /** Idempotent; called from MainActivity.onCreate (where AuthManager.init used to run). */
    fun init(context: Context) {
        if (::container.isInitialized) return
        container = AppContainer(context)
        // Restore the saved session now, as before (not lazily on first use).
        container.authRepository
    }
}
