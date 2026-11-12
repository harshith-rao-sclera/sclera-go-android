package com.sclera.sclerago.presentation.session

import androidx.lifecycle.ViewModel
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.usecase.auth.CheckSessionOnForegroundUseCase
import com.sclera.sclerago.domain.usecase.auth.LogoutUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Signed-in / signed-out state for the app shell, the side menu and People and access. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val checkSessionOnForeground: CheckSessionOnForegroundUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    fun onForeground() = checkSessionOnForeground()

    fun logout() = logoutUseCase()
}
