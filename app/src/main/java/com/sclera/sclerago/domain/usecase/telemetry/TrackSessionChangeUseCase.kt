package com.sclera.sclerago.domain.usecase.telemetry

import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.telemetry.AnalyticsEvent
import com.sclera.sclerago.domain.telemetry.AnalyticsParam
import com.sclera.sclerago.domain.telemetry.AnalyticsTracker
import javax.inject.Inject

/**
 * Reports session changes as anonymous events: no email, name, role or organisation, only the kind of change
 * and, when the server ended the session, a fixed reason code. The first state seen after launch is not a change.
 */
class TrackSessionChangeUseCase @Inject constructor(private val tracker: AnalyticsTracker) {
    operator fun invoke(previous: AuthState?, current: AuthState) {
        when {
            previous == null -> Unit
            previous is AuthState.SignedOut && current is AuthState.SignedIn -> tracker.log(AnalyticsEvent.SIGN_IN_COMPLETED)
            previous is AuthState.SignedIn && current is AuthState.SignedOut -> {
                val reason = current.reason
                if (reason == null) {
                    tracker.log(AnalyticsEvent.SIGNED_OUT)
                } else {
                    tracker.log(AnalyticsEvent.SESSION_ENDED, mapOf(AnalyticsParam.REASON to reasonCode(reason)))
                }
            }
        }
    }

    private fun reasonCode(error: AuthError): String = when (error) {
        is AuthError.SessionExpired -> "session_expired"
        is AuthError.MultipleSession -> "signed_in_elsewhere"
        is AuthError.UserBlocked -> "user_blocked"
        is AuthError.UserNotFound -> "user_not_found"
        is AuthError.Network, is AuthError.Server -> "other"
    }
}
