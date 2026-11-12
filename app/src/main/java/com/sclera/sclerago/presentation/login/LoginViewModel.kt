package com.sclera.sclerago.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.usecase.auth.ClearLoginCookiesUseCase
import com.sclera.sclerago.domain.usecase.auth.CompleteLoginUseCase
import com.sclera.sclerago.domain.usecase.auth.EmailProblem
import com.sclera.sclerago.domain.usecase.auth.IsOnlineUseCase
import com.sclera.sclerago.domain.usecase.auth.ValidateEmailUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ScleraAuth"

enum class LoginStage { Email, Web, Finishing }

data class LoginUiState(val stage: LoginStage = LoginStage.Email, val error: UiText? = null)

fun EmailProblem.toUiText(): UiText = when (this) {
    EmailProblem.BLANK -> UiText.Res(R.string.login_error_email_blank)
    EmailProblem.INVALID -> UiText.Res(R.string.login_error_email_invalid)
}

/** Null when [email] can be submitted, otherwise the message to show. */
internal fun validateEmail(email: String): UiText? = ValidateEmailUseCase()(email)?.toUiText()

/**
 * Login flow: native email entry → `getRedirectUrlByEmail` in a WebView, which lands on the
 * Keycloak page (email prefilled via `login_hint`, user enters the password) → back to the
 * app domain → session id + access token → app.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val completeLogin: CompleteLoginUseCase,
    private val clearLoginCookies: ClearLoginCookiesUseCase,
    private val validateEmail: ValidateEmailUseCase,
    private val isOnline: IsOnlineUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var loginJob: Job? = null

    /**
     * The login screen (re)entered composition: start over at the email step showing [reason],
     * and drop any login still running — as the screen's own `remember` state and coroutine
     * scope did before.
     */
    fun start(reason: AuthError?) {
        loginJob?.cancel()
        loginJob = null
        _state.value = LoginUiState(LoginStage.Email, reason?.toUiText())
    }

    fun onEmailChanged() = _state.update { it.copy(error = null) }

    fun onContinue(email: String) {
        val invalid = validateEmail(email)?.toUiText()
        when {
            invalid != null -> _state.update { it.copy(error = invalid) }
            !isOnline() -> _state.update { it.copy(error = AuthError.Network().toUiText()) }
            else -> _state.update { it.copy(error = null, stage = LoginStage.Web) }
        }
    }

    fun onCancelWeb() = _state.update { it.copy(stage = LoginStage.Email) }

    fun fail(message: UiText) = _state.update { it.copy(error = message, stage = LoginStage.Email) }

    /** The SSO WebView reached the app domain. [email] is the trimmed email that was entered. */
    fun onReturned(email: String) {
        if (_state.value.stage != LoginStage.Web) return
        _state.update { it.copy(stage = LoginStage.Finishing) }
        loginJob = viewModelScope.launch {
            try {
                // Switches MainActivity to the app; the login screen leaves composition.
                completeLogin(email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                Log.e(TAG, "Login failed", e)
                clearLoginCookies()
                fail(e.toUiText())
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                fail(UiText.Res(R.string.login_error_generic))
            }
        }
    }
}
