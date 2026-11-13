package com.sclera.sclerago.data.online

import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The ids the server (`GET /me`) says the signed-in person [email] has. Never printed: ids and an email. */
data class MeIdentity(val email: String, val userId: String, val organisationId: String) {
    override fun toString() = "MeIdentity(redacted)"
}

/**
 * Holds the identity `GET /me` reported, so the online data and the sync engine use the organisation the API knows
 * (the user details and the token may not name it). Empty until `/me` has answered once (or its cached answer is read).
 */
@Singleton
class OnlineIdentity @Inject constructor() : UserDataCleaner {
    private val state = MutableStateFlow<MeIdentity?>(null)
    val identity: StateFlow<MeIdentity?> = state.asStateFlow()

    fun set(identity: MeIdentity?) {
        state.value = identity
    }

    override fun clear() {
        state.value = null
    }
}
