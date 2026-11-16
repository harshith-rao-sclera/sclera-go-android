package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.access.CanDeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.CountPeopleGivenAllPropertiesUseCase
import com.sclera.sclerago.domain.usecase.access.DeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveGroupsUseCase
import com.sclera.sclerago.domain.usecase.access.ObservePeopleUseCase
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.access.RefreshAccessUseCase
import com.sclera.sclerago.domain.usecase.access.RemovePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SaveGroupUseCase
import com.sclera.sclerago.domain.usecase.access.SavePersonUseCase
import com.sclera.sclerago.domain.usecase.access.ValidateGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ValidatePersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Groups and people in memory, behaving like the server: a new group or person ([Group.id] / [Person.id] empty) gets
 * the next id ("g-1", "u-1", ...), a saved one replaces the one with its id, and [refresh] keeps what is there.
 */
class InMemoryAccessRepository(groups: List<Group> = emptyList(), people: List<Person> = emptyList()) : AccessRepository {
    private val groupFlow = MutableStateFlow(groups)
    private val peopleFlow = MutableStateFlow(people)
    override val groups: StateFlow<List<Group>> = groupFlow
    override val people: StateFlow<List<Person>> = peopleFlow
    private var nextGroup = 0
    private var nextPerson = 0
    var refreshes = 0
        private set

    fun setGroups(list: List<Group>) {
        groupFlow.value = list
    }

    fun setPeople(list: List<Person>) {
        peopleFlow.value = list
    }

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun saveGroup(group: Group): Group {
        val saved = if (group.id.isEmpty()) group.copy(id = "g-${++nextGroup}") else group
        groupFlow.value = groupFlow.value.replaceOrAdd(saved) { it.id == saved.id }
        return saved
    }

    override suspend fun deleteGroup(id: String) {
        groupFlow.value = groupFlow.value.filterNot { it.id == id }
    }

    override suspend fun savePerson(person: Person): Person {
        val saved = if (person.id.isEmpty()) person.copy(id = "u-${++nextPerson}") else person
        peopleFlow.value = peopleFlow.value.replaceOrAdd(saved) { it.id == saved.id }
        return saved
    }

    override suspend fun removePerson(id: String) {
        peopleFlow.value = peopleFlow.value.filterNot { it.id == id }
    }

    private fun <T> List<T>.replaceOrAdd(item: T, same: (T) -> Boolean): List<T> =
        if (any(same)) map { if (same(it)) item else it } else this + item
}

/** The people-and-access use cases over [repo], wired as Hilt does ([guard] lets everything through by default). */
fun accessUseCases(repo: AccessRepository, guard: PermissionGuard = allowAllGuard()): AccessUseCases {
    val canDelete = CanDeleteGroupUseCase(repo)
    return AccessUseCases(
        ObserveGroupsUseCase(repo), ObservePeopleUseCase(repo), RefreshAccessUseCase(repo),
        ValidatePersonUseCase(repo), ValidateGroupUseCase(repo), SavePersonUseCase(repo, guard), RemovePersonUseCase(repo, guard),
        CountPeopleGivenAllPropertiesUseCase(repo), SaveGroupUseCase(repo, guard), canDelete, DeleteGroupUseCase(repo, canDelete, guard),
    )
}
