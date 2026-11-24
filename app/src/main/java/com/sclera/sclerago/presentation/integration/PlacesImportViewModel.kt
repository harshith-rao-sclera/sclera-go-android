package com.sclera.sclerago.presentation.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlaceNode
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.VdmsPlaces
import com.sclera.sclerago.domain.model.isSelectable
import com.sclera.sclerago.domain.model.key
import com.sclera.sclerago.domain.model.keysUnder
import com.sclera.sclerago.domain.model.missingParents
import com.sclera.sclerago.domain.model.selectableKeys
import com.sclera.sclerago.domain.model.withParents
import com.sclera.sclerago.domain.model.withoutOrphans
import com.sclera.sclerago.domain.usecase.integration.VdmsPlacesUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.model.OnlineOnly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Buildings, floors & locations on the Integration page, as the web's: they don't sync with the linked VDMS, so an admin
 * sends this property's to VDMS or imports VDMS's here by hand. The sheet asks VDMS for a fresh list when it opens.
 * Online only.
 */
@HiltViewModel
class PlacesImportViewModel @Inject constructor(private val useCases: VdmsPlacesUseCases) : ViewModel() {
    var state by mutableStateOf(PlacesUiState())
        private set

    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    /** Checks on a send VDMS is still working on while no sheet is open. */
    private var pageWatchJob: Job? = null
    /** The open sheet's wait for its send. */
    private var sheetWatchJob: Job? = null
    private var messageJob: Job? = null

    init {
        viewModelScope.launch { useCases.configureBlock().collect { block -> set { copy(access = block) } } }
    }

    private fun set(f: PlacesUiState.() -> PlacesUiState) {
        state = state.f()
    }

    /** Changes the open sheet and works its tree out again. */
    private fun setSheet(f: PlacesSheetState.() -> PlacesSheetState) = set { copy(sheet = sheet?.f()?.withView(places)) }

    private fun now(): Long = System.currentTimeMillis()

    /** Loads [propertyId]'s places (only called once the property is known to be linked to a VDMS). */
    fun bind(propertyId: String) {
        listOf(loadJob, refreshJob, pageWatchJob, sheetWatchJob).forEach { it?.cancel() }
        state = PlacesUiState(propertyId = propertyId, access = state.access)
        if (propertyId.isNotEmpty()) load()
    }

    /** Loads both sides; a failure shows on the card with Try again. */
    fun load() {
        loadJob?.cancel()
        set { copy(loading = true) }
        loadJob = viewModelScope.launch {
            val found = fetch()
            set { copy(loading = false, failed = found == null && places == null) }
            watchPage()
        }
    }

    /**
     * Both sides, now; null when they couldn't be fetched (what was shown stays). In an open sheet, ticks on places no
     * longer there (or no longer choosable) go, and so do the places whose parent went with them.
     */
    private suspend fun fetch(): VdmsPlaces? {
        val found = try {
            useCases.get(state.propertyId)
        } catch (_: OnlineException) {
            return null
        }
        set {
            val kept = sheet?.let { sh ->
                val tree = found.treeFor(sh.direction)
                val still = tree.selectableKeys()
                sh.copy(selected = sh.selected.filter { it in still }.toSet().withoutOrphans(tree.missingParents()).selected)
            }
            copy(places = found, failed = false, pushActive = found.push?.isActive(now()) == true, sheet = kept?.withView(found))
        }
        return found
    }

    /** A send VDMS is still working on: checked every few seconds for a minute, while no sheet is open (as the web). */
    private fun watchPage() {
        pageWatchJob?.cancel()
        if (state.sheet != null || !state.pushActive) return
        pageWatchJob = viewModelScope.launch {
            repeat(PAGE_CHECKS) {
                delay(PAGE_CHECK_MS)
                val found = fetch()
                if (found != null && found.push?.isActive(now()) != true) return@launch
            }
        }
    }

    /**
     * Asks VDMS to send its list again, then checks every 2 seconds for up to 30 until it has; when it hasn't, says so.
     * The stored list shows meanwhile, except while an open sheet waits for its first answer ([PlacesSheetState.firstLoad]).
     */
    fun refresh() {
        if (state.refreshing) return
        refreshJob?.cancel()
        set { copy(refreshing = true) }
        val before = state.places?.snapshotAt
        refreshJob = viewModelScope.launch {
            val asked = try {
                useCases.refresh(state.propertyId)
            } catch (_: OnlineException) {
                refreshed()
                flash(UiText.Res(R.string.pi_refresh_failed))
                return@launch
            }
            repeat(REFRESH_CHECKS) {
                delay(CHECK_MS)
                val at = fetch()?.snapshotAt
                // Newer than when VDMS was asked (by the server's clock), or just newer when the server didn't say.
                if (at != null && (if (asked != null) at >= asked else at != before)) {
                    refreshed()
                    return@launch
                }
            }
            refreshed()
            flash(UiText.Res(R.string.pi_refresh_late))
        }
    }

    /** VDMS answered, or the ask gave up: the open sheet shows what there is. */
    private fun refreshed() = set { copy(refreshing = false, sheet = sheet?.copy(firstLoad = false)) }

    /** An online-only button was tapped offline: says why it needs the server. */
    fun explainOffline() = flash(UiText.Res(OnlineOnly.INTEGRATION.message))

