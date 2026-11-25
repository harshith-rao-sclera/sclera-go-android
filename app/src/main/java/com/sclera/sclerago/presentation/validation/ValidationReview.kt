package com.sclera.sclerago.presentation.validation

import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.ToastEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.LoadFailedNote
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.PageBar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.sclera.sclerago.domain.validation.RunSetup
import com.sclera.sclerago.domain.validation.IssueSeverity
import androidx.compose.ui.platform.testTag

/**
 * One job's results (Review), as the iOS app's: its head and summary, the fields to fix, how the review is going, the
 * steps every asset goes through (select one to list the assets that didn't pass it), and every asset by outcome. Tap an asset to fix its issues; long-press to tick assets and decide on
 * them together (Validated / Recollect; the server no longer leaves assets out of a review).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun JobReview(
    propertyId: String,
    jobId: String,
    steps: Map<String, ValStep>,
    assets: List<Asset>,
    /** The property's name, shown with the asset's place. */
    propName: String,
    onOpenAsset: (String) -> Unit,
    onBack: () -> Unit,
    onRunAgain: (ValRun) -> Unit,
    /** An asset whose result opens as the run shows ("This asset's result" from an asset); [onAssetOpened] once it has. */
    openAssetId: String? = null,
    onAssetOpened: () -> Unit = {},
    vm: RunReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId, jobId) { vm.bind(propertyId, jobId) }
    LaunchedEffect(jobId, openAssetId) {
        openAssetId?.let {
            vm.openAsset(it)
            onAssetOpened()
        }
    }
    val s = vm.state
    // "Run setup", opened from the head's Setup.
    var setupOpen by rememberSaveable(jobId) { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> vm.saveExport(uri?.toString()) }
    LaunchedEffect(s.export) { s.export?.let { saver.launch(it.fileName) } }
    val j = s.run.takeIf { s.jobId == jobId }
    val localById = remember(assets) { assets.associateBy { it.id } }
    Box(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }) {
        Column(Modifier.fillMaxSize()) {
            PageBar(stringResource(R.string.rev_title), null, onBack = onBack, backLabel = stringResource(R.string.rev_back))
            if (j == null) {
                if (s.runFailed) EmptyNote(stringResource(R.string.valt_job_load_failed)) else ReviewSkeleton()
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = 150.dp)) {
                item(key = "head") { ReviewHead(j) { setupOpen = true } }
                // Apply fixes, above the first card (Export CSV and Run again float in the corner).
                item(key = "actions") { ReviewActions(j, s.confidentCount, s.checkBlock, s.busy, vm::applyConfident) }
                item(key = "summary") { ReviewSummary(j) }
                s.insights?.takeIf { j.status.finished }?.let { ins ->
                    if (ins.hasContent) item(key = "fields") { FieldsToFix(ins) }
                    item(key = "progress") { ReviewProgress(ins) }
                }
                if (j.steps.isNotEmpty()) item(key = "steps") { ReviewSteps(j, steps, s.filter.step, vm::setStep) }
                item(key = "assets-head") {
                    ReviewAssetsHead(j, s.filter.outcome) { o -> if (o == null) s.filter.outcome?.let(vm::setOutcome) else vm.setOutcome(o) }
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        SearchField(s.filter.query, vm::setQuery, stringResource(R.string.rev_search_assets))
                    }
                }
                // A new filter or search: placeholders over the old filter's rows until the new ones come.
                if (s.filtering) item(key = "filtering") {
                    SkeletonList(stringResource(R.string.val_loading), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), count = 4, trailing = true)
                } else if (s.results.isEmpty()) item(key = "none") {
                    when {
                        s.loadingResults -> SkeletonList(stringResource(R.string.val_loading), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), count = 4, trailing = true)
                        s.resultsFailed -> LoadFailedNote(stringResource(R.string.rev_results_failed), vm::retryResults)
                        else -> EmptyNote(stringResource(if (j.status.active && j.processed == 0) R.string.valt_checking_first else R.string.rev_no_assets))
                    }
                }
                if (!s.filtering) items(s.results, key = { it.assetId }) { res ->
                    ReviewAssetCard(
                        j, res, localById[res.assetId], selected = res.assetId in s.selected, fresh = res.assetId in s.fresh,
                        onOpen = { if (s.selected.isNotEmpty()) vm.toggleSelected(res.assetId) else vm.openAsset(res.assetId) },
                        // Ticking is for deciding on several together, which is the reviewer's (Validate): as the web's
                        // check boxes, there is none for anyone else.
                        onSelect = if (s.validateBlock?.lacksPermission == true) null else ({ vm.select(res.assetId) }),
                    )
                }
                // Load more: placeholder rows where the next page will go, under the ones shown.
                if (s.loadingMore && !s.filtering) item(key = "more-loading") {
                    SkeletonList(stringResource(R.string.val_loading), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), count = 2, trailing = true)
                }
                if (s.results.isNotEmpty() && !s.filtering) item(key = "more") {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            stringResource(if (s.nextCursor != null) R.string.rev_showing else R.string.rev_showing_all, s.results.size),
                            11, 400, C.Dim, Modifier.weight(1f),
                            align = if (s.nextCursor == null) TextAlign.Center else null,
                        )
                        if (s.nextCursor != null && !s.loadingMore) Pill(stringResource(R.string.rev_load_more), vm::loadMore, height = 36.dp, hPad = 16.dp, size = 12)
                    }
                }
            }
            if (j != null && s.selected.isNotEmpty()) SelectionBar(s, vm)
        }
        // Export CSV and Run again, floating in the corner (not while assets are ticked: the selection bar is there).
        if (j != null && s.selected.isEmpty()) {
            ReviewFabs(j, s.checkBlock, s.busy, vm::export, { onRunAgain(j) }, Modifier.align(Alignment.BottomEnd).navigationBarsPadding())
        }

        j?.let { run -> s.panel?.let { p -> RunAssetSheet(run, p, steps, assets, propName, s, vm, onOpenAsset) } }
        s.merge?.let { MergeSheet(it, s, vm) }
        s.recollectFor?.let { ids -> RecollectSheet(ids.size, s, vm) }
        j?.setup?.takeIf { setupOpen }?.let { RunSetupSheet(it) { setupOpen = false } }
        ToastEffect(s.message)
    }
}

