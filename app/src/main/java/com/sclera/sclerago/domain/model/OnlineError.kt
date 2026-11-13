package com.sclera.sclerago.domain.model

/** Why a call to the Sclera online API failed, in terms the screens can show (the server's own text is never shown). */
sealed interface OnlineError {
    /** The device has no connection, or the server could not be reached. */
    data object Offline : OnlineError

    /** The session ended or the token was refused even after renewing it. */
    data object SessionExpired : OnlineError

    /** The signed-in user is not allowed to do this. */
    data object Forbidden : OnlineError

    data object NotFound : OnlineError

    /** The change clashes with what is there; [code] is the server's problem code (e.g. a group that still has people). */
    data class Conflict(val code: String) : OnlineError

    /** The server refused the values that were sent. */
    data object Invalid : OnlineError

    /** The server is still getting something ready; try again after [retryAfterSeconds] (0 when it did not say). */
    data class Unavailable(val retryAfterSeconds: Int) : OnlineError

    data object Server : OnlineError
}

/** A call to the online API failed; [error] says why. The message carries no server text. */
class OnlineException(val error: OnlineError, cause: Throwable? = null) : Exception(error.toString(), cause)
