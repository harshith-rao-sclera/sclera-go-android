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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.RunAssetResult
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.PageBar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/**
 * One job's results (Review): the summary with how far it got and what it found, filters by outcome and step, and
 * every asset with the steps it didn't pass. Tap an asset to fix its issues; long-press to tick assets and decide on
 * them together (Validated / Recollect / Exclude).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun JobReview(
    propertyId: String,
    jobId: String,
    steps: Map<String, ValStep>,
    assets: List<Asset>,
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
    Box(Modifier.fillMaxSize().background(C.Bg).noRippleClick { }) {
        Column(Modifier.fillMaxSize()) {
            PageBar(
                j?.name ?: "Review",
                j?.let {
                    val took = took(it)?.let { t -> " · " + stringResource(R.string.rev_took, t) }.orEmpty()
                    "${scopeText(it)} · ${plural(it.steps.size, "step", "steps")} · ${relativeTime(it.startedAt ?: it.createdAt ?: 0L)}$took"
                },
                onBack = onBack,
            )
            if (j == null) {
                EmptyNote(if (s.runFailed) "This job couldn't be loaded." else "Loading…")
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = pageGutter(), end = pageGutter(), bottom = 24.dp)) {
                item(key = "summary") { Summary(j, s, vm, onRunAgain) }
                s.insights?.takeIf { j.status.finished && it.hasContent }?.let { ins ->
                    item(key = "insights") {
                        Column(
                            Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
                                .border(1.dp, C.Border, r(14)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Txt("Fields to fix", 13, 700)
                            if (ins.topFields.isEmpty()) Txt("No field-level issues.", 11, 400, C.Muted)
                            ins.topFields.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Txt(f.fieldName, 12, 600, C.Soft, Modifier.weight(1f))
                                    Txt(
                                        listOfNotNull(
                                            "${f.missing} empty".takeIf { f.missing > 0 },
                                            plural(f.issues - f.missing, "wrong value", "wrong values").takeIf { f.issues > f.missing },
                                            if (f.open < f.issues) "${f.open} of ${f.issues} still open" else "${f.issues} open",
                                        ).joinToString(" · "),
                                        11, 400, C.Muted,
                                    )
                                }
                            }
                            Txt("Review progress", 13, 700, modifier = Modifier.padding(top = 4.dp))
                            Txt("Issues: ${ins.open} still open · ${ins.applied} fixed · ${ins.kept} kept as is", 11, 400, C.Muted, maxLines = 2)
                            if (ins.aiSuggested > 0) Txt("${ins.aiApplied} of ${ins.aiSuggested} AI suggestions applied", 11, 400, C.Muted)
                            Txt("Assets: ${ins.validated} validated · ${ins.recollect} sent back · ${ins.pending} to decide", 11, 400, C.Muted, maxLines = 2)
                        }
                    }
                }
                item(key = "filters") { Filters(j, steps, s, vm) }
                if (s.results.isEmpty()) item(key = "none") {
                    EmptyNote(
                        when {
                            s.loadingResults -> "Loading…"
                            j.status.active && j.processed == 0 -> "Checking the first assets… Results show up here as soon as the first batch is done."
                            else -> "No assets here."
                        },
                    )
                }
                items(s.results, key = { it.assetId }) { res ->
                    ResultRow(
                        j, res, steps, selected = res.assetId in s.selected, fresh = res.assetId in s.fresh,
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
                        if (s.nextCursor != null) Pill(if (s.loadingMore) "Loading…" else "Load more", vm::loadMore, height = 36.dp, hPad = 16.dp, size = 12)
                    }
                }
            }
            if (j != null && s.selected.isNotEmpty() && s.canValidate) SelectionBar(s, vm)
        }

        j?.let { run -> s.panel?.let { p -> RunAssetSheet(run, p, steps, assets, s, vm, onOpenAsset) } }
        s.merge?.let { MergeSheet(it, s, vm) }
        s.recollectFor?.let { ids -> RecollectSheet(ids.size, s, vm) }
        AnimatedVisibility(
            s.message != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { s.message?.let { Toast(it.asString()) } }
    }
}

/** How many assets pass, the bar and counts; while running, how far it has got; then what can be done with the job. */
@Composable
private fun Summary(j: ValRun, s: RunReviewUiState, vm: RunReviewViewModel, onRunAgain: (ValRun) -> Unit) {
    Column(
        Modifier.padding(12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(stringResource(R.string.rev_checked_of, j.processed, j.total), 14, 700, modifier = Modifier.weight(1f), maxLines = 2)
            j.score?.let { Txt("Quality ${it.roundToInt()}/100", 11, 700, scoreColor(it)) }
        }
        val percent = if (j.total > 0) (j.processed * 100f / j.total).roundToInt() else 0
        if (j.status.active) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(C.Raised)) {
                Box(Modifier.fillMaxWidth(maxOf(percent, 3) / 100f).fillMaxHeight().background(C.Yellow.copy(alpha = 0.7f)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(
                when {
                    j.status.active -> j.phase?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rev_status_starting)
                    j.status == RunStatus.FAILED -> stringResource(R.string.rev_status_stopped, percent)
                    else -> stringResource(R.string.rev_status_finished)
                },
                11, 600, C.Soft, maxLines = 2,
            )
            Txt(timeNote(j), 11, 400, C.Muted, Modifier.weight(1f), maxLines = 2)
        }
        ResultBar(j.failedCount, j.warningCount, j.passedCount)
        Counts(j.failedCount, j.warningCount, j.passedCount)
        j.statusDetail?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, if (j.status == RunStatus.FAILED) C.Red else C.Muted, maxLines = 5) }
        val confident = s.confidentCount
        val actions = j.status.finished || j.processed > 0
        if (actions) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (j.status.finished && confident > 0 && s.canCheck) {
                Pill(
                    "Apply ${plural(confident, "confident fix", "confident fixes")}", { if (!s.busy) vm.applyConfident() },
                    bg = C.Yellow, fg = C.OnYellow, height = 34.dp, hPad = 14.dp, size = 12, weight = 700,
                )
            }
            if (j.processed > 0) Pill("Export CSV", { if (!s.busy) vm.export() }, height = 34.dp, hPad = 14.dp, size = 12)
            if (j.status.finished && s.canCheck) Pill("Run again", { onRunAgain(j) }, height = 34.dp, hPad = 14.dp, size = 12)
        }
    }
}

