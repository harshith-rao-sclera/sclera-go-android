package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * The Sclera session: login completion, access-token generation/regeneration, session validation
 * and sign-out. Any error that ends the session moves [state] to [AuthState.SignedOut].
 */
interface AuthRepository {
    val state: StateFlow<AuthState>

    /**
     * Finishes a login once the SSO WebView has returned to the app domain: reads the session id,
     * generates the first access token, loads the user details, persists the session and signs in.
     * Throws [com.sclera.sclerago.domain.model.AuthError] when it can't.
     */
    suspend fun completeLogin(enteredEmail: String)

    /** Forgets the cookies of a failed login so the next attempt starts clean. */
    fun clearLoginCookies()

    /** App came to the foreground: re-check the session (at most once a minute). */
    fun onForeground()

    /** User-initiated logout: ends the session on the server (best effort) and locally. */
    fun logout()
}

/** Whether the device currently has an internet connection. */
interface NetworkMonitor {
    fun isOnline(): Boolean
}
