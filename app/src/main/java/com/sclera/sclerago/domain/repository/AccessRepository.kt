package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.DirectoryPage
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.PermissionInfo
import com.sclera.sclerago.domain.model.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * People and access: the groups and who is in them, from the server (the last copy is kept for offline reading).
 * Changes need a connection; every failure throws [com.sclera.sclerago.domain.model.OnlineException].
 */
interface AccessRepository {
    val groups: StateFlow<List<Group>>
    val people: StateFlow<List<Person>>

    /** Loads groups and people again; what is shown stays when it fails. */
    suspend fun refresh()

    /** Creates ([Group.id] empty) or updates a group; returns it as the server saved it. */
    suspend fun saveGroup(group: Group): Group

    suspend fun deleteGroup(id: String)

    /** Invites ([Person.id] empty) or updates a person; returns them as the server saved them. */
    suspend fun savePerson(person: Person): Person

    /** Every permission a group can be given, in the server's order, with its name and description (empty until loaded). */
    val permissions: StateFlow<List<PermissionInfo>> get() = NoPermissions

    /** A [page] (from 1) of [limit] people the organisation's directory lists matching [query] (everyone when blank). */
    suspend fun directory(query: String, limit: Int, page: Int): DirectoryPage = NoDirectory
}

private val NoDirectory = DirectoryPage(emptyList(), available = false, reason = null, hasMore = false)

private val NoPermissions: StateFlow<List<PermissionInfo>> = MutableStateFlow(emptyList())
