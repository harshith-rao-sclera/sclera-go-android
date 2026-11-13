package com.sclera.sclerago.data.sync

import kotlinx.coroutines.flow.StateFlow

/** What the sync engine needs to talk to the server as the signed-in user. Never printed: it holds a token. */
data class SyncCredentials(
    val email: String,
    val userId: String,
    val role: String,
    val organisationId: String,
    val accessToken: String,
    val sessionId: String,
) {
    override fun toString() = "SyncCredentials(redacted)"
}

/** The signed-in user's credentials; null when signed out. Emits again whenever the token or session is refreshed. */
interface SyncCredentialsProvider {
    val credentials: StateFlow<SyncCredentials?>
}
