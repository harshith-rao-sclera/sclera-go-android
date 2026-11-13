package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.sync.SyncCredentials
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Who is signed in as far as the online data is concerned. Never printed: it holds an email. */
data class OnlineUser(val email: String, val organisationId: String) {
    override fun toString() = "OnlineUser(redacted)"
}

private fun SyncCredentials?.toUser() = this?.let { OnlineUser(it.email, it.organisationId) }

/**
 * The signed-in user of the online data. It changes on sign-in and sign-out only, not when the access token is
 * renewed, so the stores load once per sign-in.
 */
@Singleton
class OnlineSession @Inject constructor(credentials: SyncCredentialsProvider, @ApplicationScope scope: CoroutineScope) {
    val user: StateFlow<OnlineUser?> = credentials.credentials
        .map { it.toUser() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, credentials.credentials.value.toUser())

    /** The organisation id every call needs; the session is treated as over when it is not known. */
    fun organisation(): String =
        user.value?.organisationId?.takeIf { it.isNotBlank() } ?: throw OnlineException(OnlineError.SessionExpired)
}
