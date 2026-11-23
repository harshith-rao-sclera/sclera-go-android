package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PermissionInfo
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.access.BrowseDirectoryUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccessTab { PEOPLE, GROUPS }

/** What People and access shows: the tab, search and open forms. The groups and people are in [AccessViewModel.groups] / [AccessViewModel.people]. */
data class AccessState(
    val tab: AccessTab = AccessTab.PEOPLE,
    /** The People tab's search (the web searches people only). */
    val query: String = "",
    /** Person being added (id == "") or edited; null when the form is closed. */
    val personForm: Person? = null,
    /** Group being added (id == "") or edited; null when the form is closed. */
    val groupForm: Group? = null,
    /** Group waiting for delete confirmation. */
    val confirmDelete: Group? = null,
    /** What just happened ("Ada saved") or why it couldn't (shown once, then consumed). */
    val notice: UiText? = null,
)

/** A person in the list and their group (null = no access). */
data class PersonRow(val person: Person, val group: Group?)

/** A group in the list and how many people are in it. */
data class GroupRow(val group: Group, val members: Int)

/** The rows and totals the page shows for the current tab and search. */
data class AccessListState(
    val tab: AccessTab = AccessTab.PEOPLE,
    val query: String = "",
    val people: List<PersonRow> = emptyList(),
    val groups: List<GroupRow> = emptyList(),
    val peopleTotal: Int = 0,
    val groupsTotal: Int = 0,
    val nowMillis: Long = 0,
    /** People and groups are being read from the server and nothing is saved on the device yet: a loader shows instead. */
    val loading: Boolean = false,
    /** A pull to refresh is reading people and groups from the server again. */
    val refreshing: Boolean = false,
    /** Why people and groups couldn't be read, when there is nothing saved to show ("Not available", as on the web). */
    val unavailable: UiText? = null,
    /** Whether the add button works: adding a person needs a group to put them in; a new group needs the lists. */
    val canAdd: Boolean = false,
    /** Everyone else the organisation's directory lists (not added here yet), under the users, a page at a time. */
    val others: List<DirectoryPerson> = emptyList(),
    /** Another page of [others] can be asked for ("Show more users"). */
    val othersHasMore: Boolean = false,
    /** A page of [others] is being read. */
    val othersLoading: Boolean = false,
    /** Why the directory couldn't list the others, when it couldn't. */
    val othersReason: UiText? = null,
)

