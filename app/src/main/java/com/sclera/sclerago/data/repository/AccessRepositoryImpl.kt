package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.online.AccessStore
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Groups and people, read and changed through the [AccessStore] (the server decides who may). */
@Singleton
class AccessRepositoryImpl @Inject constructor(private val store: AccessStore) : AccessRepository {
    override val groups: StateFlow<List<Group>> = store.groups
    override val people: StateFlow<List<Person>> = store.people

    override suspend fun refresh() = store.refresh()
    override suspend fun saveGroup(group: Group): Group = store.saveGroup(group)
    override suspend fun deleteGroup(id: String) = store.deleteGroup(id)
    override suspend fun savePerson(person: Person): Person = store.savePerson(person)
    override suspend fun removePerson(id: String) = store.removePerson(id)
}
