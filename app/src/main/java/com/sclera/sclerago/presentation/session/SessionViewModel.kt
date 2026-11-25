package com.sclera.sclerago.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.access.ObserveAccessRefusalsUseCase
import com.sclera.sclerago.domain.usecase.access.RecheckAccessUseCase
import com.sclera.sclerago.domain.usecase.access.StartAccessSyncUseCase
import com.sclera.sclerago.domain.usecase.access.StopAccessSyncUseCase
import com.sclera.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.ClearUserDataUseCase
import com.sclera.sclerago.domain.usecase.auth.LogoutUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.telemetry.TrackSessionChangeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    private val startAccessSync: StartAccessSyncUseCase,
    private val stopAccessSync: StopAccessSyncUseCase,
    private val recheckAccess: RecheckAccessUseCase,
    observeRefusals: ObserveAccessRefusalsUseCase,
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
        // A call the server refused (403): the access and the property list are read again at once, then not again for
        // a short while, however many calls are refused meanwhile.
        viewModelScope.launch {
            observeRefusals().collect {
                if (authState.value is AuthState.SignedIn) {
                    recheckAccess()
                    delay(REFUSAL_PAUSE_MS)
                }
            }
        }
    }

    /**
     * Back in the foreground: the session is checked, and the user's access and property list (with the geofences) are
     * read now and every 30 seconds while the app stays on screen.
     */
    fun onForeground() {
        checkSessionOnForeground()
        startAccessSync()
    }

    /** Off screen: the access and property list stop being read again (until [onForeground]). */
    fun onBackground() = stopAccessSync()

    /** Signs out; false (nothing happens) when offline: signing out needs the server. */
    fun logout(): Boolean = logoutUseCase()

    private companion object {
        /** After a refused call is acted on, further refusals wait this long (one re-check covers them). */
        const val REFUSAL_PAUSE_MS = 30_000L
    }
}
