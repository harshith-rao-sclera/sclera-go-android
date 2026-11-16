package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
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

    /** Takes away all of the person's access. */
    suspend fun removePerson(id: String)
}
