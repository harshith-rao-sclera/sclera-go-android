package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import kotlinx.coroutines.flow.StateFlow

/** People and access: the groups and who is in them. */
interface AccessRepository {
    val groups: StateFlow<List<Group>>
    val people: StateFlow<List<Person>>

    fun setGroups(groups: List<Group>)
    fun setPeople(people: List<Person>)
}
