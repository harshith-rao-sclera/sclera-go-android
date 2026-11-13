package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.mapper.toCurrentAccess
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.MeDto
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** The last `/me` answer for the person who signed in as [email]; it holds their email, so it is kept encrypted. */
@Serializable
private data class CachedMe(val email: String, val me: MeDto)

/**
 * The caller's identity, group and permissions (`GET /me`), fetched once after each sign-in without blocking anything.
 * Its organisation id and user id are handed to [OnlineIdentity] so every online call and the sync use them. The last
 * answer is kept encrypted ([EncryptedCache], wiped on sign-out) so the access and organisation are known offline.
 * A failed fetch leaves the cached answer, or [AccessState.Failed] when there is none. The server checks every call.
 */
@Singleton
class CurrentAccessRepositoryImpl @Inject constructor(
    private val api: AdcApi,
    session: OnlineSession,
    private val identity: OnlineIdentity,
    private val cache: EncryptedCache,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CurrentAccessRepository, UserDataCleaner {
    private val _state = MutableStateFlow<AccessState>(AccessState.Loading)
    override val state: StateFlow<AccessState> = _state

    init {
        scope.launch {
            // Keyed on the email only: learning the organisation changes the session's user but must not fetch again.
            session.user.map { it?.email }.distinctUntilChanged().collectLatest { email ->
                clear()
                if (email != null) load(email)
            }
        }
    }

    private suspend fun load(email: String) {
        val cached = withContext(io) { readCache(email) }
        if (cached != null) publish(email, cached)
        try {
            val fresh = api.me()
            publish(email, fresh)
            withContext(io) { writeCache(email, fresh) }
        } catch (e: OnlineException) {
            if (cached == null) _state.value = AccessState.Failed(e.error)
        }
    }

    private fun publish(email: String, me: MeDto) {
        val access = me.toCurrentAccess(email)
        access.organisationId?.let { identity.set(MeIdentity(email, access.userId, it)) }
        _state.value = if (access.hasGroup) AccessState.Loaded(access) else AccessState.NoGroup(access.noPermissions())
    }

    private fun CurrentAccess.noPermissions() = copy(permissions = emptySet(), canManageProperties = false)

    private fun readCache(email: String): MeDto? = runCatching {
        cache.read(CACHE)?.let { AdcJson.decodeFromString(CachedMe.serializer(), it) }
    }.getOrNull()?.takeIf { it.email.equals(email, ignoreCase = true) }?.me

    private fun writeCache(email: String, me: MeDto) {
        runCatching { cache.write(CACHE, AdcJson.encodeToString(CachedMe.serializer(), CachedMe(email, me))) }
    }

    override fun clear() {
        identity.clear()
        _state.value = AccessState.Loading
    }

    private companion object {
        const val CACHE = "me"
    }
}
