package com.example.sclerago.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sclerago.di.Di
import com.example.sclerago.domain.model.AuthState
import com.example.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.example.sclerago.domain.usecase.auth.LogoutUseCase
import com.example.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import kotlinx.coroutines.flow.StateFlow

/** Signed-in / signed-out state for the app shell, the side menu and People and access. */
class SessionViewModel(
    observeAuthState: ObserveAuthStateUseCase,
    private val checkSessionOnForeground: CheckSessionOnForegroundUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    fun onForeground() = checkSessionOnForeground()

    fun logout() = logoutUseCase()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = Di.container
                SessionViewModel(c.observeAuthState, c.checkSessionOnForeground, c.logout)
            }
        }
    }
}
