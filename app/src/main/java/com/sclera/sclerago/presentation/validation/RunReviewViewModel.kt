package com.sclera.sclerago.presentation.validation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.validation.GetValidationFieldsUseCase
import com.sclera.sclerago.domain.usecase.validation.MergeDuplicatesUseCase
import com.sclera.sclerago.domain.usecase.validation.RecheckOutcome
import com.sclera.sclerago.domain.usecase.validation.RunReviewUseCases
import com.sclera.sclerago.domain.usecase.validation.confidentFixes
import com.sclera.sclerago.domain.validation.DECISION_NOTE_MAX
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.FieldChoice
import com.sclera.sclerago.domain.validation.IssueValue
import com.sclera.sclerago.domain.validation.MergeAsset
import com.sclera.sclerago.domain.validation.ResultFilter
import com.sclera.sclerago.domain.validation.ReviewAssetInfo
import com.sclera.sclerago.domain.validation.RunAssetResult
import com.sclera.sclerago.domain.validation.RunExport
import com.sclera.sclerago.domain.validation.RunInsights
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sclera.sclerago.domain.validation.withFirstPage

/** One asset of the run opened from the list: its issues (every status) and what is typed per issue. */
data class AssetPanelState(
    val assetId: String,
    val issues: List<RunIssue> = emptyList(),
    val loading: Boolean = true,
    val typed: Map<String, String> = emptyMap(),
    /** Issues with a call in flight. */
    val busy: Set<String> = emptySet(),
    /** The asset as it is on this phone (Asset info); null until read, or when it isn't on this phone. */
    val info: ReviewAssetInfo? = null,
    val infoLoading: Boolean = true,
)

/** Merging duplicates: the assets, the one kept and the value chosen per field (by the asset it comes from). */
data class MergeState(
    val ids: List<String>,
    val assets: List<MergeAsset> = emptyList(),
    val keepId: String? = null,
    val choice: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** What was picked when it loaded (the first asset and its defaults), so closing after a change asks first. */
    val startKeepId: String? = null,
    val startChoice: Map<String, String> = emptyMap(),
) {
    val keep: MergeAsset? get() = assets.firstOrNull { it.id == keepId }

    /** Another asset to keep or another value picked: closing asks before discarding it. */
    val changed: Boolean get() = !loading && (keepId != startKeepId || choice != startChoice)
    val differing: List<String> get() = MergeDuplicatesUseCase.differingFields(assets)

    /** Field id → the value it gets: from the asset chosen for it. */
    fun values(): Map<String, IssueValue> = choice.mapNotNull { (f, a) -> assets.firstOrNull { it.id == a }?.values?.get(f)?.let { f to it } }.toMap()
}

data class RunReviewUiState(
    val propertyId: String = "",
    val jobId: String = "",
    val run: ValRun? = null,
    val runFailed: Boolean = false,
    val results: List<RunAssetResult> = emptyList(),
    val nextCursor: String? = null,
    val loadingResults: Boolean = true,
    /** The first page of results (for the filter in force) couldn't be read: the list says so, with Try again, not "No assets". */
    val resultsFailed: Boolean = false,
    val loadingMore: Boolean = false,
    /** A new filter, step or search is being read: the rows shown are the old filter's, so placeholders show instead. */
    val filtering: Boolean = false,
    /** A pull to refresh is reloading the run and its results. */
    val refreshing: Boolean = false,
    val filter: ResultFilter = ResultFilter(),
    /** Rows that just arrived while the run is going (highlighted briefly). */
    val fresh: Set<String> = emptySet(),
    val selected: Set<String> = emptySet(),
    val openIssues: List<RunIssue> = emptyList(),
    val insights: RunInsights? = null,
    /** Why the user can't fix values or run checks (Check), null when they can: those buttons are greyed out and say why. */
    val checkBlock: AccessBlock? = AccessBlock.CHECKING,
    /**
     * Why the user can't sign ticked assets off, send them back, merge duplicates or undo fixes (Validate), null when
     * they can: those buttons are greyed out and say why (the User Access Guide).
     */
    val validateBlock: AccessBlock? = AccessBlock.CHECKING,
    /** Asking for a recollection note for these assets. */
    val recollectFor: List<String>? = null,
    val recollectNote: String = "",
    val busy: Boolean = false,
    val panel: AssetPanelState? = null,
    val merge: MergeState? = null,
    val fields: List<FieldChoice> = emptyList(),
    /** A CSV fetched and waiting for the user to pick where to save it. */
    val export: RunExport? = null,
    /** The asset being checked again inside the run (Revalidate); one at a time. */
    val rechecking: String? = null,
    val message: UiText? = null,
) {
    val canCheck: Boolean get() = checkBlock == null
    val canValidate: Boolean get() = validateBlock == null
    val confidentCount: Int get() = openIssues.confidentFixes().size
    val panelResult: RunAssetResult? get() = panel?.let { p -> results.firstOrNull { it.assetId == p.assetId } }
}

