package com.sclera.sclerago.domain.model

/** The signed-in user as the app shows them. */
data class UserSession(
    val email: String,
    /** Name from the user details or token, falling back to the email. */
    val displayName: String,
    /** Role for display (e.g. "Admin"); null when the backend gives none. */
    val role: String?,
    /** The user's organisation (e.g. "Optima"); null when the backend gives none. */
    val organisation: String? = null,
    /** Profile photo URL; null when the user has none. */
    val avatarUrl: String? = null,
    /** The user's organisation id (from `/me`); its properties are the ones the app shows. Null until loaded. */
    val organisationId: String? = null,
)

sealed interface AuthState {
    /** [reason] explains why the user landed on the login page (expired session, blocked, …). */
    data class SignedOut(val reason: AuthError? = null) : AuthState
    data class SignedIn(val session: UserSession) : AuthState
}
