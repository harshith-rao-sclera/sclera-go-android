package com.example.sclerago

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.sclerago.data.ADMINS_ID
import com.example.sclerago.data.DEFAULT_GROUPS
import com.example.sclerago.data.Group
import com.example.sclerago.data.Permission
import com.example.sclerago.data.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class AccessTab { PEOPLE, GROUPS }

data class AccessState(
    val groups: List<Group> = DEFAULT_GROUPS,
    val people: List<Person> = emptyList(),
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

/** People and access (mock data, in memory): who is in which group, for which properties. */
@HiltViewModel
class AccessViewModel @Inject constructor() : ViewModel() {
    var state by mutableStateOf(AccessState())
        private set

    private fun set(f: AccessState.() -> AccessState) { state = state.f() }

    /** Seeds the list with the signed-in user as an admin the first time the screen opens. */
    fun ensureSeeded(currentEmail: String?, propertyIds: Set<String>) {
        if (state.people.isNotEmpty() || currentEmail.isNullOrBlank()) return
        set { copy(people = listOf(Person("u-me", currentEmail, ADMINS_ID, allProperties = true, lastSeen = "just now"))) }
    }

    fun setTab(t: AccessTab) = set { copy(tab = t, query = "") }
    fun setQuery(q: String) = set { copy(query = q) }

    fun group(id: String): Group? = state.groups.firstOrNull { it.id == id }
    fun peopleIn(groupId: String): Int = state.people.count { it.groupId == groupId }

    val visiblePeople: List<Person>
        get() = state.query.trim().lowercase().let { q -> state.people.filter { q.isEmpty() || q in it.email.lowercase() } }

    val visibleGroups: List<Group>
        get() = state.query.trim().lowercase().let { q ->
            state.groups.filter { q.isEmpty() || q in it.name.lowercase() || q in it.description.lowercase() }
        }

    // ---- People

    fun newPerson() = set {
        val firstGroup = groups.firstOrNull { !it.locked } ?: groups.first()
        copy(personForm = Person("", "", firstGroup.id, allProperties = false))
    }
    fun editPerson(p: Person) = set { copy(personForm = p) }
    fun closePersonForm() = set { copy(personForm = null) }

    /** Saves [p]; returns an error message instead when it can't be saved. */
    fun savePerson(p: Person): String? {
        val problem = personProblem(p, state.people, group(p.groupId))
        if (problem != null) return problem
        val clean = p.copy(email = p.email.trim(), propertyIds = if (p.allProperties) emptySet() else p.propertyIds)
        set {
            if (clean.id.isEmpty()) copy(people = people + clean.copy(id = "u${System.currentTimeMillis()}"), personForm = null)
            else copy(people = people.map { if (it.id == clean.id) clean else it }, personForm = null)
        }
        return null
    }

    fun askRemovePerson(p: Person?) = set { copy(personToRemove = p) }
    fun removePerson(p: Person) = set { copy(people = people.filterNot { it.id == p.id }, personToRemove = null) }

    // ---- Groups

    fun newGroup() = set { copy(groupForm = Group("", "", "", emptySet())) }
    fun editGroup(g: Group) = set { copy(groupForm = g) }
    fun closeGroupForm() = set { copy(groupForm = null) }

    fun saveGroup(g: Group): String? {
        val problem = groupProblem(g, state.groups)
        if (problem != null) return problem
        val clean = g.copy(
            // Locked groups (Admins) keep their name.
            name = if (g.locked) group(g.id)?.name ?: g.name.trim() else g.name.trim(),
            description = g.description.trim(),
            permissions = if (g.locked) Permission.entries.toSet() else g.permissions,
        )
        set {
            val groups = if (clean.id.isEmpty()) groups + clean.copy(id = "g${System.currentTimeMillis()}")
            else groups.map { if (it.id == clean.id) clean else it }
            // People in a group that now needs every property get every property.
            val people = if (clean.needsAllProperties) people.map { if (it.groupId == clean.id) it.copy(allProperties = true, propertyIds = emptySet()) else it } else people
            copy(groups = groups, people = people, groupForm = null)
        }
        return null
    }

    fun canDelete(g: Group): Boolean = !g.locked && peopleIn(g.id) == 0
    fun askDeleteGroup(g: Group?) = set { copy(confirmDelete = g) }
    fun deleteGroup(g: Group) {
        if (!canDelete(g)) return
        set { copy(groups = groups.filterNot { it.id == g.id }, confirmDelete = null) }
    }
}

/** Why [p] can't be saved, or null when it can. */
internal fun personProblem(p: Person, people: List<Person>, group: Group?): String? {
    val email = p.email.trim()
    return when {
        email.isEmpty() -> "Enter their email address."
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address."
        people.any { it.id != p.id && it.email.equals(email, ignoreCase = true) } -> "$email already has access."
        group == null -> "Choose a group."
        group.needsAllProperties && !p.allProperties -> "${group.name} has permissions that need all properties."
        !p.allProperties && p.propertyIds.isEmpty() -> "Choose at least one property, or give them all properties."
        else -> null
    }
}

/** Why [g] can't be saved, or null when it can. */
internal fun groupProblem(g: Group, groups: List<Group>): String? {
    val name = g.name.trim()
    return when {
        name.isEmpty() -> "Give the group a name."
        groups.any { it.id != g.id && it.name.equals(name, ignoreCase = true) } -> "There is already a group called $name."
        else -> null
    }
}
