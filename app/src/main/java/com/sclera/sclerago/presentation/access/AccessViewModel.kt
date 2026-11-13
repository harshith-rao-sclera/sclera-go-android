package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccessTab { PEOPLE, GROUPS }

/** What People and access shows: the tab, search and open forms. The groups and people are in [AccessViewModel.groups] / [AccessViewModel.people]. */
data class AccessState(
    val tab: AccessTab = AccessTab.PEOPLE,
    val query: String = "",
    /** Groups tab: show only groups that can do this; null = all. */
    val groupFilter: Permission? = null,
    /** Groups ticked for deleting together (only groups that can be deleted). */
    val pickedGroups: Set<String> = emptySet(),
    /** Deleting [pickedGroups] waits for confirmation. */
    val confirmDeletePicked: Boolean = false,
    /** Person being added (id == "") or edited; null when the form is closed. */
    val personForm: Person? = null,
    /** Group being added (id == "") or edited; null when the form is closed. */
    val groupForm: Group? = null,
    /** Group waiting for delete confirmation. */
    val confirmDelete: Group? = null,
    val personToRemove: Person? = null,
    /** Group edit waiting for the user to agree that its people get every property. */
    val groupToConfirm: Group? = null,
    /** Why the last change could not be made (shown once, then consumed). */
    val notice: UiText? = null,
)

/** A person in the list: who, their group, and when they were last seen. */
data class PersonRow(val person: Person, val group: Group?, val isMe: Boolean, val lastSeenAt: Long?)

/** A group in the list: how many people are in it and whether it can be deleted. */
data class GroupRow(val group: Group, val members: Int, val canDelete: Boolean)

/** The rows and totals the page shows for the current tab and search. */
data class AccessListState(
    val tab: AccessTab = AccessTab.PEOPLE,
    val query: String = "",
    /** The Groups tab's permission filter (null = All). */
    val groupFilter: Permission? = null,
    /** How many groups can do each permission, for the filter chips. */
    val permissionCounts: Map<Permission, Int> = emptyMap(),
    /** Groups ticked for deleting together. */
    val pickedGroups: Set<String> = emptySet(),
    /** How many of the shown groups could be ticked (deletable ones), for "Select all N". */
    val selectableGroups: Int = 0,
    val people: List<PersonRow> = emptyList(),
    val groups: List<GroupRow> = emptyList(),
    val peopleTotal: Int = 0,
    val groupsTotal: Int = 0,
    val nowMillis: Long = 0,
)

