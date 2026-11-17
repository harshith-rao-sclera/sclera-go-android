package com.sclera.sclerago.presentation.validation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.usecase.validation.ValidationUseCases
import com.sclera.sclerago.domain.validation.FieldChoice
import com.sclera.sclerago.domain.validation.RunMode
import com.sclera.sclerago.domain.validation.RunRequest
import com.sclera.sclerago.domain.validation.RunScope
import com.sclera.sclerago.domain.validation.RunSource
import com.sclera.sclerago.domain.validation.StepSeverity
import com.sclera.sclerago.domain.validation.TypeScope
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.ValidationDraft
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Run validation sheet: what to check, which assets, and the assets ticked. */
data class RunSheetState(
    val source: RunSource?,
    val mode: RunMode = RunMode.ALL,
    val chosen: Set<String> = emptySet(),
    /** The Choose assets sheet is open over it. */
    val picking: Boolean = false,
    val busy: Boolean = false,
    /** Opened from Data Collection ([QuickValidate]) rather than the Validation page. */
    val quick: Boolean = false,
)

/** The Default order sheet. */
data class OrderSheetState(val steps: List<String> = emptyList(), val custom: Boolean = false, val loading: Boolean = true, val saving: Boolean = false)

/** The new / edit validation page. */
data class EditorState(
    val id: String?,
    val name: String,
    val description: String,
    val steps: List<String>,
    val stepFields: Map<String, List<String>> = emptyMap(),
    val stepSeverity: Map<String, StepSeverity> = emptyMap(),
    val typeScope: TypeScope? = null,
    val templateKey: String? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** The steps before "Start from" or "Clear all" replaced them, for Undo (as the web's builder). */
    val undo: StepsSnapshot? = null,
) {
    val complete: Boolean get() = name.isNotBlank() && steps.isNotEmpty()

    fun draft() = ValidationDraft(name, description, steps, stepFields, stepSeverity, typeScope, templateKey)
}

/** An editor's steps and their settings, kept so "Start from" and "Clear all" can be undone. */
data class StepsSnapshot(
    val steps: List<String>,
    val stepFields: Map<String, List<String>>,
    val stepSeverity: Map<String, StepSeverity>,
    val templateKey: String?,
)

data class ValidationUiState(
    val propertyId: String = "",
    val loading: Boolean = true,
    /** A pull to refresh is reading the saved validations and runs again. */
    val refreshing: Boolean = false,
    /** The lists are the phone's copy (no connection). */
    val offline: Boolean = false,
    val loadFailed: Boolean = false,
    val steps: List<ValStep> = emptyList(),
    val templates: List<ValTemplate> = emptyList(),
    val catalogLoaded: Boolean = false,
    /** The templates couldn't be read (no connection, or the server refused). */
    val catalogFailed: Boolean = false,
    val saved: List<ValidationDef> = emptyList(),
    val runs: List<ValRun> = emptyList(),
    val canCheck: Boolean = false,
    val canValidate: Boolean = false,
    val runSheet: RunSheetState? = null,
    val templateDetails: String? = null,
    val orderSheet: OrderSheetState? = null,
    val editor: EditorState? = null,
    val fields: List<FieldChoice> = emptyList(),
    val confirmDelete: ValidationDef? = null,
    /** Ready runs being started or discarded. */
    val busyRuns: Set<String> = emptySet(),
    val reviewJobId: String? = null,
    val message: UiText? = null,
    /** A run started from Data Collection: the note for the screen it was started from. */
    val quickStarted: UiText? = null,
) {
    val stepsByKey: Map<String, ValStep> get() = steps.associateBy { it.key }

}

/**
 * The Validation page (side menu → Validation), as the web's workspace: templates, saved validations and recent runs,
 * the Run validation sheet, the Default order sheet and the new / edit validation page. Everything goes to the server;
 * offline the lists show the phone's copy.
 */
@HiltViewModel
class ValidationViewModel @Inject constructor(private val useCases: ValidationUseCases) : ViewModel() {
    var state by mutableStateOf(ValidationUiState())
        private set

    private var poll: Job? = null
    private var messageJob: Job? = null

    private fun set(f: ValidationUiState.() -> ValidationUiState) {
        state = state.f()
    }

    /** Opens the page for [propertyId] (again when it changed). */
    fun bind(propertyId: String) {
        if (propertyId.isNotEmpty() && propertyId == state.propertyId) {
            set { copy(canCheck = useCases.access.canCheck(), canValidate = useCases.access.canValidate()) }
            reload()
            return
        }
        poll?.cancel()
        state = ValidationUiState(propertyId = propertyId, canCheck = useCases.access.canCheck(), canValidate = useCases.access.canValidate())
        if (propertyId.isEmpty()) return
        loadCatalog()
        reload()
    }

    /** Closing the page: every sheet and the run review close with it. */
    fun closeOverlays() = set {
        copy(runSheet = null, templateDetails = null, orderSheet = null, editor = null, confirmDelete = null, reviewJobId = null)
    }

    fun flash(msg: UiText) {
        set { copy(message = msg) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2800)
            set { copy(message = null) }
        }
    }

    private fun fail(e: OnlineException) = flash(e.error.toUiText())

    private fun loadCatalog() {
        val p = state.propertyId
        viewModelScope.launch {
            try {
                val (steps, templates) = useCases.catalog(p)
                set { copy(steps = steps, templates = templates, catalogLoaded = true, catalogFailed = false) }
            } catch (_: OnlineException) {
                // Silent, as on the web: the page still shows the lists; templates need a connection.
                set { copy(catalogFailed = true) }
            }
        }
    }

    /** Pull to refresh: reloads the saved validations and runs. */
    fun refresh() {
        if (state.refreshing || state.propertyId.isEmpty()) return
        set { copy(refreshing = true) }
        reload()
    }

    /** Reloads saved validations and recent runs; keeps polling while a run is queued or running. */
    fun reload() {
        val p = state.propertyId
        if (p.isEmpty()) return
        viewModelScope.launch {
            try {
                val saved = useCases.saved(p)
                val runs = useCases.runs(p)
                set { copy(saved = saved, runs = runs, loading = false, loadFailed = false, offline = false) }
            } catch (e: OnlineException) {
                set { copy(loading = false, loadFailed = saved.isEmpty() && runs.isEmpty()) }
                if (e.error != OnlineError.Offline) flash(UiText.Res(R.string.val_load_failed)) else set { copy(offline = true) }
            } finally {
                set { copy(refreshing = false) }
            }
            if (!state.catalogLoaded) loadCatalog()
            pollWhileActive()
        }
    }

    private fun pollWhileActive() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive && state.runs.any { it.status.active }) {
                delay(POLL_MS)
                if (state.reviewJobId != null) continue
                try {
                    val runs = useCases.runs(state.propertyId)
                    set { copy(runs = runs) }
                } catch (_: OnlineException) {
                    break
                }
            }
        }
    }

    // ---------------------------------------------------------------- Runs

    fun openReview(jobId: String?) {
        set { copy(reviewJobId = jobId) }
        if (jobId == null) reload()
    }

    /** "Run on all assets" on a template card: starts at once. */
    fun runOnAll(source: RunSource) = start(RunRequest(source, RunMode.ALL, emptyList(), start = true), closeSheet = false)

    fun openRunSheet(source: RunSource? = null, pick: Boolean = false, assetIds: Set<String> = emptySet(), quick: Boolean = false) {
        val s = source ?: state.templates.firstOrNull()?.let { RunSource.Template(it.key) }
        val choose = pick || assetIds.isNotEmpty()
        set { copy(runSheet = RunSheetState(s, if (choose) RunMode.CHOSEN else RunMode.ALL, assetIds, picking = pick, quick = quick), templateDetails = null) }
    }

    /** Whether this user may run checks (Validate in Data Collection shows only then). */
    fun mayCheck(): Boolean = useCases.access.canCheck()

    /** The note of a run started from Data Collection has been shown there. */
    fun quickShown() = set { copy(quickStarted = null) }

    fun closeRunSheet() = set { copy(runSheet = null) }
    fun setRunSource(s: RunSource) = set { copy(runSheet = runSheet?.copy(source = s)) }
    fun setRunMode(m: RunMode) = set { copy(runSheet = runSheet?.copy(mode = m)) }

    /** The Choose assets sheet over Run validation. */
    fun openPicker() = set { copy(runSheet = runSheet?.copy(mode = RunMode.CHOSEN, picking = true)) }
    fun closePicker() = set { copy(runSheet = runSheet?.copy(picking = false)) }
    fun toggleChosen(id: String) = set {
        val sh = runSheet ?: return@set this
        copy(runSheet = sh.copy(chosen = if (id in sh.chosen) sh.chosen - id else sh.chosen + id))
    }

    /** Select all / none of [ids]. */
    fun setChosen(ids: Collection<String>, on: Boolean) = set {
        val sh = runSheet ?: return@set this
        copy(runSheet = sh.copy(chosen = if (on) sh.chosen + ids else sh.chosen - ids.toSet()))
    }

    /** Runs the sheet's choice now ([now]) or saves it as ready. */
    fun submitRunSheet(now: Boolean) {
        val sh = state.runSheet ?: return
        val source = sh.source ?: return
        start(RunRequest(source, sh.mode, sh.chosen.toList(), start = now), closeSheet = true)
    }

    private fun start(request: RunRequest, closeSheet: Boolean) {
        val p = state.propertyId
        set { copy(runSheet = runSheet?.copy(busy = true)) }
        viewModelScope.launch {
            try {
                val run = useCases.start(p, request)
                val quick = state.runSheet?.quick == true
                set {
                    copy(
                        runSheet = if (closeSheet) null else runSheet,
                        runs = listOf(run) + runs.filter { it.id != run.id },
                        // From Data Collection the web says where to follow it.
                        quickStarted = if (quick) UiText.Res(if (request.start) R.string.val_quick_started else R.string.val_quick_ready, listOf(run.name)) else null,
                    )
                }
                if (!quick) flash(startedMessage(run, ready = !request.start))
                reload()
            } catch (e: OnlineException) {
                set { copy(runSheet = runSheet?.copy(busy = false)) }
                flash(
                    when {
                        e.error == OnlineError.Invalid && request.mode == RunMode.CHANGED -> UiText.Res(R.string.val_nothing_changed)
                        e.error == OnlineError.Invalid && request.mode == RunMode.CHOSEN -> UiText.Res(R.string.val_chosen_gone)
                        e.error == OnlineError.Invalid -> UiText.Res(R.string.val_no_types_match)
                        else -> e.error.toUiText()
                    },
                )
            }
        }
    }

    /** "Run" on a ready run. */
    fun startReady(run: ValRun) = onReady(run) {
        val started = useCases.startReady(state.propertyId, run.id)
        set { copy(runs = runs.map { if (it.id == run.id) started else it }) }
        flash(startedMessage(started, ready = false))
    }

    /** "Discard" on a ready run. */
    fun discard(run: ValRun) = onReady(run) {
        useCases.discard(state.propertyId, run.id)
        set { copy(runs = runs.filter { it.id != run.id }) }
        flash(UiText.Res(R.string.val_run_discarded, listOf(run.name)))
    }

    private fun onReady(run: ValRun, block: suspend () -> Unit) {
        if (run.id in state.busyRuns) return
        set { copy(busyRuns = busyRuns + run.id) }
        viewModelScope.launch {
            try {
                block()
                reload()
            } catch (e: OnlineException) {
                fail(e)
            } finally {
                set { copy(busyRuns = busyRuns - run.id) }
            }
        }
    }

    private fun startedMessage(run: ValRun, ready: Boolean): UiText {
        val scope = scopeText(run)
        return UiText.Res(if (ready) R.string.val_run_saved_ready else R.string.val_run_started, listOf(run.name, scope))
    }

    // ---------------------------------------------------------------- Templates and order

    fun showTemplate(key: String?) = set { copy(templateDetails = key) }

    fun openOrder() {
        set { copy(orderSheet = OrderSheetState(steps = steps.map { it.key })) }
        viewModelScope.launch {
            try {
                val o = useCases.order(state.propertyId)
                set { copy(orderSheet = orderSheet?.copy(steps = o.steps, custom = o.custom, loading = false)) }
            } catch (e: OnlineException) {
                set { copy(orderSheet = null) }
                fail(e)
            }
        }
    }

    fun closeOrder() = set { copy(orderSheet = null) }

    fun moveOrderStep(index: Int, by: Int) = set {
        val o = orderSheet ?: return@set this
        val j = index + by
        if (index !in o.steps.indices || j !in o.steps.indices) return@set this
        copy(orderSheet = o.copy(steps = o.steps.toMutableList().also { it.add(j, it.removeAt(index)) }))
    }

    fun saveOrder() = orderCall(R.string.val_order_saved) { useCases.saveOrder(state.propertyId, it.steps) }

    fun resetOrder() = orderCall(R.string.val_order_reset) { useCases.resetOrder(state.propertyId) }

    private fun orderCall(done: Int, block: suspend (OrderSheetState) -> Unit) {
        val o = state.orderSheet ?: return
        set { copy(orderSheet = o.copy(saving = true)) }
        viewModelScope.launch {
            try {
                block(o)
                set { copy(orderSheet = null) }
                flash(UiText.Res(done))
                loadCatalog()
            } catch (e: OnlineException) {
                set { copy(orderSheet = orderSheet?.copy(saving = false)) }
                fail(e)
            }
        }
    }

    // ---------------------------------------------------------------- Saved validations

    fun askDelete(v: ValidationDef?) = set { copy(confirmDelete = v) }

    fun delete() {
        val v = state.confirmDelete ?: return
        set { copy(confirmDelete = null) }
        viewModelScope.launch {
            try {
                useCases.delete(state.propertyId, v.id)
                flash(UiText.Res(R.string.val_deleted))
                reload()
            } catch (e: OnlineException) {
                fail(e)
            }
        }
    }

    /** New validation, empty or from [template] (named "{template} (custom)"). */
    fun newValidation(template: ValTemplate? = null) {
        set {
            copy(
                editor = EditorState(
                    id = null,
                    name = template?.name?.let { "$it$CUSTOM_SUFFIX" }.orEmpty(),
                    description = template?.description.orEmpty(),
                    steps = template?.steps.orEmpty(),
                    templateKey = template?.key,
                ),
                templateDetails = null,
            )
        }
        loadFields()
    }

    fun editValidation(v: ValidationDef) {
        set {
            copy(
                editor = EditorState(
                    id = v.id, name = v.name, description = v.description.orEmpty(), steps = v.steps,
                    stepFields = v.stepFields, stepSeverity = v.stepSeverity, typeScope = v.typeScope, templateKey = v.templateKey,
                ),
            )
        }
        loadFields()
    }

    private fun loadFields() {
        if (state.fields.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val f = useCases.fields(state.propertyId)
                set { copy(fields = f) }
            } catch (_: OnlineException) {
                // The step settings then offer "All fields" only.
            }
        }
    }

    private fun edit(markDirty: Boolean = true, f: EditorState.() -> EditorState) = set {
        val e = editor ?: return@set this
        copy(editor = e.f().let { if (markDirty) it.copy(dirty = true) else it })
    }

    fun setEditorName(v: String) = edit { copy(name = v.take(com.sclera.sclerago.domain.usecase.validation.VALIDATION_NAME_MAX)) }
    fun setEditorDescription(v: String) = edit { copy(description = v.take(com.sclera.sclerago.domain.usecase.validation.VALIDATION_DESCRIPTION_MAX)) }
    fun addStep(step: String) = edit { if (step in steps) this else copy(steps = steps + step) }
    fun removeStep(step: String) = edit {
        copy(steps = steps - step, stepFields = stepFields - step, stepSeverity = stepSeverity - step)
    }
    fun moveStep(step: String, by: Int) = edit {
        val i = steps.indexOf(step)
        val j = i + by
        if (i < 0 || j !in steps.indices) this else copy(steps = steps.toMutableList().also { it.add(j, it.removeAt(i)) })
    }

    /** A step's fields: empty = every field. */
    fun setStepFields(step: String, ids: List<String>) = edit { copy(stepFields = if (ids.isEmpty()) stepFields - step else stepFields + (step to ids)) }

    fun setStepSeverity(step: String, s: StepSeverity?) = edit { copy(stepSeverity = if (s == null) stepSeverity - step else stepSeverity + (step to s)) }

    /** "Start from": the template's steps (and its name and description when the user hasn't typed their own). */
    fun startFrom(t: ValTemplate) = edit {
        val untouchedName = name.isBlank() || state.templates.any { name == it.name + CUSTOM_SUFFIX }
        copy(
            undo = snapshot(),
            steps = t.steps,
            stepFields = stepFields.filterKeys { it in t.steps },
            stepSeverity = stepSeverity.filterKeys { it in t.steps },
            name = if (untouchedName) t.name + CUSTOM_SUFFIX else name,
            description = if (untouchedName) t.description else description,
            templateKey = if (id == null) t.key else templateKey,
        )
    }

    fun clearSteps() = edit { copy(undo = snapshot(), steps = emptyList(), stepFields = emptyMap(), stepSeverity = emptyMap()) }

    /** Puts back the steps "Start from" or "Clear all" replaced. */
    fun undoSteps() = edit {
        val u = undo ?: return@edit this
        copy(steps = u.steps, stepFields = u.stepFields, stepSeverity = u.stepSeverity, templateKey = u.templateKey, undo = null)
    }

    private fun EditorState.snapshot() = StepsSnapshot(steps, stepFields, stepSeverity, templateKey)

    /** Leaves the page (the page asks first when something changed). */
    fun closeEditor() = set { copy(editor = null) }

    /** Saves the validation; with [andRun], opens the Run validation sheet on it. */
    fun saveEditor(andRun: Boolean) {
        val e = state.editor ?: return
        if (!e.complete || e.saving) return
        edit(markDirty = false) { copy(saving = true) }
        viewModelScope.launch {
            try {
                val saved = useCases.save(state.propertyId, e.id, e.draft())
                set { copy(editor = null) }
                flash(UiText.Res(if (e.id == null) R.string.val_saved else R.string.val_updated))
                reload()
                if (andRun) openRunSheet(RunSource.Saved(saved.id))
            } catch (ex: OnlineException) {
                edit(markDirty = false) { copy(saving = false) }
                flash(if (ex.error is OnlineError.Conflict) UiText.Res(R.string.val_name_taken, listOf(e.name.trim())) else ex.error.toUiText())
            }
        }
    }

    companion object {
        const val POLL_MS = 2000L
        private const val CUSTOM_SUFFIX = " (custom)"

        /** "All assets (12)", "3 chosen assets"… as the web words a run's scope. */
        fun scopeText(run: ValRun): UiText = when (run.scope) {
            RunScope.ALL -> UiText.Res(R.string.val_scope_all, listOf(run.total))
            RunScope.CHANGED -> UiText.Plural(R.plurals.val_scope_changed, run.total)
            RunScope.TYPES -> UiText.Plural(R.plurals.val_scope_types, run.total)
            RunScope.SELECTED -> UiText.Plural(R.plurals.val_scope_selected, run.total)
        }
    }
}
