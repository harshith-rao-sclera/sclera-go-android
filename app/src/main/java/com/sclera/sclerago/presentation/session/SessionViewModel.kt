package com.sclera.sclerago.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.ClearUserDataUseCase
import com.sclera.sclerago.domain.usecase.auth.LogoutUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Signed-in / signed-out state for the app shell, the side menu and People and access. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val checkSessionOnForeground: CheckSessionOnForegroundUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val clearUserData: ClearUserDataUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    init {
        // Whenever the session ends (sign-out, expiry, blocked account), nothing of the user stays on the device.
        viewModelScope.launch {
            authState.collect { if (it is AuthState.SignedOut) clearUserData() }
        }
    }

    fun onForeground() = checkSessionOnForeground()

    fun logout() = logoutUseCase()
}