/** People and access: who is in which group, for which properties. */
@HiltViewModel
class AccessViewModel @Inject constructor(
    private val useCases: AccessUseCases,
    observeAuthState: ObserveAuthStateUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    var state by mutableStateOf(AccessState())
        private set

    private fun set(f: AccessState.() -> AccessState) { state = state.f() }

    init {
        // When the session ends, close every form and clear the search.
        viewModelScope.launch {
            observeAuthState().collect { if (it is AuthState.SignedOut) state = AccessState() }
        }
        // People and groups are read from the server whenever the page opens; offline the saved copy stays.
        viewModelScope.launch { online(quietWhenOffline = true) { useCases.refresh() } }
    }

    private val groupList = useCases.observeGroups().observedIn(viewModelScope)
    private val peopleList = useCases.observePeople().observedIn(viewModelScope)

    val groups: List<Group> get() = groupList.value
    val people: List<Person> get() = peopleList.value

    /** Runs a call to the server; when it fails the user is told why (never the server's own text). Returns whether it worked. */
    private suspend fun online(quietWhenOffline: Boolean = false, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: OnlineException) {
        if (!(quietWhenOffline && e.error == OnlineError.Offline)) set { copy(notice = e.error.toUiText()) }
        false
    }

    fun consumeNotice() = set { copy(notice = null) }

    fun setTab(t: AccessTab) = set { copy(tab = t, query = "", groupFilter = null, pickedGroups = emptySet()) }
    fun setGroupFilter(p: Permission?) = set { copy(groupFilter = p) }
    fun setQuery(q: String) = set { copy(query = q) }

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }
    fun peopleIn(groupId: String): Int = people.count { it.groupId == groupId }

    /** Matches the email or the name of the person's group. */
    val visiblePeople: List<Person>
        get() = state.query.trim().lowercase().let { q ->
            people.filter { q.isEmpty() || q in it.email.lowercase() || q in group(it.groupId)?.name.orEmpty().lowercase() }
        }

    val visibleGroups: List<Group>
        get() = state.query.trim().lowercase().let { q ->
            val f = state.groupFilter
            groups.filter { (q.isEmpty() || q in it.name.lowercase() || q in it.description.lowercase()) && (f == null || it.can(f)) }
        }

    /** The page's rows for the current tab and search; [me] is the signed-in user's email. */
    fun listState(me: String?): AccessListState {
        val now = time.nowMillis()
        return AccessListState(
            tab = state.tab,
            query = state.query,
            people = visiblePeople.map {
                val isMe = it.email.equals(me, ignoreCase = true)
                PersonRow(it, group(it.groupId), isMe, if (isMe) now else it.lastSeenAt)
            },
            groups = visibleGroups.map { GroupRow(it, peopleIn(it.id), canDelete(it)) },
            groupFilter = state.groupFilter,
            permissionCounts = Permission.entries.associateWith { p -> groups.count { it.can(p) } },
            pickedGroups = pickedGroups.map { it.id }.toSet(),
            selectableGroups = visibleGroups.count(::canDelete),
            peopleTotal = people.size,
            groupsTotal = groups.size,
            nowMillis = now,
        )
    }

    // ---- People

    fun newPerson() {
        val firstGroup = groups.firstOrNull { !it.locked } ?: groups.first()
        set { copy(personForm = Person("", "", firstGroup.id, allProperties = false)) }
    }
    fun editPerson(p: Person) = set { copy(personForm = p) }
    fun closePersonForm() = set { copy(personForm = null) }

    /** Why [p] can't be saved yet, or null. */
    fun personProblem(p: Person): PersonProblem? = useCases.validatePerson(p)

    /** Saves [p]; returns why it can't be saved instead, or null when it is on its way (the form closes once the server has it). */
    fun savePerson(p: Person): PersonProblem? {
        useCases.validatePerson(p)?.let { return it }
        viewModelScope.launch {
            var problem: PersonProblem? = null
            if (online { problem = useCases.savePerson(p) } && problem == null) set { copy(personForm = null) }
        }
        return null
    }

    fun askRemovePerson(p: Person?) = set { copy(personToRemove = p) }
    fun removePerson(p: Person) {
        viewModelScope.launch {
            val removed = online { useCases.removePerson(p) }
            set { copy(personToRemove = null, personForm = if (removed) null else personForm) }
        }
    }

    // ---- Groups

    fun newGroup() = set { copy(groupForm = Group("", "", "", emptySet())) }
    fun editGroup(g: Group) = set { copy(groupForm = g) }
    fun closeGroupForm() = set { copy(groupForm = null, groupToConfirm = null) }

    /** Why [g] can't be saved yet, or null. */
    fun groupProblem(g: Group): GroupProblem? = useCases.validateGroup(g)

    /** How many people [g] would give every property to when saved (they are asked first). */
    fun givenAllProperties(g: Group): Int {
        people // recompose when people change
        return useCases.countGivenAllProperties(g)
    }

    /**
     * Saves [g]; returns why it can't be saved instead, or null. When the save would give people every
     * property, nothing is saved yet: [AccessState.groupToConfirm] is set until [confirmGroupSave] or [cancelGroupConfirm].
     */
    fun saveGroup(g: Group): GroupProblem? {
        useCases.validateGroup(g)?.let { return it }
        if (givenAllProperties(g) > 0) {
            set { copy(groupToConfirm = g) }
            return null
        }
        return commitGroup(g)
    }

    fun confirmGroupSave() {
        state.groupToConfirm?.let(::commitGroup)
    }

    fun cancelGroupConfirm() = set { copy(groupToConfirm = null) }

    private fun commitGroup(g: Group): GroupProblem? {
        useCases.validateGroup(g)?.let { return it }
        viewModelScope.launch {
            var problem: GroupProblem? = null
            if (online { problem = useCases.saveGroup(g) } && problem == null) set { copy(groupForm = null, groupToConfirm = null) }
        }
        return null
    }

    fun canDelete(g: Group): Boolean {
        people // recompose when people join or leave the group
        return useCases.canDeleteGroup(g)
    }
    fun askDeleteGroup(g: Group?) = set { copy(confirmDelete = g) }

    // ---- Selecting groups to delete together

    /** The ticked groups that still exist and can still be deleted. */
    val pickedGroups: List<Group> get() = groups.filter { it.id in state.pickedGroups && canDelete(it) }

    /** Ticks or unticks [g]; groups that can't be deleted (Admins, groups with people) can't be ticked. */
    fun toggleGroupPick(g: Group) {
        if (!canDelete(g)) return
        set { copy(pickedGroups = if (g.id in pickedGroups) pickedGroups - g.id else pickedGroups + g.id) }
    }

    /** Ticks every deletable group shown; when they all are already, clears instead. */
    fun selectAllGroups() {
        val all = visibleGroups.filter(::canDelete).map { it.id }.toSet()
        set { copy(pickedGroups = if (all.isNotEmpty() && pickedGroups.containsAll(all)) emptySet() else all) }
    }

    fun clearGroupPick() = set { copy(pickedGroups = emptySet(), confirmDeletePicked = false) }
    fun askDeletePicked() = set { copy(confirmDeletePicked = pickedGroups.isNotEmpty()) }
    fun cancelDeletePicked() = set { copy(confirmDeletePicked = false) }

    /** Deletes every ticked group that can still be deleted, then ends the selection. */
    fun deletePicked() {
        val doomed = pickedGroups
        viewModelScope.launch {
            doomed.forEach { online { useCases.deleteGroup(it) } }
            set { copy(pickedGroups = emptySet(), confirmDeletePicked = false) }
        }
    }
    fun deleteGroup(g: Group) {
        viewModelScope.launch {
            online { useCases.deleteGroup(g) }
            set { copy(confirmDelete = null, groupForm = null) }
        }
    }
}
