package com.example.sclerago.domain.usecase.auth

import com.example.sclerago.domain.model.AuthState
import com.example.sclerago.domain.repository.AuthRepository
import com.example.sclerago.domain.repository.NetworkMonitor
import kotlinx.coroutines.flow.StateFlow

class ObserveAuthStateUseCase(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = repository.state
}

class CompleteLoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(enteredEmail: String) = repository.completeLogin(enteredEmail)
}

class ClearLoginCookiesUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.clearLoginCookies()
}

class CheckSessionOnForegroundUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.onForeground()
}

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.logout()
}

class IsOnlineUseCase(private val monitor: NetworkMonitor) {
    operator fun invoke(): Boolean = monitor.isOnline()
}

/** Why an email can't be submitted on the login screen. */
enum class EmailProblem { BLANK, INVALID }

/** Null when [email] can be submitted, otherwise why not. */
class ValidateEmailUseCase {
    operator fun invoke(email: String): EmailProblem? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> EmailProblem.BLANK
            !EMAIL_ADDRESS.matches(trimmed) -> EmailProblem.INVALID
            else -> null
        }
    }

    companion object {
        /** Same pattern as android.util.Patterns.EMAIL_ADDRESS (kept here so the domain stays pure Kotlin). */
        val EMAIL_ADDRESS = Regex(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+",
        )
    }
}
