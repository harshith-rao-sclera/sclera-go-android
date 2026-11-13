package com.sclera.sclerago.data.sync

import com.sclera.sclerago.data.online.MeIdentity
import com.sclera.sclerago.data.online.OnlineIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The sign-in credentials with the organisation and user ids `GET /me` reported put in place of the ones read from the
 * user details or token (those are only used until `/me` has answered, or when it never did).
 */
class MeCredentialsProvider(
    base: SyncCredentialsProvider,
    identity: OnlineIdentity,
    scope: CoroutineScope,
) : SyncCredentialsProvider {
    override val credentials: StateFlow<SyncCredentials?> =
        combine(base.credentials, identity.identity, ::overlay).stateIn(scope, SharingStarted.Eagerly, overlay(base.credentials.value, identity.identity.value))

    private companion object {
        fun overlay(base: SyncCredentials?, me: MeIdentity?): SyncCredentials? =
            if (base == null || me == null || !me.email.equals(base.email, ignoreCase = true)) base
            else base.copy(
                userId = me.userId.ifBlank { base.userId },
                organisationId = me.organisationId.ifBlank { base.organisationId },
            )
    }
}
