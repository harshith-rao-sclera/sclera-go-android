package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.presentation.common.observedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccessTab { PEOPLE, GROUPS }

/** What People and access shows: the tab, search and open forms. The groups and people are in [AccessViewModel.groups] / [AccessViewModel.people]. */
data class AccessState(
    val tab: AccessTab = AccessTab.GROUPS,
    val query: String = "",
    /** Person being added (id == "") or edited; null when the form is closed. */
    val personForm: Person? = null,
    /** Group being added (id == "") or edited; null when the form is closed. */
    val groupForm: Group? = null,
    /** Group waiting for delete confirmation. */
    val confirmDelete: Group? = null,
    val personToRemove: Person? = null,
)

/** People and access: who is in which group, for which properties. */
@HiltViewModel
class AccessViewModel @Inject constructor(
    private val useCases: AccessUseCases,
    observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {
    var state by mutableStateOf(AccessState())
        private set

    private fun set(f: AccessState.() -> AccessState) { state = state.f() }

    init {
        // When the session ends, close every form and clear the search.
        viewModelScope.launch {
            observeAuthState().collect { if (it is AuthState.SignedOut) state = AccessState() }
        }
    }

    private val groupList = useCases.observeGroups().observedIn(viewModelScope)
    private val peopleList = useCases.observePeople().observedIn(viewModelScope)

    val groups: List<Group> get() = groupList.value
    val people: List<Person> get() = peopleList.value

    /** Seeds the list with the signed-in user as an admin the first time the screen opens. */
    @Suppress("UNUSED_PARAMETER")
    fun ensureSeeded(currentEmail: String?, propertyIds: Set<String>) = useCases.seedSignedInUser(currentEmail)

    fun setTab(t: AccessTab) = set { copy(tab = t, query = "") }
    fun setQuery(q: String) = set { copy(query = q) }

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }
    fun peopleIn(groupId: String): Int = people.count { it.groupId == groupId }

    val visiblePeople: List<Person>
        get() = state.query.trim().lowercase().let { q -> people.filter { q.isEmpty() || q in it.email.lowercase() } }

    val visibleGroups: List<Group>
        get() = state.query.trim().lowercase().let { q ->
            groups.filter { q.isEmpty() || q in it.name.lowercase() || q in it.description.lowercase() }
        }

    // ---- People

    fun newPerson() {
        val firstGroup = groups.firstOrNull { !it.locked } ?: groups.first()
        set { copy(personForm = Person("", "", firstGroup.id, allProperties = false)) }
    }
    fun editPerson(p: Person) = set { copy(personForm = p) }
    fun closePersonForm() = set { copy(personForm = null) }

    /** Saves [p]; returns an error message instead when it can't be saved. */
    fun savePerson(p: Person): String? {
        useCases.savePerson(p)?.let { return it }
        set { copy(personForm = null) }
        return null
    }

    fun askRemovePerson(p: Person?) = set { copy(personToRemove = p) }
    fun removePerson(p: Person) {
        useCases.removePerson(p)
        set { copy(personToRemove = null) }
    }

    // ---- Groups

    fun newGroup() = set { copy(groupForm = Group("", "", "", emptySet())) }
    fun editGroup(g: Group) = set { copy(groupForm = g) }
    fun closeGroupForm() = set { copy(groupForm = null) }

    /** Saves [g]; returns an error message instead when it can't be saved. */
    fun saveGroup(g: Group): String? {
        useCases.saveGroup(g)?.let { return it }
        set { copy(groupForm = null) }
        return null
    }

    fun canDelete(g: Group): Boolean {
        people // recompose when people join or leave the group
        return useCases.canDeleteGroup(g)
    }
    fun askDeleteGroup(g: Group?) = set { copy(confirmDelete = g) }
    fun deleteGroup(g: Group) {
        if (useCases.deleteGroup(g)) set { copy(confirmDelete = null) }
    }
}