/** One run's results page (the web's JobReview): progress, insights, steps, the assets with their issues and decisions. */
@HiltViewModel
class RunReviewViewModel @Inject constructor(
    private val useCases: RunReviewUseCases,
    private val fieldsOf: GetValidationFieldsUseCase,
) : ViewModel() {
    var state by mutableStateOf(RunReviewUiState())
        private set

    private var poll: Job? = null
    private var resultsJob: Job? = null

    /** Which filter change is the latest: only its end turns [RunReviewUiState.filtering] off. */
    private var filterRound = 0
    private var searchJob: Job? = null
    private var messageJob: Job? = null

    private fun set(f: RunReviewUiState.() -> RunReviewUiState) {
        state = state.f()
    }

    private val p get() = state.propertyId
    private val j get() = state.jobId

    init {
        // The buttons follow the access as it is fetched or changes, not only when the run opens (as the web).
        viewModelScope.launch {
            combine(useCases.access.observeCheckBlock(), useCases.access.observeValidateBlock(), ::Pair).collect { (check, validate) ->
                set {
                    copy(
                        checkBlock = check,
                        validateBlock = validate,
                        // Merging, recollection notes and ticking rows to decide on are the reviewer's (Validate) only:
                        // without it they close (while the access is being fetched, ticked rows stay ticked).
                        merge = merge?.takeIf { validate == null },
                        recollectFor = recollectFor?.takeIf { validate == null },
                        selected = if (validate?.lacksPermission == true) emptySet() else selected,
                    )
                }
            }
        }
    }

    fun bind(propertyId: String, jobId: String) {
        if (propertyId == state.propertyId && jobId == state.jobId) {
            // The same job opened again: start without the sheets and ticks left from last time, with fresh numbers.
            set { copy(panel = null, merge = null, selected = emptySet(), recollectFor = null, export = null) }
            viewModelScope.launch { refreshAll() }
            pollWhileActive()
            return
        }
        poll?.cancel()
        state = RunReviewUiState(
            propertyId = propertyId, jobId = jobId, checkBlock = useCases.access.checkBlock(),
            validateBlock = useCases.access.validateBlock(),
        )
        viewModelScope.launch {
            loadRun()
            loadResults(reset = true)
            if (state.run?.status?.finished == true) loadFinished()
            pollWhileActive()
        }
    }

    fun flash(msg: UiText) {
        set { copy(message = msg) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2800)
            set { copy(message = null) }
        }
    }

    private suspend fun loadRun() {
        try {
            val run = useCases.run(p, j)
            set { copy(run = run, runFailed = false) }
        } catch (e: OnlineException) {
            set { copy(runFailed = run == null) }
            if (state.run == null) flash(e.error.toUiText())
        }
    }

    private fun pollWhileActive() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive && state.run?.status?.active == true) {
                delay(POLL_MS)
                val before = state.run?.processed
                loadRun()
                val run = state.run ?: break
                if (run.processed != before) mergeFirstPage()
                if (run.status.finished) {
                    loadResults(reset = true, refresh = true)
                    loadFinished()
                }
            }
        }
    }

    /** After the run: the open issues (for confident fixes) and the insights. */
    private suspend fun loadFinished() {
        try {
            val issues = useCases.issues.open(p, j)
            set { copy(openIssues = issues) }
        } catch (_: OnlineException) {
        }
        try {
            val insights = useCases.insights(p, j)
            set { copy(insights = insights) }
        } catch (_: OnlineException) {
        }
    }

    // ---------------------------------------------------------------- Results

    /**
     * Loads the first page ([reset]) or the next one. [refresh]: the first page goes over the rows already shown, as the
     * web's (rows past it and the open asset's row stay; one whose asset was deleted or merged goes): [withFirstPage].
     */
    private suspend fun loadResults(reset: Boolean, refresh: Boolean = false) {
        val filter = state.filter
        val cursor = if (reset) null else state.nextCursor
        if (reset) set { copy(loadingResults = true, resultsFailed = false) } else set { copy(loadingMore = true) }
        try {
            val page = useCases.results(p, j, filter, cursor)
            if (filter != state.filter) return
            set {
                copy(
                    results = when {
                        reset && refresh -> withFirstPage(results, page, panel?.assetId)
                        reset -> page.items
                        else -> results + page.items.filter { n -> results.none { it.assetId == n.assetId } }
                    },
                    nextCursor = page.nextCursor,
                )
            }
        } catch (e: OnlineException) {
            flash(e.error.toUiText())
            // The first page failed: rows of another filter would be wrong, and none isn't "No assets". A refresh keeps
            // the rows it already had and only says why (the toast).
            if (reset && filter == state.filter) {
                set { if (refresh && results.isNotEmpty()) this else copy(results = emptyList(), nextCursor = null, resultsFailed = true) }
            }
        } finally {
            set { copy(loadingResults = false, loadingMore = false) }
        }
    }

    /** New rows on top while the run goes on (as the web does when `processed` changes). */
    private suspend fun mergeFirstPage() {
        try {
            val page = useCases.results(p, j, state.filter, null)
            val known = state.results.map { it.assetId }.toSet()
            val fresh = page.items.filter { it.assetId !in known }
            if (fresh.isEmpty()) return
            set { copy(results = fresh + results, fresh = fresh.map { it.assetId }.toSet()) }
            delay(FRESH_MS)
            set { copy(fresh = emptySet()) }
        } catch (_: OnlineException) {
        }
    }

    fun loadMore() {
        if (state.nextCursor == null || state.loadingMore) return
        viewModelScope.launch { loadResults(reset = false) }
    }

    /** Try again after the results couldn't be read: their first page, for the filter in force. */
    fun retryResults() {
        if (state.loadingResults) return
        resultsJob?.cancel()
        resultsJob = viewModelScope.launch { loadResults(reset = true) }
    }

    private fun refilter(f: ResultFilter, debounce: Boolean = false) {
        val round = ++filterRound
        set { copy(filter = f, selected = emptySet(), filtering = true) }
        resultsJob?.cancel()
        resultsJob = viewModelScope.launch {
            try {
                if (debounce) delay(SEARCH_MS)
                loadResults(reset = true)
            } finally {
                if (round == filterRound) set { copy(filtering = false) }
            }
        }
    }

    /** Outcome tab / legend button: tapping the active one clears it. */
    fun setOutcome(o: StepOutcome?) = refilter(state.filter.copy(outcome = if (state.filter.outcome == o) null else o))

    /** A step in the pipeline: the assets that didn't pass it; tapping it again clears it. */
    fun setStep(step: String?) = refilter(state.filter.copy(step = if (state.filter.step == step) null else step))

    fun setQuery(q: String) = refilter(state.filter.copy(query = q), debounce = true)

    fun toggleSelected(id: String) = set { copy(selected = if (id in selected) selected - id else selected + id) }

    /**
     * Long-press on an asset: ticks it to decide on several together, which is the reviewer's (Validate): as the web's
     * check boxes, nobody else ticks.
     */
    fun select(id: String) {
        if (state.validateBlock?.lacksPermission == true) return
        toggleSelected(id)
    }

    /** Why a fix to the asset ([status], its assignee [assignedTo], in Managed Assets when [managed]) would be refused; null when it wouldn't. */
    fun lockOf(status: Status?, assignedTo: String?, managed: Boolean): AssetLock? = useCases.access.lockOf(status, assignedTo, managed)

    /** The header tick: every row shown, or none. */
    fun selectAllShown(on: Boolean) = set { copy(selected = if (on) results.map { it.assetId }.toSet() else emptySet()) }

    fun clearSelection() = set { copy(selected = emptySet()) }

    /** Pull to refresh: reloads the run, its first page of results and (when finished) its issues and insights. */
    fun refresh() {
        if (state.refreshing || state.jobId.isEmpty()) return
        set { copy(refreshing = true) }
        viewModelScope.launch {
            try {
                refreshAll()
                pollWhileActive()
            } finally {
                set { copy(refreshing = false) }
            }
        }
    }

    /** Reloads the run, its first page and (when finished) its issues and insights after a change. */
    private suspend fun refreshAll() {
        loadRun()
        loadResults(reset = true, refresh = true)
        if (state.run?.status?.finished == true) loadFinished()
    }

    // ---------------------------------------------------------------- Actions on the run

    fun applyConfident() {
        if (state.busy) return
        set { copy(busy = true) }
        viewModelScope.launch {
            try {
                val r = useCases.applyConfident(p, state.openIssues)
                flash(UiText.Plural(R.plurals.rev_fixes_applied, r.accepted))
                refreshAll()
                state.panel?.let { reloadPanel(it.assetId) }
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            } finally {
                set { copy(busy = false) }
            }
        }
    }

    fun export() {
        if (state.busy) return
        set { copy(busy = true) }
        viewModelScope.launch {
            try {
                val e = useCases.export(p, j)
                set { copy(export = e) }
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            } finally {
                set { copy(busy = false) }
            }
        }
    }

    /** The user picked where to save the CSV (null: cancelled). */
    fun saveExport(uri: String?) {
        val e = state.export ?: return
        set { copy(export = null) }
        if (uri == null) return
        viewModelScope.launch {
            flash(UiText.Res(if (useCases.export.save(e, uri)) R.string.rev_export_saved else R.string.rev_export_failed))
        }
    }

    // ---------------------------------------------------------------- Decisions

    /** Mark as validated / Undo; Recollect asks for a note first ([askRecollect]). */
    fun decide(ids: List<String>, decision: Decision, note: String? = null) {
        if (ids.isEmpty() || state.busy) return
        set { copy(busy = true, recollectFor = null, recollectNote = "") }
        viewModelScope.launch {
            try {
                val outcome = useCases.decide(p, j, ids, decision, note)
                // Drafts and exceptions aren't signed off: the server leaves them as they are and names them.
                val skipped = outcome.notSubmitted.toSet()
                val applied = ids.filter { it !in skipped }
                // The asset's status follows a sign-off or a send-back (undoing one is read back from the server).
                val status = when (decision) {
                    Decision.VALIDATED -> Status.VALIDATED
                    Decision.RECOLLECT -> Status.DRAFT
                    else -> null
                }
                set {
                    copy(
                        results = results.map { r ->
                            if (r.assetId !in applied) r
                            else r.copy(
                                decision = if (decision == Decision.CLEAR) null else decision,
                                decisionNote = if (decision == Decision.RECOLLECT) note?.trim()?.takeIf { it.isNotEmpty() } else null,
                                assetStatus = status ?: r.assetStatus,
                            )
                        },
                        selected = emptySet(),
                    )
                }
                val msg = when (decision) {
                    Decision.VALIDATED -> R.plurals.rev_decided_validated
                    Decision.RECOLLECT -> R.plurals.rev_decided_recollect
                    // Excluded is no longer sent: it only comes back as an older run's decision.
                    Decision.EXCLUDED, Decision.CLEAR -> R.plurals.rev_decided_cleared
                }
                // One message at a time: what wasn't signed off matters more than the count that was.
                flash(
                    if (skipped.isNotEmpty()) UiText.Plural(R.plurals.sign_off_not_submitted, skipped.size)
                    else UiText.Plural(msg, outcome.updated),
                )
                loadRun()
                loadFinished()
                state.panel?.let { if (it.assetId in ids) reloadPanel(it.assetId) }
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            } finally {
                set { copy(busy = false) }
            }
        }
    }

    fun askRecollect(ids: List<String>?) = set { copy(recollectFor = ids, recollectNote = "") }
    fun setRecollectNote(v: String) = set { copy(recollectNote = v.take(DECISION_NOTE_MAX)) }
    fun sendRecollect() {
        val ids = state.recollectFor ?: return
        decide(ids, Decision.RECOLLECT, state.recollectNote)
    }

    // ---------------------------------------------------------------- One asset

    fun openAsset(assetId: String?) {
        if (assetId == null) {
            set { copy(panel = null) }
            return
        }
        set { copy(panel = AssetPanelState(assetId)) }
        viewModelScope.launch { reloadPanel(assetId) }
        viewModelScope.launch {
            val info = useCases.assetInfo(assetId)
            set { copy(panel = panel?.takeIf { it.assetId == assetId }?.copy(info = info, infoLoading = false)) }
        }
    }

    private suspend fun reloadPanel(assetId: String) {
        try {
            val issues = useCases.issues.ofAsset(p, j, assetId)
            set { copy(panel = panel?.takeIf { it.assetId == assetId }?.copy(issues = issues, loading = false)) }
        } catch (e: OnlineException) {
            set { copy(panel = panel?.copy(loading = false)) }
            flash(if (e.error == OnlineError.NotFound) UiText.Res(R.string.rev_asset_gone) else e.error.toUiText())
            // Deleted or merged since the run: its result has left the run, so off the list, and the run's totals again.
            if (e.error == OnlineError.NotFound) {
                set { copy(results = results.filterNot { it.assetId == assetId }) }
                loadRun()
            }
        }
    }

    fun setTyped(issueId: String, v: String) = set { copy(panel = panel?.let { it.copy(typed = it.typed + (issueId to v)) }) }

    fun applySuggestion(issue: RunIssue, value: IssueValue?) = onIssue(issue, R.string.rev_apply_failed) { useCases.fix.apply(p, issue.id, value) }

    fun applyTyped(issue: RunIssue) {
        val text = state.panel?.typed?.get(issue.id).orEmpty()
        if (text.isBlank()) return
        onIssue(issue, R.string.rev_apply_failed) { useCases.fix.applyText(p, issue.id, text) }
    }

    fun dismiss(issue: RunIssue) = onIssue(issue, R.string.rev_update_failed) { useCases.fix.dismiss(p, issue.id) }

    fun revert(issue: RunIssue) = onIssue(issue, R.string.rev_undo_failed) { useCases.revert(p, issue.id) }

    /**
     * "Revalidate" on the open asset (the web's): checks it again with this run's steps, inside the run (the rest of the
     * run isn't checked again), then shows its new result here. The sheet may move to another asset meanwhile.
     */
    fun recheck() {
        val assetId = state.panel?.assetId ?: return
        if (state.rechecking != null) return
        val property = p
        val job = j
        set { copy(rechecking = assetId) }
        viewModelScope.launch {
            try {
                val said = when (val outcome = useCases.recheck(property, job, assetId)) {
                    RecheckOutcome.Done -> {
                        if (state.jobId == job) {
                            refreshAll()
                            if (state.panel?.assetId == assetId) reloadPanel(assetId)
                        }
                        UiText.Res(R.string.rev_rechecked)
                    }
                    is RecheckOutcome.Failed -> outcome.detail?.let { UiText.Raw(it) } ?: UiText.Res(R.string.rev_recheck_failed)
                    RecheckOutcome.StillChecking -> UiText.Res(R.string.rev_recheck_still)
                }
                flash(said)
            } catch (e: OnlineException) {
                flash(
                    when (e.error) {
                        OnlineError.Offline, OnlineError.Forbidden, OnlineError.SessionExpired -> e.error.toUiText()
                        else -> UiText.Res(R.string.rev_recheck_failed)
                    },
                )
            } finally {
                set { copy(rechecking = null) }
            }
        }
    }

    private fun onIssue(issue: RunIssue, failed: Int, call: suspend () -> RunIssue) {
        val panel = state.panel ?: return
        if (issue.id in panel.busy) return
        set { copy(panel = this.panel?.let { it.copy(busy = it.busy + issue.id) }) }
        viewModelScope.launch {
            try {
                call()
                set { copy(panel = this.panel?.let { it.copy(typed = it.typed - issue.id) }) }
                reloadPanel(panel.assetId)
                refreshAll()
            } catch (e: OnlineException) {
                flash(
                    when (e.error) {
                        OnlineError.Offline, OnlineError.Forbidden, OnlineError.SessionExpired -> e.error.toUiText()
                        else -> UiText.Res(failed)
                    },
                )
            } finally {
                set { copy(panel = this.panel?.let { it.copy(busy = it.busy - issue.id) }) }
            }
        }
    }

    // ---------------------------------------------------------------- Merge duplicates

    fun openMerge(issue: RunIssue) {
        val ids = (listOf(issue.assetId) + issue.related).distinct()
        set { copy(merge = MergeState(ids)) }
        viewModelScope.launch {
            try {
                if (state.fields.isEmpty()) {
                    val f = runCatching { fieldsOf(p) }.getOrDefault(emptyList())
                    set { copy(fields = f) }
                }
                val assets = useCases.merge.load(p, ids)
                set { copy(merge = merge?.copy(assets = assets, loading = false)) }
                assets.firstOrNull()?.let { setMergeKeep(it.id) }
                set { copy(merge = merge?.let { it.copy(startKeepId = it.keepId, startChoice = it.choice) }) }
            } catch (e: OnlineException) {
                set { copy(merge = null) }
                flash(e.error.toUiText())
            }
        }
    }

    fun closeMerge() = set { copy(merge = null) }

    /** The asset to keep; each differing field starts with its value (or the first other asset's when empty). */
    fun setMergeKeep(id: String) = set {
        val m = merge ?: return@set this
        val keep = m.assets.firstOrNull { it.id == id } ?: return@set this
        val defaults = useCases.merge.defaults(keep, m.assets)
        val choice = defaults.mapNotNull { (f, v) -> m.assets.firstOrNull { it.values[f] == v }?.let { f to it.id } }.toMap()
        copy(merge = m.copy(keepId = id, choice = choice))
    }

    fun chooseMergeValue(fieldId: String, assetId: String) = set { copy(merge = merge?.let { it.copy(choice = it.choice + (fieldId to assetId)) }) }

    fun merge() {
        val m = state.merge ?: return
        val keep = m.keepId ?: return
        set { copy(merge = m.copy(saving = true)) }
        viewModelScope.launch {
            try {
                useCases.merge(p, keep, m.ids, m.values())
                set { copy(merge = null, panel = null) }
                flash(UiText.Plural(R.plurals.rev_merged, m.ids.size))
                refreshAll()
            } catch (e: OnlineException) {
                set { copy(merge = merge?.copy(saving = false)) }
                flash(e.error.toUiText())
            }
        }
    }

    companion object {
        const val POLL_MS = 1500L
        const val SEARCH_MS = 250L
        const val FRESH_MS = 1600L
    }
}
