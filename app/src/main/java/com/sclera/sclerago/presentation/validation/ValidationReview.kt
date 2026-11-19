package com.sclera.sclerago.presentation.validation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.PageBar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * One job's results (Review), as the iOS app's: its head and summary, the fields to fix, how the review is going, the
 * steps every asset goes through (select one to list the assets that didn't pass it), and every asset by outcome. Tap an asset to fix its issues; long-press to tick assets and decide on
 * them together (Validated / Recollect / Exclude).
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
    vm: RunReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId, jobId) { vm.bind(propertyId, jobId) }
    val s = vm.state
    BackHandler(onBack = onBack)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> vm.saveExport(uri?.toString()) }
    LaunchedEffect(s.export) { s.export?.let { saver.launch(it.fileName) } }
    val j = s.run.takeIf { s.jobId == jobId }
    val localById = remember(assets) { assets.associateBy { it.id } }
    Box(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }) {
        Column(Modifier.fillMaxSize()) {
            PageBar(stringResource(R.string.rev_title), null, onBack = onBack)
            if (j == null) {
                EmptyNote(if (s.runFailed) "This job couldn't be loaded." else stringResource(R.string.val_loading))
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = 24.dp)) {
                item(key = "head") { ReviewHead(j) }
                item(key = "summary") {
                    ReviewSummary(j, s.confidentCount, s.canCheck, s.busy, vm::applyConfident, vm::export) { onRunAgain(j) }
                }
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
                if (s.results.isEmpty()) item(key = "none") {
                    EmptyNote(
                        when {
                            s.loadingResults -> stringResource(R.string.val_loading)
                            j.status.active && j.processed == 0 -> "Checking the first assets… Results show up here as soon as the first batch is done."
                            else -> stringResource(R.string.rev_no_assets)
                        },
                    )
                }
                items(s.results, key = { it.assetId }) { res ->
                    ReviewAssetCard(
                        j, res, localById[res.assetId], selected = res.assetId in s.selected, fresh = res.assetId in s.fresh,
                        onOpen = { if (s.selected.isNotEmpty()) vm.toggleSelected(res.assetId) else vm.openAsset(res.assetId) },
                        onSelect = if (s.canValidate) ({ vm.toggleSelected(res.assetId) }) else null,
                    )
                }
                if (s.results.isNotEmpty()) item(key = "more") {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt(
                            stringResource(if (s.nextCursor != null) R.string.rev_showing else R.string.rev_showing_all, s.results.size),
                            11, 400, C.Muted, Modifier.weight(1f),
                        )
                        if (s.nextCursor != null) Pill(
                            if (s.loadingMore) stringResource(R.string.val_loading) else stringResource(R.string.rev_load_more),
                            vm::loadMore, height = 36.dp, hPad = 16.dp, size = 12,
                        )
                    }
                }
            }
            if (j != null && s.selected.isNotEmpty() && s.canValidate) SelectionBar(s, vm)
        }

        j?.let { run -> s.panel?.let { p -> RunAssetSheet(run, p, steps, assets, propName, s, vm, onOpenAsset) } }
        s.merge?.let { MergeSheet(it, s, vm) }
        s.recollectFor?.let { ids -> RecollectSheet(ids.size, s, vm) }
        AnimatedVisibility(
            s.message != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { s.message?.let { Toast(it.asString()) } }
    }
}

/** "Took 3 min" once a job has finished; null before. */
internal fun took(j: ValRun): String? =
    if (j.status.active || j.finishedAt == null) null else elapsedSeconds(j.startedAt ?: j.createdAt, j.finishedAt)?.let(::duration)?.ifBlank { null }

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
            Txt(plural(ids.size, "asset", "assets") + " selected", 12, 700, modifier = Modifier.weight(1f))
            val allShown = s.results.isNotEmpty() && s.results.all { it.assetId in s.selected }
            if (!allShown) Pill(stringResource(R.string.val_select_shown, s.results.size), { vm.selectAllShown(true) }, height = 40.dp, hPad = 14.dp)
            Pill("Clear", vm::clearSelection, height = 40.dp, hPad = 14.dp)
        }
        val checkOnly = s.results.any { it.assetId in s.selected && it.checkOnly }
        DecisionButtons(
            s.busy, onValidate = { vm.decide(ids, Decision.VALIDATED) }, onRecollect = { vm.askRecollect(ids) },
            onExclude = { vm.decide(ids, Decision.EXCLUDED) }, validateBlocked = checkOnly,
        )
        if (checkOnly) Txt(stringResource(R.string.rev_submit_first), 11, 400, C.Muted, maxLines = 2)
    }
}

@Composable
internal fun DecisionButtons(
    busy: Boolean,
    onValidate: () -> Unit,
    onRecollect: () -> Unit,
    onExclude: () -> Unit,
    /** Some of the assets aren't submitted: marking as validated is off (submit first). */
    validateBlocked: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(
            stringResource(R.string.rev_mark_validated), { if (!busy && !validateBlocked) onValidate() },
            Modifier.weight(1f).alpha(if (validateBlocked) 0.4f else 1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700,
        )
        Pill(stringResource(R.string.rev_decision_recollect), { if (!busy) onRecollect() }, Modifier.weight(1f), height = 40.dp)
        Pill(stringResource(R.string.rev_exclude), { if (!busy) onExclude() }, Modifier.weight(1f), bg = Color(0xFF2A1919), fg = C.Red, height = 40.dp, weight = 700)
    }
}

/** "What should the field team collect again?" before sending assets back to drafts. */
@Composable
private fun RecollectSheet(count: Int, s: RunReviewUiState, vm: RunReviewViewModel) {
    GuardedClose(s.recollectNote.isNotBlank(), { vm.askRecollect(null) }) { close ->
        BottomSheet(close) {
            SheetHeader("Send ${plural(count, "asset", "assets")} back", "They go back to drafts for the field team.", close)
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledInput("Note (optional)", s.recollectNote, vm::setRecollectNote, "What should the field team collect again?", singleLine = false, minLines = 3)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancel", close, height = 40.dp, hPad = 18.dp)
                Pill("Send for recollection", { if (!s.busy) vm.sendRecollect() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700)
            }
        }
    }
}
