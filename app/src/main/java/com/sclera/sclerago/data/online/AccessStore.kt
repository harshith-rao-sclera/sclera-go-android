package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.mapper.removeAccessBody
import com.sclera.sclerago.data.mapper.toCreateBody
import com.sclera.sclerago.data.mapper.toGroup
import com.sclera.sclerago.data.mapper.toInviteBody
import com.sclera.sclerago.data.mapper.toPerson
import com.sclera.sclerago.data.mapper.toUpdateBody
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.GroupDto
import com.sclera.sclerago.data.remote.adc.UserDto
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** What is kept encrypted for offline use: the groups and the people (names, emails and who can open which property). */
@Serializable
private data class AccessCache(val groups: List<GroupDto> = emptyList(), val users: List<UserDto> = emptyList())

/**
 * The organisation's groups and people, from the API; the last copy is kept encrypted ([EncryptedCache]) so it
 * shows offline. Writes are online only. Loaded when someone signs in; forgotten on sign-out.
 */
@Singleton
class AccessStore @Inject constructor(
    private val api: AdcApi,
    private val session: OnlineSession,
    private val cache: EncryptedCache,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UserDataCleaner {
    private val _groups = MutableStateFlow(emptyList<Group>())
    private val _people = MutableStateFlow(emptyList<Person>())
    private var groupDtos = emptyList<GroupDto>()
    private var userDtos = emptyList<UserDto>()

    val groups: StateFlow<List<Group>> = _groups
    val people: StateFlow<List<Person>> = _people

    init {
        scope.launch {
            session.user.collectLatest { user ->
                if (user == null) {
                    clear()
                } else {
                    readCache()
                    try {
                        refresh()
                    } catch (_: OnlineException) {
                        // the cached copy stays
                    }
                }
            }
        }
    }

    /** Loads groups and people again. Throws [OnlineException]; what is shown stays when it fails. */
    suspend fun refresh() {
        val user = session.user.value
        val org = session.organisation()
        val loadedGroups = api.groups(org)
        val loadedUsers = api.users(org)
        if (session.user.value != user) return // signed out meanwhile: drop it
        keep(loadedGroups, loadedUsers)
    }

    /** Creates ([Group.id] empty) or updates a group; returns it as saved. Throws [OnlineException]. */
    suspend fun saveGroup(group: Group): Group {
        val org = session.organisation()
        val saved = if (group.id.isEmpty()) api.createGroup(org, group.toCreateBody()) else api.updateGroup(org, group.id, group.toUpdateBody())
        keep((groupDtos.filterNot { it.id == saved.id } + saved).sortedBy { it.name.lowercase() }, userDtos)
        return saved.toGroup()
    }

    suspend fun deleteGroup(id: String) {
        api.deleteGroup(session.organisation(), id)
        keep(groupDtos.filterNot { it.id == id }, userDtos)
    }

    /** Invites ([Person.id] empty) or updates a person; returns them as saved. Throws [OnlineException]. */
    suspend fun savePerson(person: Person): Person {
        val org = session.organisation()
        val saved = if (person.id.isEmpty()) api.inviteUser(org, person.toInviteBody()) else api.updateUser(org, person.id, person.toUpdateBody())
        keep(groupDtos, (userDtos.filterNot { it.id == saved.id } + saved).sortedBy { it.email.lowercase() })
        return saved.toPerson()
    }

    /** Takes away all of a person's access (the API has no delete). Throws [OnlineException]. */
    suspend fun removePerson(id: String) {
        api.updateUser(session.organisation(), id, removeAccessBody())
        keep(groupDtos, userDtos.filterNot { it.id == id })
    }

    override fun clear() {
        groupDtos = emptyList()
        userDtos = emptyList()
        publish()
    }

    private suspend fun readCache() {
        val text = withContext(io) { cache.read(CACHE) } ?: return
        runCatching { AdcJson.decodeFromString(AccessCache.serializer(), text) }.getOrNull()?.let {
            groupDtos = it.groups
            userDtos = it.users
            publish()
        }
    }

    private suspend fun keep(groups: List<GroupDto>, users: List<UserDto>) {
        groupDtos = groups
        userDtos = users
        publish()
        withContext(io) { runCatching { cache.write(CACHE, AdcJson.encodeToString(AccessCache.serializer(), AccessCache(groups, users))) } }
    }

    /** People with no group have no access (they were removed), so they are not listed. */
    private fun publish() {
        _groups.value = groupDtos.map(GroupDto::toGroup)
        _people.value = userDtos.filter { it.groupId != null }.map(UserDto::toPerson)
    }

    private companion object {
        const val CACHE = "access"
    }
}