/** The outcome chips (as before), the steps the assets didn't pass, and a search. */
@Composable
private fun Filters(j: ValRun, steps: Map<String, ValStep>, s: RunReviewUiState, vm: RunReviewViewModel) {
    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf<Triple<StepOutcome?, String, Int>>(
                Triple(null, "All", j.processed), Triple(StepOutcome.FAILED, "Failed", j.failedCount),
                Triple(StepOutcome.WARNING, "To look at", j.warningCount), Triple(StepOutcome.PASSED, "Passed", j.passedCount),
            ).forEach { (o, label, n) ->
                FilterChip(label, n, s.filter.outcome == o, o?.let { outcomeColor(it) }) {
                    if (o == null) s.filter.outcome?.let(vm::setOutcome) else vm.setOutcome(o)
                }
            }
        }
        if (j.openCount > 0) Txt(pluralStringResource(R.plurals.rev_issues_to_review, j.openCount, j.openCount), 11, 600, C.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            j.steps.forEach { key ->
                val st = j.stepStats[key]
                val problems = (st?.failed ?: 0) + (st?.warning ?: 0)
                FilterChip(stepShort(steps, key), problems, s.filter.step == key, stepStateColor(j.stateOf(key))) { vm.setStep(key) }
            }
        }
        SearchField(s.filter.query, vm::setQuery, "Search assets")
    }
}

