package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.RunAssetResult
import com.sclera.sclerago.domain.validation.RunInsights
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepState
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.genericAssetIco
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.icons.assetIco
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

// The Review page's sections, as the iOS app's: the run's head, its summary, the fields to fix, how the review is
// going, the steps every asset goes through, and the assets.

/** A section of the Review page: a rounded card with a title, a line under it and its content. */
@Composable
internal fun ReviewCard(
    title: String? = null,
    sub: String? = null,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(r(16)).background(C.Surface)
            .border(1.dp, C.Border, r(16)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(title, 15, 700, modifier = Modifier.weight(1f))
                count?.let { CountBadge(it) }
            }
            sub?.let { Txt(it, 11, 400, C.Soft, maxLines = Int.MAX_VALUE) }
        }
        content()
    }
}

@Composable
private fun CountBadge(n: Int) {
    Box(Modifier.heightIn(min = 22.dp).widthIn(min = 22.dp).clip(CircleShape).background(C.Raised).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Txt(n.toString(), 10, 700, C.Soft)
    }
}

/** The run's name and state, and what it covered: "All 12 assets · 2 steps · Started 1 min ago · Took 3s". */
@Composable
internal fun ReviewHead(j: ValRun) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(j.name, 22, 800, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
            StatusChip(j.status)
        }
        val took = took(j)?.let { stringResource(R.string.rev_took, it) }
        Txt(
            listOfNotNull(
                scopeText(j),
                pluralStringResource(R.plurals.rev_steps, j.steps.size, j.steps.size),
                relativeTime(j.startedAt ?: j.createdAt ?: 0L).let { at ->
                    // Who started it, as the web's "by …" (runs started by others are listed too).
                    j.startedBy?.let { stringResource(R.string.rev_started_by, at, it) } ?: stringResource(R.string.rev_started, at)
                },
                took,
            ).joinToString(" · "),
            11, 500, C.Muted, maxLines = Int.MAX_VALUE,
        )
    }
}

/** How far the run got, its data quality, and how many assets failed, need a look or passed; then what can be done. */
@Composable
internal fun ReviewSummary(j: ValRun, confident: Int, canCheck: Boolean, busy: Boolean, onApplyConfident: () -> Unit, onExport: () -> Unit, onRunAgain: () -> Unit) {
    ReviewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            j.score?.let { score ->
                val c = scoreColor(score)
                Row(
                    Modifier.clip(r(8)).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Txt(stringResource(R.string.rev_quality), 11, 600, c)
                    Txt(score.roundToInt().toString(), 12, 800, c)
                    Txt(stringResource(R.string.rev_quality_of), 10, 600, c.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LineIcon(Icons.Menu, 13.dp, C.Yellow, 2.4f)
                Txt(pluralStringResource(R.plurals.rev_steps, j.steps.size, j.steps.size), 11, 700, C.Yellow)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Txt(j.processed.toString(), 20, 800)
                Txt(stringResource(R.string.rev_of_assets_checked, j.total), 14, 700, modifier = Modifier.padding(bottom = 2.dp))
            }
            val percent = if (j.total > 0) (j.processed * 100f / j.total).roundToInt() else 0
            Txt(
                when {
                    j.status.active -> listOf(j.phase?.takeIf { it.isNotBlank() } ?: stringResource(R.string.rev_status_starting), timeNote(j)).filter { it.isNotBlank() }.joinToString(" · ")
                    j.status == RunStatus.FAILED -> stringResource(R.string.rev_status_stopped, percent)
                    else -> stringResource(R.string.rev_status_finished)
                },
                11, 500, C.Muted, maxLines = Int.MAX_VALUE,
            )
        }
        if (j.status.active) JobProgress(j) else ResultBar(j.failedCount, j.warningCount, j.passedCount)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(j.failedCount, stringResource(R.string.rev_stat_failed), dot = C.Red)
            StatTile(j.warningCount, stringResource(R.string.rev_stat_look), dot = C.Orange)
            StatTile(j.passedCount, stringResource(R.string.rev_stat_passed), dot = C.Green)
        }
        j.statusDetail?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, if (j.status == RunStatus.FAILED) C.Red else C.Muted, maxLines = Int.MAX_VALUE) }
        if (j.status.finished || j.processed > 0) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (j.status.finished && confident > 0 && canCheck) {
                Pill(
                    pluralStringResource(R.plurals.rev_apply_confident, confident, confident), { if (!busy) onApplyConfident() },
                    bg = C.Yellow, fg = C.OnYellow, height = 34.dp, hPad = 14.dp, size = 12, weight = 700,
                )
            }
            if (j.processed > 0) Pill(stringResource(R.string.rev_export_csv), { if (!busy) onExport() }, height = 34.dp, hPad = 14.dp, size = 12)
            if (j.status.finished && canCheck) Pill(stringResource(R.string.rev_run_again), onRunAgain, height = 34.dp, hPad = 14.dp, size = 12)
        }
    }
}