/** "Took 3 min" once a job has finished; null before. */
@Composable
internal fun took(j: ValRun): String? =
    if (j.status.active || j.finishedAt == null) null else elapsedSeconds(j.startedAt ?: j.createdAt, j.finishedAt)?.let { duration(it) }?.ifBlank { null }

/** While running, the time left from the pace so far; once finished, how long it took (the web's overview line). */
@Composable
internal fun timeNote(j: ValRun): String {
    val elapsed = elapsedSeconds(j.startedAt ?: j.createdAt, j.finishedAt)
    if (!j.status.active) return took(j)?.let { stringResource(R.string.rev_took, it) }.orEmpty()
    val rate = if (j.processed > 0 && elapsed != null && elapsed > 0) j.processed / elapsed else 0.0
    return if (rate > 0) stringResource(R.string.rev_time_left, duration((j.total - j.processed) / rate))
    else stringResource(R.string.rev_estimating)
}

/** Ticked assets (long-press): decide on them together, as the Building info page deletes its ticked locations. */
@Composable
private fun SelectionBar(s: RunReviewUiState, vm: RunReviewViewModel) {
    val ids = s.selected.toList()
    Column(
        Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border2).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(pluralStringResource(R.plurals.valt_n_selected, ids.size, ids.size), 12, 700, modifier = Modifier.weight(1f))
            val allShown = s.results.isNotEmpty() && s.results.all { it.assetId in s.selected }
            if (!allShown) Pill(stringResource(R.string.val_select_shown, s.results.size), { vm.selectAllShown(true) }, height = 40.dp, hPad = 14.dp)
            Pill(stringResource(R.string.pi_clear), vm::clearSelection, height = 40.dp, hPad = 14.dp)
        }
        val picked = s.results.filter { it.assetId in s.selected }
        val checkOnly = picked.any { it.checkOnly }
        // Every one signed off in an earlier run: marking them again changes nothing (the web's "Already validated").
        val allValidated = picked.isNotEmpty() && picked.all { it.alreadyValidated }
        DecisionButtons(
            s.busy, onValidate = { vm.decide(ids, Decision.VALIDATED) }, onRecollect = { vm.askRecollect(ids) },
            validateBlocked = checkOnly || allValidated,
            access = s.validateBlock.reason(),
        )
        when {
            checkOnly -> Txt(stringResource(R.string.rev_submit_first), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
            allValidated -> Txt(stringResource(R.string.rev_already_validated), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
    }
}

@Composable
internal fun DecisionButtons(
    busy: Boolean,
    onValidate: () -> Unit,
    onRecollect: () -> Unit,
    /** Some of the assets aren't submitted, or every one is already validated: marking as validated is off. */
    validateBlocked: Boolean = false,
    /** Why the user can't decide at all (no Validate): both stay, greyed out, and a tap says this ([AccessGate]). */
    access: UiText? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccessGate(access, Modifier.weight(1f)) {
            Pill(
                stringResource(R.string.rev_mark_validated), { if (!busy && !validateBlocked) onValidate() },
                Modifier.fillMaxWidth().alpha(if (validateBlocked && access == null) 0.4f else 1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700,
            )
        }
        AccessGate(access, Modifier.weight(1f)) {
            Pill(stringResource(R.string.rev_decision_recollect), { if (!busy) onRecollect() }, Modifier.fillMaxWidth(), height = 40.dp)
        }
    }
}

/**
 * "Run setup", as the web's: the types the run was limited to, then each step's checks with the fields they looked at
 * ("All fields" when none were chosen) and the severity their problems were reported as, when one was set.
 */
@Composable
private fun RunSetupSheet(setup: RunSetup, onClose: () -> Unit) {
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.rev_setup_title), stringResource(R.string.rev_setup_sub), onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("run_setup"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val types = setup.types + listOfNotNull(stringResource(R.string.rev_setup_no_type).takeIf { setup.unclassified })
            if (types.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(stringResource(R.string.rev_setup_types), 13, 700)
                    Txt(types.joinToString(", "), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
            }
            setup.steps.forEach { step ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(step.name, 13, 700, maxLines = 2)
                    step.checks.forEach { check ->
                        val fields = check.fields?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: stringResource(R.string.rev_setup_all_fields)
                        val severity = when (check.severity) {
                            IssueSeverity.WARNING -> stringResource(R.string.rev_setup_as_warning)
                            IssueSeverity.ERROR -> stringResource(R.string.rev_setup_as_error)
                            else -> null
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Txt(check.name, 12, 600, maxLines = Int.MAX_VALUE)
                            Txt(listOfNotNull(fields, severity).joinToString(" · "), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                        }
                    }
                }
            }
        }
    }
}

/** "What should the field team collect again?" before sending assets back to drafts. */
@Composable
private fun RecollectSheet(count: Int, s: RunReviewUiState, vm: RunReviewViewModel) {
    GuardedClose(s.recollectNote.isNotBlank(), { vm.askRecollect(null) }) { close ->
        BottomSheet(close) {
            SheetHeader(pluralStringResource(R.plurals.valt_recollect_title, count, count), stringResource(R.string.valt_recollect_sub), close)
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledInput(
                    stringResource(R.string.valt_note_optional), s.recollectNote, vm::setRecollectNote,
                    stringResource(R.string.valt_recollect_hint), singleLine = false, minLines = 3,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.val_cancel), close, height = 40.dp, hPad = 18.dp)
                Pill(stringResource(R.string.valt_send_recollect), { if (!s.busy) vm.sendRecollect() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700)
            }
        }
    }
}
