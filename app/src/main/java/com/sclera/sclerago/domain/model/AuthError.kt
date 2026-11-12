package com.sclera.sclerago.domain.model

/**
 * Every way auth can fail. [endsSession] errors send the user back to the login page.
 * The exception message is English and meant for logs only; the user-facing text is chosen by the
 * presentation layer from the error type.
 */
sealed class AuthError(
    message: String,
    val endsSession: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class SessionExpired : AuthError("session expired", true)
    class MultipleSession : AuthError("signed in elsewhere", true)
    class UserBlocked : AuthError("user blocked", true)
    class UserNotFound : AuthError("user not found", true)
    class Network(cause: Throwable? = null) : AuthError("network error", false, cause = cause)
    class Server(val detail: String) : AuthError("server error: $detail", false)

    companion object {
        // Body status codes used by the Sclera backend (see fn() in the web bundle).
        const val STATUS_USER_NOT_FOUND = 701
        const val STATUS_USER_BLOCKED = 706
        const val STATUS_MULTIPLE_SESSION = 716
        const val STATUS_SESSION_EXPIRED = 821

        fun fromStatus(status: Int): AuthError? = when (status) {
            STATUS_SESSION_EXPIRED -> SessionExpired()
            STATUS_MULTIPLE_SESSION -> MultipleSession()
            STATUS_USER_BLOCKED -> UserBlocked()
            STATUS_USER_NOT_FOUND -> UserNotFound()
            else -> null
        }
    }
}