    private fun flash(msg: UiText) {
        set { copy(message = msg) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(MESSAGE_MS)
            set { copy(message = null) }
        }
    }

    // ---------------------------------------------------------------- History

    fun openHistory() = set { copy(historyOpen = true) }

    fun closeHistory() = set { copy(historyOpen = false) }

    // ---------------------------------------------------------------- Sheet

    /**
     * Opens Send to VDMS or Import from VDMS (not while a send is still running, for sending). VDMS's list is a copy from
     * its last send, so a fresh one is asked for at once, as the web does.
     */
    fun open(direction: PlacesDirection) {
        val s = state
        if (s.access != null || s.places == null || s.loading || s.sheet != null) return
        if (direction == PlacesDirection.TO_VDMS && s.pushActive) return
        pageWatchJob?.cancel()
        set { copy(sheet = PlacesSheetState(direction).withView(places)) }
        refresh()
    }

    /** Closes the sheet and reads both sides again, as the web. */
    fun close() {
        sheetWatchJob?.cancel()
        set { copy(sheet = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            fetch()
            watchPage()
        }
    }

    fun setSearch(q: String) = setSheet { copy(search = q) }

    fun setHideExisting(on: Boolean) = setSheet { copy(hideExisting = on) }

    fun toggleOpen(n: PlaceNode) = setSheet { copy(expanded = if (n.key in expanded) expanded - n.key else expanded + n.key) }

    /**
     * Ticks or unticks [n] (a row as shown) and what can be added under it; unticking takes off what is under it too.
     * Ticking brings along the parents the other side lacks; unticking one drops what needs it. The sheet says so.
     */
    fun toggle(n: PlaceNode) {
        val sh = state.sheet ?: return
        val keys = n.keysUnder()
        val on = if (n.isSelectable) n.key in sh.selected else keys.all { it in sh.selected }
        choose(if (on) sh.selected - keys.toSet() else sh.selected + keys, adding = !on)
    }

    /** Ticks every place shown that can be added (and the parents they need). */
    fun selectAll() {
        state.sheet?.let { choose(it.selected + it.view.addable, adding = true) }
    }

    /** Ticks every changed place shown, to update it on the other side (and the parents they need). */
    fun selectChanges() {
        state.sheet?.let { choose(it.selected + it.view.changes, adding = true) }
    }

    fun clear() = setSheet { copy(selected = emptySet(), parentNote = null) }

    private fun choose(next: Set<PlaceKey>, adding: Boolean) {
        val sh = state.sheet ?: return
        val needs = state.places?.treeFor(sh.direction).orEmpty().missingParents()
        val change = if (adding) next.withParents(needs) else next.withoutOrphans(needs)
        val note = when {
            adding -> change.added.takeIf { it.isNotEmpty() }?.let { parentsAddedText(it, sh.direction) }
            change.removed > 0 -> orphansRemovedText(change.removed)
            else -> null
        }
        setSheet { copy(selected = change.selected, parentNote = note) }
    }

    /**
     * Sends the ticked places to VDMS (it adds them in the background: the sheet waits up to a minute for the result), or
     * imports them from VDMS's list at once.
     */
    fun submit() {
        val sh = state.sheet ?: return
        if (sh.selected.isEmpty() || sh.submitting || sh.waiting || sh.done || state.noSnapshot(sh.direction)) return
        setSheet { copy(submitting = true) }
        viewModelScope.launch {
            try {
                if (sh.direction == PlacesDirection.TO_VDMS) {
                    val push = useCases.send(state.propertyId, sh.selected)
                    if (push.isActive(now())) {
                        setSheet { copy(waiting = true) }
                        waitForSend(push.id)
                    } else {
                        setSheet { copy(result = push.result) }
                    }
                } else {
                    val result = useCases.importPlaces(state.propertyId, sh.selected)
                    setSheet { copy(result = result) }
                    fetch()
                }
            } catch (e: OnlineException) {
                // The server's own text can hold internal details, so the app's words are shown instead.
                flash(
                    UiText.Res(
                        when {
                            e.error !is OnlineError.Conflict -> R.string.pi_import_failed
                            sh.direction == PlacesDirection.TO_VDMS -> R.string.pi_send_busy
                            else -> R.string.pi_no_snapshot
                        },
                    ),
                )
            } finally {
                setSheet { copy(submitting = false) }
            }
        }
    }

    /** VDMS works on a send in the background: checked every 2 seconds for up to a minute (a failed check is tried again). */
    private fun waitForSend(pushId: String) {
        sheetWatchJob?.cancel()
        sheetWatchJob = viewModelScope.launch {
            repeat(SEND_CHECKS) {
                delay(CHECK_MS)
                val push = fetch()?.push
                if (push != null && push.id == pushId && !push.isActive(now())) {
                    setSheet { copy(waiting = false, result = push.result) }
                    return@launch
                }
            }
            setSheet { copy(waiting = false, timedOut = true) }
        }
    }

    private companion object {
        const val CHECK_MS = 2_000L
        /** 30 seconds of checks for VDMS's list. */
        const val REFRESH_CHECKS = 15
        /** A minute of checks for a send, in the sheet. */
        const val SEND_CHECKS = 30
        const val PAGE_CHECK_MS = 3_000L
        /** A minute of checks for a send, on the page. */
        const val PAGE_CHECKS = 20
        const val MESSAGE_MS = 3_200L
    }
}
