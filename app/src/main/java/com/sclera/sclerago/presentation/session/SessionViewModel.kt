package com.sclera.sclerago.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.access.RefreshAccessOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.ClearUserDataUseCase
import com.sclera.sclerago.domain.usecase.auth.LogoutUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.telemetry.TrackSessionChangeUseCase
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
    private val trackSessionChange: TrackSessionChangeUseCase,
    private val refreshAccess: RefreshAccessOnForegroundUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    init {
        // Whenever the session ends (sign-out, expiry, blocked account), nothing of the user stays on the device.
        viewModelScope.launch {
            var previous: AuthState? = null
            authState.collect {
                if (it is AuthState.SignedOut) clearUserData()
                trackSessionChange(previous, it)
                previous = it
            }
        }
    }

    /** Back in the foreground: the session is checked, and the user's access read again (as the web does on return). */
    fun onForeground() {
        checkSessionOnForeground()
        refreshAccess()
    }

    /** Signs out; false (nothing happens) when offline: signing out needs the server. */
    fun logout(): Boolean = logoutUseCase()
}
