package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.DEFAULT_GROUPS
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Groups and people in memory (mock data) until People and access is wired to the Sclera API. */
@ActivityRetainedScoped
class AccessRepositoryImpl @Inject constructor() : AccessRepository, UserDataCleaner {
    private val groupState = MutableStateFlow(DEFAULT_GROUPS)
    private val peopleState = MutableStateFlow(emptyList<Person>())
    override val groups: StateFlow<List<Group>> = groupState.asStateFlow()
    override val people: StateFlow<List<Person>> = peopleState.asStateFlow()

    override fun setGroups(groups: List<Group>) {
        groupState.value = groups
    }

    override fun clear() {
        groupState.value = DEFAULT_GROUPS
        peopleState.value = emptyList()
    }

    override fun setPeople(people: List<Person>) {
        peopleState.value = people
    }
}
