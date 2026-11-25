package com.sclera.sclerago.presentation.assign

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.presentation.assets.message
import com.sclera.sclerago.domain.usecase.access.ObserveAccessChangesUseCase
import com.sclera.sclerago.domain.usecase.assign.AssignUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Who the assets go to, as the web's picker: nobody (takes them off whoever has them), or a person by email. */
sealed interface AssignChoice {
    data object Nobody : AssignChoice

    data class Person(val email: String) : AssignChoice
}

/**
 * The Assign asset sheet: the assets it assigns, the search, who matches, the signed-in user's email ([me], for "Assign
 * to me"), what it opened on ([initial]: one asset's current assignee) and what is picked (nothing until a choice is made).
 */
data class AssignSheetState(
    val ids: Set<String>,
    val query: String = "",
    val people: List<DirectoryPerson> = emptyList(),
    val searching: Boolean = true,
    val me: String? = null,
    val initial: AssignChoice? = null,
    val picked: AssignChoice? = initial,
) {
    /** Apply works once a choice is made, as the web's. */
    val canApply: Boolean get() = picked != null

    /** Something other than what it opened on is picked (closing asks first). */
    val changed: Boolean get() = picked != initial
}

/** Assign asset (asset list selection and asset details), as the web's: the assets go to one person, picked by email, or to nobody. */
@HiltViewModel
class AssignViewModel @Inject constructor(
    private val useCases: AssignUseCases,
    accessChanges: ObserveAccessChangesUseCase,
) : ViewModel() {
    var sheet by mutableStateOf<AssignSheetState?>(null)
        private set

    /** The signed-in user may assign (an admin who can collect), kept up to date as the access changes. */
    private var allowed by mutableStateOf(useCases.assign.allowed())

    /** The signed-in user may take unassigned assets for themselves ("Assign to me": collects, not an admin). */
    private var takeAllowed by mutableStateOf(useCases.take.allowed())

    /** A take is on its way (the web's "Assigning…"): another tap waits for it. */
    var taking by mutableStateOf(false)
        private set

    private var propertyId = ""
    private var searchJob: Job? = null
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** What happened, to flash on the dashboard. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    init {
        // Assign shows (and its sheet stays open) only while the user may assign: re-read whenever the access changes.
        viewModelScope.launch {
            accessChanges().collect {
                allowed = useCases.assign.allowed()
                takeAllowed = useCases.take.allowed()
                if (!allowed) close()
            }
        }
    }

    /** The property whose assets are assigned; changing it closes the sheet. */
    fun bind(property: String) {
        if (property == propertyId) return
        propertyId = property
        close()
    }

    /** Whether Assign shows: the signed-in user is an admin who can collect, the only ones the server lets assign. */
    fun mayAssign(): Boolean = allowed

    /** Whether "Assign to me" shows in place of Assign: someone who collects but isn't an admin (the web's assignsToAnyone). */
    fun mayTake(): Boolean = !allowed && takeAllowed

    /** Why "Assign to me" is greyed out for [assets] (what a tap says), null when they can be taken. */
    fun takeReason(assets: Collection<Asset>): UiText? = when {
        taking -> UiText.Res(R.string.assign_taking)
        else -> useCases.take.refusal(assets)?.let { UiText.Res(it.message(assets.size > 1)) }
    }

    /**
     * "Assign to me": takes [assets] for the signed-in user, then says how many, and calls [onDone] (to clear the
     * selection, as the web). When they can't be taken it says why instead.
     */
    fun take(assets: Collection<Asset>, onDone: () -> Unit = {}) {
        if (!mayTake() || assets.isEmpty() || propertyId.isEmpty()) return
        if (taking) {
            message.tryEmit(UiText.Res(R.string.assign_taking))
            return
        }
        useCases.take.refusal(assets)?.let {
            message.tryEmit(UiText.Res(it.message(assets.size > 1)))
            return
        }
        val property = propertyId
        taking = true
        viewModelScope.launch {
            try {
                useCases.take(property, assets)
                message.emit(UiText.Plural(R.plurals.assign_taken, assets.size, listOf(assets.size)))
            } catch (e: OnlineException) {
                // The server's own text can hold internal details, so the app's words are shown instead.
                message.emit(UiText.Res(if (e.error == OnlineError.Forbidden) R.string.assign_take_refused else R.string.assign_take_failed))
            } finally {
                // Some may have been taken before one failed: the list shows where each one is now.
                taking = false
                onDone()
            }
        }
    }

    /**
     * Opens the sheet for [ids]. With one asset, [assignedTo] is its assignee (a user id): the sheet opens on that person
     * so it can be changed or cleared, as the web's; with several there's no common value, so it opens with nothing picked.
     */
    fun open(ids: Set<String>, assignedTo: String? = null) {
        if (ids.isEmpty() || propertyId.isEmpty() || !allowed) return
        val current = if (ids.size == 1) useCases.currentAssignee(propertyId, assignedTo) else null
        sheet = AssignSheetState(ids, me = useCases.myEmail(), initial = current?.let { AssignChoice.Person(it) })
        search("", debounce = false)
    }

    fun close() {
        searchJob?.cancel()
        sheet = null
    }

    /** Picks who the assets go to. */
    fun pick(choice: AssignChoice) {
        sheet = sheet?.copy(picked = choice)
    }

    fun setQuery(q: String) {
        sheet = sheet?.copy(query = q)
        search(q, debounce = true)
    }

    private fun search(q: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_MS)
            sheet = sheet?.copy(searching = true)
            val found = try {
                useCases.findAssignees(q)
            } catch (_: OnlineException) {
                emptyList() // As on the web, the list just comes back empty.
            }
            sheet = sheet?.copy(people = found, searching = false)
        }
    }

    /** Apply: closes the sheet and assigns the assets, then says what happened. False while nothing is picked. */
    fun submit(): Boolean {
        val s = sheet ?: return false
        val choice = s.picked ?: return false
        close()
        val property = propertyId
        val email = (choice as? AssignChoice.Person)?.email
        viewModelScope.launch {
            try {
                message.emit(done(useCases.assign(property, s.ids, email)))
            } catch (e: OnlineException) {
                // The server's own text can hold internal details, so the app's words are shown instead; a refusal is
                // the server's one rule here (admins only), which the web shows.
                message.emit(UiText.Res(if (e.error == OnlineError.Forbidden) R.string.assign_admin_only else R.string.assign_failed))
            }
        }
        return true
    }

    /**
     * "3 assets assigned to Ada (added as a Collector on this property)", as the web says it, or "3 assets unassigned";
     * then how many the server doesn't have yet (made here and not synced), which were left as they were.
     */
    private fun done(r: AssignResult): UiText {
        val who = r.name ?: r.email
        val count = r.assigned
        val main = when {
            who == null -> UiText.Plural(R.plurals.assign_done_nobody, count)
            r.added -> UiText.Plural(R.plurals.assign_done_added, count, listOf(count, who))
            r.givenAccess -> UiText.Plural(R.plurals.assign_done_access, count, listOf(count, who))
            else -> UiText.Plural(R.plurals.assign_done, count, listOf(count, who))
        }
        if (r.notFound == 0) return main
        val missing = UiText.Plural(R.plurals.assign_not_found, r.notFound)
        return if (r.assigned + r.unchanged == 0) missing else UiText.Res(R.string.assign_done_and_not_found, listOf(main, missing))
    }

    private companion object {
        const val SEARCH_MS = 300L
    }
}
