package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.isLockedFor
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.IssueSuggestion
import com.sclera.sclerago.domain.validation.IssueTarget
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/**
 * One asset of the run: its decision, then each step with the issues found there. Open issues offer their suggested
 * values (Apply), a typed value, merging duplicates, or keeping the asset as it is; handled ones can be undone.
 */
@Composable
internal fun RunAssetSheet(
    run: ValRun,
    p: AssetPanelState,
    steps: Map<String, ValStep>,
    assets: List<Asset>,
    s: RunReviewUiState,
    vm: RunReviewViewModel,
    onOpenAsset: (String) -> Unit,
) {
    val res = s.panelResult
    val close = { vm.openAsset(null) }
    val local = assets.firstOrNull { it.id == p.assetId }
    val locked = local != null && isLockedFor(local.st, isAdmin = s.canValidate)
    BottomSheet(close, maxHeightFraction = 0.95f) {
        val name = res?.assetName?.takeIf { it.isNotBlank() } ?: local?.name ?: stringResource(R.string.val_unnamed)
        val first = res?.let { r -> run.steps.indexOfFirst { r.outcomeOf(it).problem } }?.takeIf { it >= 0 }
        SheetHeader(
            name,
            when {
                res == null -> null
                first != null -> stringResource(R.string.rev_first_problem, first + 1, stepShort(steps, run.steps[first]))
                else -> stringResource(R.string.rev_passed_every)
            },
            close,
        )
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                res?.let { SmallChip(outcomeLabel(it.outcome), outcomeColor(it.outcome), outcomeColor(it.outcome).copy(alpha = 0.14f)) }
                res?.let { Txt(stringResource(R.string.rev_score, it.score), 11, 600, scoreColor(it.score.toDouble())) }
                local?.where?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, C.Muted, Modifier.weight(1f), maxLines = 2) }
            }
            if (local != null) {
                TextAction(stringResource(R.string.rev_open_asset), { onOpenAsset(local.id) }, icon = Icons.ChevronRight)
            } else {
                Txt(stringResource(R.string.rev_asset_not_here), 11, 400, C.Muted, maxLines = 3)
            }
            if (res != null) DecisionBox(res.decision, res.decisionNote, s, p, vm)

            if (p.loading) Txt(stringResource(R.string.val_loading), 12, 400, C.Muted)
            run.steps.forEachIndexed { i, key ->
                val issues = p.issues.filter { it.step == key }
                StepBlock(i, key, steps, res?.outcomeOf(key) ?: StepOutcome.SKIPPED, issues, p, s, vm, locked)
            }
            // Issues of a step the run no longer lists (e.g. a renamed check) still show.
            p.issues.filter { it.step !in run.steps }.forEach { IssueRow(it, p, s, vm, locked) }
        }
    }
}

@Composable
private fun DecisionBox(decision: Decision?, note: String?, s: RunReviewUiState, p: AssetPanelState, vm: RunReviewViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (decision != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip(decisionLabel(decision), decisionColor(decision), decisionColor(decision).copy(alpha = 0.14f))
                note?.takeIf { it.isNotBlank() }?.let { Txt("“$it”", 12, 400, C.Soft, Modifier.weight(1f), maxLines = 3) }
            }
            if (s.canValidate) TextAction(stringResource(R.string.rev_undo_decision), { vm.decide(listOf(p.assetId), Decision.CLEAR) }, color = C.Soft, enabled = !s.busy, icon = Icons.Refresh)
        } else if (!s.canValidate) {
            Txt(stringResource(R.string.rev_reviewer_signs_off), 12, 400, C.Muted, maxLines = 3)
        } else {
            Txt(stringResource(R.string.rev_to_review_chip), 12, 600, C.Muted)
            DecisionButtons(
                s.busy,
                onValidate = { vm.decide(listOf(p.assetId), Decision.VALIDATED) },
                onRecollect = { vm.askRecollect(listOf(p.assetId)) },
                onExclude = { vm.decide(listOf(p.assetId), Decision.EXCLUDED) },
            )
        }
    }
}

@Composable
private fun StepBlock(
    index: Int,
    key: String,
    steps: Map<String, ValStep>,
    outcome: StepOutcome,
    issues: List<RunIssue>,
    p: AssetPanelState,
    s: RunReviewUiState,
    vm: RunReviewViewModel,
    locked: Boolean,
) {
    val open = issues.count { it.status == IssueStatus.OPEN }
    Column(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineIcon(outcomeIcon(outcome), 16.dp, outcomeColor(outcome), 2.2f)
            Txt("${index + 1}. ${stepName(steps, key)}", 13, 700, modifier = Modifier.weight(1f))
            Txt(
                outcomeLabel(outcome) + if (issues.isEmpty()) "" else " · " +
                    if (open > 0) stringResource(R.string.rev_n_open, open) else stringResource(R.string.rev_all_handled),
                11, 500, C.Muted,
            )
        }
        if (issues.isEmpty() && (outcome == StepOutcome.PASSED || outcome == StepOutcome.SKIPPED)) {
            Txt(steps[key]?.description?.takeIf { outcome == StepOutcome.PASSED } ?: stringResource(R.string.rev_step_not_applicable), 11, 400, C.Muted, maxLines = 3)
        }
        issues.forEach { IssueRow(it, p, s, vm, locked) }
    }
}