@Composable
private fun FilterChip(label: String, n: Int, on: Boolean, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(Capsule).background(if (on) C.YTintBg else C.Surface)
            .border(1.dp, if (on) C.Yellow else C.ChipBd, Capsule).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        dot?.let { Box(Modifier.size(7.dp).clip(CircleShape).background(it)) }
        Txt(label, 11, 600, if (on) C.Yellow else C.Soft)
        Txt(n.toString(), 10, 700, (if (on) C.Yellow else C.Soft).copy(alpha = 0.8f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
    j: ValRun,
    res: RunAssetResult,
    steps: Map<String, ValStep>,
    selected: Boolean,
    fresh: Boolean,
    onOpen: () -> Unit,
    onSelect: (() -> Unit)?,
) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(r(12)).background(if (fresh) C.YTintBg else C.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) C.Yellow else C.Border, r(12))
            .combinedClickable(onClick = onOpen, onLongClick = onSelect).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val c = outcomeColor(res.outcome)
            val icon = when (res.outcome) {
                StepOutcome.FAILED -> Icons.OctagonX
                StepOutcome.WARNING -> Icons.Alert
                else -> Icons.CheckCircle
            }
            LineIcon(if (selected) Icons.Check else icon, 20.dp, if (selected) C.Yellow else c, 2.2f)
            Txt(res.assetName.ifBlank { "Unnamed asset" }, 14, 600, modifier = Modifier.weight(1f))
            res.decision?.let { SmallChip(decisionLabel(it), decisionColor(it), decisionColor(it).copy(alpha = 0.14f)) }
            LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.4f)
        }
        val problems = j.steps.filter { res.outcomeOf(it).problem }
        if (problems.isEmpty()) Txt("Passed every step", 11, 400, C.Muted, Modifier.padding(start = 30.dp))
        problems.forEach { key ->
            val o = res.outcomeOf(key)
            Row(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(outcomeColor(o)))
                Column(Modifier.weight(1f)) {
                    Txt(stepName(steps, key), 12, 600, C.Soft, maxLines = 2)
                    Txt(if (o == StepOutcome.FAILED) "Failed" else "Needs a look", 11, 400, C.Muted)
                }
            }
        }
        if (res.issueCount > 0) Txt(
            (if (res.openCount == 0) "All ${res.issueCount} issues handled" else "${res.openCount} open of ${plural(res.issueCount, "issue", "issues")}") +
                (res.decisionNote?.takeIf { it.isNotBlank() }?.let { " · “$it”" } ?: ""),
            11, 400, C.Muted, Modifier.padding(start = 30.dp), maxLines = 2,
        )
        Row(Modifier.padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(stringResource(R.string.rev_score, res.score), 11, 700, scoreColor(res.score.toDouble()))
            if (res.decision == null) Txt(stringResource(R.string.rev_to_review_chip), 11, 400, C.Muted)
            res.checkedAt?.let { Txt(stringResource(R.string.rev_checked_at, relativeTime(it)), 11, 400, C.Muted) }
        }
    }
}

/** "Took 3 min" once a job has finished; null before. */
private fun took(j: ValRun): String? =
    if (j.status.active || j.finishedAt == null) null else elapsedSeconds(j.startedAt ?: j.createdAt, j.finishedAt)?.let(::duration)?.ifBlank { null }

/** While running, the time left from the pace so far; once finished, how long it took (the web's overview line). */
@Composable
private fun timeNote(j: ValRun): String {
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
        DecisionButtons(s.busy, onValidate = { vm.decide(ids, Decision.VALIDATED) }, onRecollect = { vm.askRecollect(ids) }, onExclude = { vm.decide(ids, Decision.EXCLUDED) })
    }
}

@Composable
internal fun DecisionButtons(busy: Boolean, onValidate: () -> Unit, onRecollect: () -> Unit, onExclude: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Validated", { if (!busy) onValidate() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700)
        Pill("Recollect", { if (!busy) onRecollect() }, Modifier.weight(1f), height = 40.dp)
        Pill("Exclude", { if (!busy) onExclude() }, Modifier.weight(1f), bg = Color(0xFF2A1919), fg = C.Red, height = 40.dp, weight = 700)
    }
}

/** "What should the field team collect again?" before sending assets back to drafts. */
@Composable
private fun RecollectSheet(count: Int, s: RunReviewUiState, vm: RunReviewViewModel) {
    BottomSheet({ vm.askRecollect(null) }) {
        SheetHeader("Send ${plural(count, "asset", "assets")} back", "They go back to drafts for the field team.") { vm.askRecollect(null) }
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledInput("Note (optional)", s.recollectNote, vm::setRecollectNote, "What should the field team collect again?", singleLine = false, minLines = 3)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Cancel", { vm.askRecollect(null) }, height = 40.dp, hPad = 18.dp)
            Pill("Send for recollection", { if (!s.busy) vm.sendRecollect() }, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700)
        }
    }
}