/** The directory's people listed under the users so far: [seen] is everyone listed (added here or not). */
private data class Others(
    val people: List<DirectoryPerson> = emptyList(),
    val seen: Set<String> = emptySet(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val reason: UiText? = null,
)

/** People and access: who is in which group, for which properties (the web's Users page). */
@HiltViewModel
class AccessViewModel @Inject constructor(
    private val useCases: AccessUseCases,
    observeAuthState: ObserveAuthStateUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    var state by mutableStateOf(AccessState())
        private set

    private fun set(f: AccessState.() -> AccessState) { state = state.f() }

    /** A server read (when the page opened, or a pull) hasn't finished yet (worked or not); [pulled]: it was a pull to refresh. */
    private var refreshing by mutableStateOf(true)
    private var pulled by mutableStateOf(false)

    /** Why the last read failed (null once one works). */
    private var loadError by mutableStateOf<UiText?>(null)

    init {
        // When the session ends, close every form and clear the search.
        viewModelScope.launch {
            observeAuthState().collect { if (it is AuthState.SignedOut) state = AccessState() }
        }
        // People and groups are read from the server whenever the page opens; offline the saved copy stays.
        load()
    }

    /** Pull to refresh: reads people and groups from the server again; offline the user is told, and the saved copy stays. */
    fun refresh() {
        if (refreshing) return
        refreshing = true
        pulled = true
        load()
    }

    /**
     * Reads people, groups and permissions. With nothing saved, a failure shows on the page instead of the lists
     * (as the web does); with a saved copy, only a pull says why (and never just for being offline when opening).
     */
    private fun load() {
        viewModelScope.launch {
            try {
                useCases.refresh()
                loadError = null
            } catch (e: OnlineException) {
                loadError = e.error.toUiText()
                val saved = people.isNotEmpty() || groups.isNotEmpty()
                if (saved && (pulled || e.error != OnlineError.Offline)) set { copy(notice = loadError) }
            } finally {
                refreshing = false
                pulled = false
            }
        }
    }

    private val groupList = useCases.observeGroups().observedIn(viewModelScope)
    private val peopleList = useCases.observePeople().observedIn(viewModelScope)
    private val permissionList = useCases.observePermissions().observedIn(viewModelScope)

    val groups: List<Group> get() = groupList.value
    val people: List<Person> get() = peopleList.value

    /** The server's permissions (names, descriptions, order) by key; empty until loaded. */
    val permissionInfo: Map<String, PermissionInfo> get() = permissionList.value.associateBy { it.key }

    // ---- The organisation's directory, as the web uses it

    /** Everyone else the directory lists, under the users (searched with the users' search). */
    private var others by mutableStateOf(Others())
    private var othersJob: Job? = null

    init {
        loadOthers(1) // everyone, before anything is searched
    }

    /**
     * Reads [page] of the people the directory lists for the search: page 1 starts again. A page with nobody new in
     * it means the list has come round again, so there is nothing more to show (as the web decides).
     */
    private fun loadOthers(page: Int) {
        othersJob?.cancel()
        val text = state.query
        others = others.copy(loading = true)
        othersJob = viewModelScope.launch {
            if (page == 1 && text.isNotBlank()) delay(SUGGEST_DELAY_MS)
            others = try {
                val found = useCases.browseDirectory(text, BrowseDirectoryUseCase.PAGE, page)
                val before = if (page == 1) Others() else others
                val fresh = found.people.filter { it.email !in before.seen }
                Others(
                    people = before.people + fresh.filter { it.userId == null },
                    seen = before.seen + fresh.map { it.email },
                    page = page,
                    hasMore = found.available && found.hasMore && fresh.isNotEmpty(),
                    reason = if (found.available) null else directoryReason(found.reason),
                )
            } catch (e: OnlineException) {
                Others(reason = e.error.toUiText())
            }
        }
    }

    /** The next page of the directory, asked for when the Users list is scrolled near its end. */
    fun moreOthers() {
        if (others.hasMore && !others.loading) loadOthers(others.page + 1)
    }

    /** People from the directory to suggest for the email being typed when adding someone (everyone when nothing is typed). */
    var suggestions by mutableStateOf(emptyList<DirectoryPerson>())
        private set

    /** Why there are no suggestions, when there are none (so an empty list is never a mystery, as on the web). */
    var suggestionHint by mutableStateOf<UiText?>(null)
        private set
    private var suggesting: Job? = null

    /** Asks the directory for people matching [text] (a moment after typing stops), leaving out those already here. */
    fun suggest(text: String) {
        suggesting?.cancel()
        suggesting = viewModelScope.launch {
            if (text.isNotBlank()) delay(SUGGEST_DELAY_MS)
            try {
                val found = useCases.browseDirectory(text, BrowseDirectoryUseCase.SUGGESTIONS)
                val newcomers = found.people.filter { it.userId == null }
                suggestions = newcomers
                suggestionHint = when {
                    !found.available -> UiText.Res(R.string.access_suggest_unavailable, listOf(directoryReason(found.reason)))
                    found.people.isEmpty() -> if (text.isNotBlank()) UiText.Res(R.string.access_suggest_none) else null
                    newcomers.isEmpty() -> UiText.Res(R.string.access_suggest_all_here)
                    else -> null
                }
            } catch (e: OnlineException) {
                suggestions = emptyList()
                suggestionHint = UiText.Res(R.string.access_suggest_failed, listOf(e.error.toUiText()))
            }
        }
    }

    fun clearSuggestions() {
        suggesting?.cancel()
        suggestions = emptyList()
        suggestionHint = null
    }

    /** The server's reason the directory isn't available, or the web's words when it gave none. */
    private fun directoryReason(reason: String?): UiText =
        reason?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Res(R.string.access_directory_unavailable)

    /** Runs a change on the server; when it fails the user is told why (never the server's own text). Returns whether it worked. */
    private suspend fun online(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: OnlineException) {
        set { copy(notice = e.error.toUiText()) }
        false
    }

    fun consumeNotice() = set { copy(notice = null) }

    fun setTab(t: AccessTab) {
        val searched = state.query.isNotBlank()
        set { copy(tab = t, query = "") }
        if (searched) loadOthers(1)
    }
    /** The Users search: filters the users here and asks the directory again for the others. */
    fun setQuery(q: String) {
        set { copy(query = q) }
        loadOthers(1)
    }

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }
    fun peopleIn(groupId: String): Int = people.count { it.groupId == groupId }

    /** Matches the person's name or email, as the web searches. */
    val visiblePeople: List<Person>
        get() = state.query.trim().lowercase().let { q ->
            people.filter { q.isEmpty() || q in "${it.fullName.orEmpty()} ${it.email}".lowercase() }
        }

    /** The page's rows for the current tab and search. */
    fun listState(): AccessListState {
        val nothingSaved = people.isEmpty() && groups.isEmpty()
        val unavailable = if (!refreshing && nothingSaved) loadError else null
        return AccessListState(
            tab = state.tab,
            query = state.query,
            people = visiblePeople.map { PersonRow(it, group(it.groupId)) },
            groups = groups.map { GroupRow(it, peopleIn(it.id)) },
            peopleTotal = people.size,
            groupsTotal = groups.size,
            nowMillis = time.nowMillis(),
            // Saved people and groups show straight away; only an empty page waits for the server.
            loading = refreshing && nothingSaved,
            refreshing = pulled,
            unavailable = unavailable,
            canAdd = if (state.tab == AccessTab.PEOPLE) groups.isNotEmpty() else unavailable == null,
            others = others.people,
            othersHasMore = others.hasMore,
            othersLoading = others.loading,
            othersReason = others.reason,
        )
    }

    // ---- People

    /**
     * Adding someone starts in the first group that isn't Admins, for selected properties (as the web does); [invite]
     * is someone picked from the directory, whose email and name are filled in.
     */
    fun newPerson(invite: DirectoryPerson? = null) {
        val firstGroup = groups.firstOrNull { !it.locked } ?: groups.firstOrNull() ?: return
        clearSuggestions()
        set {
            copy(personForm = Person("", invite?.email.orEmpty(), firstGroup.id, allProperties = false, inviteName = invite?.name))
        }
    }
    fun editPerson(p: Person) = set { copy(personForm = p) }
    fun closePersonForm() {
        clearSuggestions()
        set { copy(personForm = null) }
    }

    /** Why [p] can't be saved yet, or null. */
    fun personProblem(p: Person): PersonProblem? = useCases.validatePerson(p)

    /** Saves [p]; returns why it can't be saved instead, or null when it is on its way (the form closes once the server has it). */
    fun savePerson(p: Person): PersonProblem? {
        useCases.validatePerson(p)?.let { return it }
        viewModelScope.launch {
            var problem: PersonProblem? = null
            if (online { problem = useCases.savePerson(p) } && problem == null) {
                val done = if (p.id.isEmpty()) UiText.Res(R.string.access_added, listOf(p.email.trim()))
                else UiText.Res(R.string.access_access_updated, listOf(p.name))
                clearSuggestions()
                set { copy(personForm = null, notice = done) }
                loadOthers(1) // whoever was just added is no longer one of the others
            }
        }
        return null
    }

    // ---- Groups

    fun newGroup() = set { copy(groupForm = Group("", "", "", emptySet())) }
    fun editGroup(g: Group) = set { copy(groupForm = g) }
    fun closeGroupForm() = set { copy(groupForm = null) }

    /** Why [g] can't be saved yet, or null. */
    fun groupProblem(g: Group): GroupProblem? = useCases.validateGroup(g)

    /** Saves [g]; returns why it can't be saved instead, or null when it is on its way (the form closes once the server has it). */
    fun saveGroup(g: Group): GroupProblem? {
        useCases.validateGroup(g)?.let { return it }
        viewModelScope.launch {
            var problem: GroupProblem? = null
            if (online { problem = useCases.saveGroup(g) } && problem == null) {
                val done = if (g.id.isEmpty()) R.string.access_added else R.string.access_saved
                set { copy(groupForm = null, notice = UiText.Res(done, listOf(g.name.trim()))) }
            }
        }
        return null
    }

    fun askDeleteGroup(g: Group?) = set { copy(confirmDelete = g) }

    /**
     * "Go to users" on "can't be deleted yet" (a group that still has people in it, as the web): the question and the
     * group's form close and the Users tab shows, to move them to another group.
     */
    fun goToUsers() {
        set { copy(confirmDelete = null, groupForm = null) }
        setTab(AccessTab.PEOPLE)
    }

    /** Deletes [g]; a group that still has people in it isn't sent (the server refuses it; the page says so first). */
    fun deleteGroup(g: Group) {
        val members = peopleIn(g.id)
        if (members > 0) return set { copy(confirmDelete = null) }
        viewModelScope.launch {
            var deleted = false
            online { deleted = useCases.deleteGroup(g, members) }
            set {
                copy(
                    confirmDelete = null,
                    groupForm = if (deleted) null else groupForm,
                    notice = if (deleted) UiText.Res(R.string.access_deleted, listOf(g.name)) else notice,
                )
            }
        }
    }
}

/** How long typing must pause before the directory is asked. */
private const val SUGGEST_DELAY_MS = 300L