/**
 * A number in a tile, with its label under it: a coloured dot before the label ([dot]), or a short bar above the
 * number when it is the one to act on ([mark]).
 */
@Composable
private fun RowScope.StatTile(n: Int, label: String, dot: Color? = null, mark: Color? = null, numberColor: Color = C.Text) {
    Column(
        Modifier.weight(1f).clip(r(12)).background(C.Raised).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(Capsule).background(mark ?: Color.Transparent))
        Txt(n.toString(), 18, 800, numberColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dot?.let { Dot(it, 7.dp) }
            Txt(label, 11, 500, C.Soft, maxLines = 1)
        }
    }
}

/** The fields with issues, most first, each with how many issues are still open there. */
@Composable
internal fun FieldsToFix(ins: RunInsights) {
    ReviewCard(stringResource(R.string.rev_fields_title), stringResource(R.string.rev_fields_sub), count = ins.topFields.size) {
        if (ins.topFields.isEmpty()) Txt(stringResource(R.string.rev_fields_none), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        ins.topFields.forEach { f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(f.fieldName, 13, 600, modifier = Modifier.weight(1f), maxLines = 2)
                val n = if (f.open > 0) f.open else f.issues
                val c = if (f.open > 0) C.Orange else C.Muted
                Box(Modifier.clip(Capsule).background(c.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Txt(pluralStringResource(R.plurals.rev_n_issues, n, n), 11, 700, c)
                }
            }
        }
    }
}

/** What has been done about the issues, and decided about the assets. */
@Composable
internal fun ReviewProgress(ins: RunInsights) {
    ReviewCard(stringResource(R.string.rev_progress_title), stringResource(R.string.rev_progress_sub)) {
        SmallHeading(stringResource(R.string.rev_issues_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(ins.open, stringResource(R.string.rev_still_open), mark = C.Orange.takeIf { ins.open > 0 }, numberColor = if (ins.open > 0) C.Orange else C.Text)
            StatTile(ins.applied, stringResource(R.string.rev_fixed))
            StatTile(ins.kept, stringResource(R.string.rev_kept))
        }
        if (ins.aiSuggested > 0) Txt(stringResource(R.string.rev_ai_applied, ins.aiApplied, ins.aiSuggested), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        SmallHeading(stringResource(R.string.rev_assets_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(ins.validated, stringResource(R.string.rev_decision_validated))
            StatTile(ins.recollect, stringResource(R.string.rev_sent_back))
            StatTile(ins.pending, stringResource(R.string.rev_to_decide), mark = C.Yellow.takeIf { ins.pending > 0 }, numberColor = if (ins.pending > 0) C.Yellow else C.Text)
        }
    }
}

@Composable
private fun SmallHeading(text: String) = Txt(text.uppercase(), 10, 700, C.Soft, spacing = 0.8f)

/**
 * The steps every asset goes through, in order: each with how it went. Selecting one lists the assets that didn't
 * pass it (again: all of them).
 */
@Composable
internal fun ReviewSteps(j: ValRun, steps: Map<String, ValStep>, selected: String?, onSelect: (String) -> Unit) {
    ReviewCard(stringResource(R.string.rev_steps_title), stringResource(R.string.rev_steps_sub)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Top) {
            j.steps.forEachIndexed { i, key ->
                if (i > 0) Box(Modifier.padding(top = 18.dp).width(18.dp).height(1.5.dp).background(C.Border2))
                StepNode(i, key, j, steps, on = selected == key) { onSelect(key) }
            }
        }
    }
}

@Composable
private fun StepNode(i: Int, key: String, j: ValRun, steps: Map<String, ValStep>, on: Boolean, onClick: () -> Unit) {
    val state = j.stateOf(key)
    val c = stepStateColor(state)
    val st = j.stepStats[key]
    Column(
        Modifier.widthIn(min = 84.dp).clip(r(12)).background(if (on) C.YTintBg else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(c.copy(alpha = 0.16f)).border(1.5.dp, if (on) C.Yellow else c, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                StepState.FAILED -> LineIcon(Icons.Close, 15.dp, c, 2.8f)
                StepState.PASSED -> LineIcon(Icons.Check, 15.dp, c, 2.8f)
                StepState.WARNING -> Txt("!", 15, 800, c)
                StepState.SKIPPED -> LineIcon(Icons.Minus, 15.dp, c, 2.8f)
                StepState.PENDING -> Txt("${i + 1}", 12, 700, c)
            }
        }
        Txt(stringResource(R.string.valt_step_numbered, i + 1, stepShort(steps, key)), 11, 700, if (on) C.Yellow else C.Text, maxLines = 2, align = TextAlign.Center)
        Txt(
            when (state) {
                StepState.FAILED -> stringResource(R.string.rev_step_failed, st?.failed ?: 0)
                StepState.WARNING -> stringResource(R.string.rev_step_look, st?.warning ?: 0)
                StepState.PASSED -> stringResource(R.string.rev_step_passed, st?.passed ?: 0)
                StepState.SKIPPED -> stringResource(R.string.rev_step_na)
                StepState.PENDING -> stringResource(R.string.rev_step_waiting)
            },
            10, 500, C.Muted, maxLines = 1, align = TextAlign.Center,
        )
    }
}

/** "Assets", what to do with them, and the chips that show all of them or those of one outcome. */
@Composable
internal fun ReviewAssetsHead(j: ValRun, outcome: StepOutcome?, onOutcome: (StepOutcome?) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(stringResource(R.string.rev_assets_label), 15, 700)
        Txt(stringResource(R.string.rev_assets_sub), 11, 400, C.Soft, maxLines = Int.MAX_VALUE)
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf<Triple<StepOutcome?, String, Int>>(
            Triple(null, stringResource(R.string.rev_filter_all), j.processed),
            Triple(StepOutcome.FAILED, stringResource(R.string.rev_stat_failed), j.failedCount),
            Triple(StepOutcome.WARNING, stringResource(R.string.rev_stat_look), j.warningCount),
            Triple(StepOutcome.PASSED, stringResource(R.string.rev_stat_passed), j.passedCount),
        ).forEach { (o, label, n) -> OutcomeChip(label, n, outcome == o, o?.let { outcomeColor(it) }) { onOutcome(o) } }
    }
}

@Composable
private fun OutcomeChip(label: String, n: Int, on: Boolean, dot: Color?, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(Capsule).background(if (on) C.YTintBg else C.Surface)
            .border(1.dp, if (on) C.Yellow else C.ChipBd, Capsule).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dot?.let { Dot(it, 7.dp) }
        Txt(label, 12, 700, if (on) C.Yellow else C.Text)
        Txt(n.toString(), 11, 700, if (on) C.Yellow else C.Soft)
    }
}

/**
 * One asset of the run: its type's icon, name and outcome, its score, a square per step (how it went there) and how
 * many of its issues are still open. Long-press ticks it (reviewers) to decide on several together.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReviewAssetCard(
    j: ValRun,
    res: RunAssetResult,
    local: Asset?,
    selected: Boolean,
    fresh: Boolean,
    onOpen: () -> Unit,
    onSelect: (() -> Unit)?,
) {
    val c = outcomeColor(res.outcome)
    Column(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(r(14)).background(if (fresh) C.YTintBg else C.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) C.Yellow else C.Border, r(14))
            .combinedClickable(onClick = onOpen, onLongClick = onSelect).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(r(10)).background(if (selected) C.Yellow else C.Raised), contentAlignment = Alignment.Center) {
                if (selected) LineIcon(Icons.Check, 20.dp, C.OnYellow, 3f)
                else LineIcon(local?.let { assetIco(it) } ?: genericAssetIco(), 19.dp, C.Yellow, 1.9f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(res.assetName.ifBlank { stringResource(R.string.val_unnamed) }, 14, 700, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (res.outcome != StepOutcome.SKIPPED) SmallChip(outcomeLabel(res.outcome), c, c.copy(alpha = 0.16f))
                    res.decision?.let { SmallChip(decisionLabel(it), decisionColor(it), decisionColor(it).copy(alpha = 0.16f)) }
                        ?: res.assetStatus?.takeIf { res.checkOnly }?.let { st ->
                            val label = stringResource(if (st == Status.EXCEPTION) R.string.rev_check_only_exception else R.string.rev_check_only_draft)
                            SmallChip(label, C.Muted, C.Raised)
                        }
                }
            }
            val sc = scoreColor(res.score.toDouble())
            Box(Modifier.widthIn(min = 34.dp).clip(r(8)).background(sc.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Txt(res.score.toString(), 12, 800, sc)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            j.steps.forEach { key -> Box(Modifier.size(10.dp).clip(r(3)).background(outcomeColor(res.outcomeOf(key)))) }
            Spacer(Modifier.weight(1f))
            if (res.issueCount > 0) Txt(
                if (res.openCount == 0) stringResource(R.string.rev_handled_all, res.issueCount)
                else stringResource(R.string.rev_open_of, res.openCount, res.issueCount),
                11, 500, C.Muted,
            )
            LineIcon(Icons.ChevronRight, 14.dp, C.Muted, 2.4f)
        }
    }
}