@Composable
private fun IssueRow(issue: RunIssue, p: AssetPanelState, s: RunReviewUiState, vm: RunReviewViewModel, locked: Boolean) {
    val busy = issue.id in p.busy
    val sevColor = when (issue.severity) {
        IssueSeverity.ERROR -> C.Red
        IssueSeverity.WARNING -> C.Orange
        IssueSeverity.INFO -> C.Blue
    }
    Column(
        Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.dp, if (issue.status == IssueStatus.OPEN) sevColor.copy(alpha = 0.5f) else C.Border, r(10))
            .padding(10.dp).alpha(if (busy) 0.6f else 1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(sevColor, 7.dp)
            Txt(listOfNotNull(issue.fieldName, issue.ruleName.takeIf { it.isNotBlank() }).joinToString(" · "), 11, 700, C.Soft, Modifier.weight(1f), maxLines = 2)
            if (issue.suggestions.any { it.sourceUrl != null }) SmallChip(stringResource(R.string.rev_from_web), C.Blue, C.Blue.copy(alpha = 0.14f))
        }
        Txt(issue.message, 12, 400, C.Text, maxLines = 6)
        if (issue.status == IssueStatus.OPEN) OpenIssue(issue, p, s, vm, busy, locked) else ClosedIssue(issue, s, vm, busy)
    }
}

@Composable
private fun OpenIssue(issue: RunIssue, p: AssetPanelState, s: RunReviewUiState, vm: RunReviewViewModel, busy: Boolean, locked: Boolean) {
    if (!s.canCheck) return
    if (issue.canApply) {
        // A locked asset still shows what is suggested, without Apply (as the web).
        issue.suggestions.forEachIndexed { i, sug ->
            SuggestionRow(sug, best = i == 0, enabled = !busy, onApply = if (locked) null else ({ vm.applySuggestion(issue, sug.value) }))
        }
    }
    if (locked) {
        Txt(stringResource(R.string.rev_locked_note), 11, 400, C.Muted, maxLines = 4)
        return
    }
    if (issue.canType) {
        val typed = p.typed[issue.id].orEmpty()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(
                typed, { vm.setTyped(issue.id, it) },
                stringResource(if (issue.suggestions.isEmpty()) R.string.rev_type_value else R.string.rev_or_type),
                Modifier.weight(1f).heightIn(min = 44.dp).clip(r(10)).background(C.Surface).border(1.dp, C.Border2, r(10))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                size = 13,
            )
            Pill(stringResource(R.string.val_save), { if (!busy && typed.isNotBlank()) vm.applyTyped(issue) }, Modifier.alpha(if (typed.isNotBlank()) 1f else 0.4f), height = 44.dp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (issue.related.isNotEmpty() && s.canValidate) TextAction(stringResource(R.string.rev_merge), { vm.openMerge(issue) }, enabled = !busy, icon = Icons.Copy)
        TextAction(
            stringResource(
                when {
                    issue.related.isNotEmpty() -> R.string.rev_not_duplicate
                    issue.likeField -> R.string.rev_keep_value
                    issue.target == IssueTarget.CLASSIFICATION -> R.string.rev_keep_as_is
                    else -> R.string.rev_dismiss
                },
            ),
            { vm.dismiss(issue) }, color = C.Soft, enabled = !busy,
        )
    }
}

@Composable
private fun SuggestionRow(sug: IssueSuggestion, best: Boolean, enabled: Boolean, onApply: (() -> Unit)?) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().clip(r(10)).background(C.Surface).padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(sug.shown.ifBlank { "—" }, 13, 700, modifier = Modifier.weight(1f, fill = false), maxLines = 3)
                if (best) Txt(stringResource(R.string.rev_best_match), 10, 600, C.Yellow)
            }
            val pct = (sug.confidence * 100).roundToInt()
            val confColor = when {
                sug.confidence >= 0.8 -> C.Green
                sug.confidence >= 0.5 -> C.Orange
                else -> C.Red
            }
            Txt(stringResource(R.string.rev_sure, pct), 10, 600, confColor)
            sug.reason?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, C.Muted, maxLines = 4) }
            sug.sourceUrl?.let { url ->
                val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
                Txt(
                    host, 11, 600, C.Blue,
                    Modifier.clip(r(6)).clickable(role = Role.Button) { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp),
                )
            }
        }
        onApply?.let { apply -> Pill(stringResource(R.string.rev_apply), { if (enabled) apply() }, bg = C.Yellow, fg = C.OnYellow, height = 40.dp, weight = 700) }
    }
}

@Composable
private fun ClosedIssue(issue: RunIssue, s: RunReviewUiState, vm: RunReviewViewModel, busy: Boolean) {
    val shown = issue.resolvedValue?.text?.takeIf { it.isNotBlank() }
        ?: issue.suggestions.firstOrNull { it.value.json == issue.resolvedValue?.json }?.label
    Txt(
        when (issue.status) {
            IssueStatus.DISMISSED -> stringResource(R.string.rev_kept)
            IssueStatus.RESOLVED -> stringResource(R.string.rev_fixed)
            else -> stringResource(R.string.rev_set_to, shown ?: "—")
        },
        12, 600, if (issue.status == IssueStatus.DISMISSED) C.Muted else C.Green,
    )
    if (s.canValidate && (issue.status == IssueStatus.ACCEPTED || issue.status == IssueStatus.DISMISSED)) {
        val label = when {
            issue.status == IssueStatus.DISMISSED -> stringResource(R.string.rev_reopen)
            issue.target == IssueTarget.CLASSIFICATION ->
                stringResource(if (issue.currentValue?.json?.contains("null") != false) R.string.rev_undo_unclassified else R.string.rev_undo_type)
            issue.currentValue?.text.isNullOrBlank() -> stringResource(R.string.rev_undo_empty)
            else -> stringResource(R.string.rev_undo_value, issue.currentValue?.text.orEmpty())
        }
        TextAction(label, { vm.revert(issue) }, color = C.Soft, enabled = !busy, icon = Icons.Refresh)
    }
}

// ---------------------------------------------------------------- Merge duplicates

/** Pick the asset to keep and, for each field where they differ, the value it keeps; the others are deleted. */
@Composable
internal fun MergeSheet(m: MergeState, s: RunReviewUiState, vm: RunReviewViewModel) {
    val names = s.fields.associate { it.id to it.name }
    BottomSheet(vm::closeMerge, maxHeightFraction = 0.95f) {
        SheetHeader(stringResource(R.string.rev_merge_title), stringResource(R.string.rev_merge_sub), vm::closeMerge)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (m.loading) Txt(stringResource(R.string.val_loading), 12, 400, C.Muted)
            if (m.assets.isNotEmpty()) Txt(stringResource(R.string.rev_merge_keep), 12, 700, C.Yellow)
            m.assets.forEach { a ->
                OptionCard(a.name.ifBlank { stringResource(R.string.val_unnamed) }, a.place, m.keepId == a.id, { vm.setMergeKeep(a.id) })
            }
            val differing = m.differing
            if (m.assets.isNotEmpty()) {
                Txt(stringResource(R.string.rev_merge_differ), 12, 700, C.Yellow, Modifier.padding(top = 8.dp))
                if (differing.isEmpty()) Txt(stringResource(R.string.rev_merge_same), 12, 400, C.Muted)
            }
            differing.forEach { f ->
                Column(
                    Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Txt(names[f] ?: stringResource(R.string.rev_field), 12, 700, C.Soft)
                    m.assets.forEach { a ->
                        val v = a.values[f]?.text.orEmpty()
                        val on = m.choice[f] == a.id
                        Row(
                            Modifier.fillMaxWidth().clip(r(8)).clickable(role = Role.RadioButton) { vm.chooseMergeValue(f, a.id) }
                                .heightIn(min = 44.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioDot(on)
                            Column(Modifier.weight(1f)) {
                                Txt(v.ifBlank { stringResource(R.string.rev_empty) }, 12, 600, if (v.isBlank()) C.Muted else C.Text, maxLines = 3)
                                Txt(a.name, 10, 400, C.Muted)
                            }
                        }
                    }
                }
            }
        }
        if (m.assets.size > 1) {
            val gone = m.assets.size - 1
            Txt(
                pluralStringResource(R.plurals.rev_merge_deleted, gone, gone), 11, 400, C.Muted,
                Modifier.fillMaxWidth().topBorder(C.Border).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().then(if (m.assets.size > 1) Modifier else Modifier.topBorder(C.Border)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.val_cancel), vm::closeMerge, height = 44.dp)
            YellowButton(stringResource(R.string.rev_merge_button), vm::merge, Modifier.weight(1f), enabled = !m.loading && !m.saving && m.keepId != null)
        }
    }
}
