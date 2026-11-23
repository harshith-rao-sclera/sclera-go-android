package com.sclera.sclerago.presentation.validation

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.Segmented
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

/**
 * One asset of the run, as the iOS app's: its outcome and first problem, the decision (Mark as validated, Recollect,
 * Exclude), where it is and how complete, then two tabs. Checks: each step with the issues found there (apply a
 * suggested or typed value, merge duplicates, or keep it as it is; missing data is filled in on the asset; handled ones
 * can be undone). Asset info: its fields. As the web's: View asset opens it on top, and Revalidate checks it again inside
 * the run.
 */
@Composable
internal fun RunAssetSheet(
    run: ValRun,
    p: AssetPanelState,
    steps: Map<String, ValStep>,
    assets: List<Asset>,
    propName: String,
    s: RunReviewUiState,
    vm: RunReviewViewModel,
    onOpenAsset: (String) -> Unit,
) {
    val res = s.panelResult
    val leave = { vm.openAsset(null) }
    val local = assets.firstOrNull { it.id == p.assetId }
    // As the web: the server's status of the asset first (the phone's copy may be stale, or the asset not on the phone),
    // so a Check-only user isn't offered a fix the server refuses on a submitted or signed-off asset.
    val status = res?.assetStatus ?: local?.st
    val locked = status != null && isLockedFor(status, isAdmin = s.canValidate)
    var tab by rememberSaveable(p.assetId) { mutableIntStateOf(0) }
    GuardedClose(p.typed.values.any { it.isNotBlank() }, leave) { close ->
        // The whole screen, closed only with × (and Back): a swipe or a tap beside it would lose the place in a long list.
        BottomSheet(close, fullHeight = true, closeOnOutside = false) {
            val name = res?.assetName?.takeIf { it.isNotBlank() } ?: local?.name ?: stringResource(R.string.val_unnamed)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(name, 18, 800, modifier = Modifier.weight(1f), maxLines = 2)
                RevalidateButton(run, p.assetId, s, vm)
                CloseButton(close, size = 36.dp, iconSize = 16.dp)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (res != null) {
                    val open = if (p.loading) res.openCount else p.issues.count { it.status == IssueStatus.OPEN }
                    DecisionRow(res.decision, res.decisionNote, open, s, p, vm, checkOnly = res.checkOnly)
                }
                ReviewAssetStrip(name, local, propName, p.info)
                Segmented(
                    listOf(stringResource(R.string.rev_tab_checks), stringResource(R.string.rev_tab_info)), tab, { tab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tab == 1) {
                    ReviewAssetInfoTab(local, p.info, p.infoLoading, onOpenAsset)
                } else {
                    // Missing data is filled in on the asset itself (on this phone), which opens on top of this review.
                    val fillIn = local?.let { a -> { onOpenAsset(a.id) } }
                    run.steps.forEachIndexed { i, key ->
                        val issues = p.issues.filter { it.step == key }
                        StepBlock(i, key, steps, res?.outcomeOf(key) ?: StepOutcome.SKIPPED, issues, p, s, vm, locked, fillIn, loading = p.loading)
                    }
                    // Issues of a step the run no longer lists (e.g. a renamed check) still show.
                    p.issues.filter { it.step !in run.steps }.forEach { IssueRow(it, p, s, vm, locked, fillIn) }
                }
            }
        }
    }
}

/**
 * Revalidate, beside the sheet's ×: checks the asset again with this run's steps (the rest of the run isn't run), for
 * whoever may run checks once the run has finished. It spins with "Checking…" while it runs, and waits while another
 * asset is being checked.
 */
@Composable
private fun RevalidateButton(run: ValRun, assetId: String, s: RunReviewUiState, vm: RunReviewViewModel) {
    if (!(s.canCheck && run.status == RunStatus.COMPLETED)) return
    val mine = s.rechecking == assetId
    val enabled = s.rechecking == null && !s.busy
    val hint = stringResource(R.string.rev_revalidate_hint)
    Row(
        Modifier.height(36.dp).clip(Capsule).background(C.YTintBg).border(1.dp, C.YTintBd, Capsule)
            .pressClickable(shape = Capsule, enabled = enabled, role = Role.Button, onClick = vm::recheck)
            .semantics { stateDescription = hint }.testTag("review_revalidate").padding(horizontal = 12.dp)
            .alpha(if (enabled || mine) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (mine) CircularProgressIndicator(Modifier.size(14.dp), color = C.Yellow, strokeWidth = 2.dp)
        else LineIcon(Icons.Refresh, 14.dp, C.Yellow, 2.4f)
        Txt(stringResource(if (mine) R.string.rev_rechecking else R.string.rev_revalidate), 12, 700, C.Yellow)
    }
}

/** Mark as validated, Recollect or Exclude (reviewers), with how many issues are still open; or the decision made. */
@Composable
private fun DecisionRow(
    decision: Decision?,
    note: String?,
    open: Int,
    s: RunReviewUiState,
    p: AssetPanelState,
    vm: RunReviewViewModel,
    /** A draft or exception: the run is a check, so Mark as validated is off until it is submitted (as the web). */
    checkOnly: Boolean = false,
) {
    when {
        decision != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip(decisionLabel(decision), decisionColor(decision), decisionColor(decision).copy(alpha = 0.16f))
            Txt(note?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.valt_quoted, it) }.orEmpty(), 12, 400, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
            if (s.canValidate) {
                TextAction(stringResource(R.string.rev_undo_decision), { vm.decide(listOf(p.assetId), Decision.CLEAR) }, color = C.Soft, enabled = !s.busy, icon = Icons.Refresh)
            }
        }
        !s.canValidate -> Txt(stringResource(R.string.rev_reviewer_signs_off), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    stringResource(R.string.rev_mark_validated), { if (!s.busy && !checkOnly) vm.decide(listOf(p.assetId), Decision.VALIDATED) },
                    Modifier.alpha(if (checkOnly) 0.4f else 1f), height = 38.dp, hPad = 14.dp, size = 12, weight = 700,
                )
                Pill(stringResource(R.string.rev_decision_recollect), { if (!s.busy) vm.askRecollect(listOf(p.assetId)) }, height = 38.dp, hPad = 14.dp, size = 12, weight = 700)
                Pill(stringResource(R.string.rev_exclude), { if (!s.busy) vm.decide(listOf(p.assetId), Decision.EXCLUDED) }, height = 38.dp, hPad = 14.dp, size = 12, weight = 700)
            }
            if (checkOnly) Txt(stringResource(R.string.rev_check_only_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
            else if (open > 0) Txt(pluralStringResource(R.plurals.rev_fix_to_validate, open, open), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
    }
}

/** A step of the asset: how it went there (and how many issues are open), what it checks, and its issues. */
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
    fillIn: (() -> Unit)?,
    loading: Boolean = false,
) {
    val open = issues.count { it.status == IssueStatus.OPEN }
    val c = outcomeColor(outcome)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.copy(alpha = 0.16f)).border(1.5.dp, c, CircleShape), contentAlignment = Alignment.Center) {
            when (outcome) {
                StepOutcome.FAILED -> LineIcon(Icons.Close, 12.dp, c, 3f)
                StepOutcome.WARNING -> Txt("!", 13, 800, c)
                StepOutcome.PASSED -> LineIcon(Icons.Check, 12.dp, c, 3f)
                StepOutcome.SKIPPED -> LineIcon(Icons.Minus, 12.dp, c, 3f)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(stringResource(R.string.valt_step_numbered, index + 1, stepName(steps, key)), 14, 700, modifier = Modifier.weight(1f), maxLines = 2)
                    Txt(
                        if (issues.isEmpty()) outcomeLabel(outcome)
                        else stringResource(
                            R.string.valt_dot_pair, outcomeLabel(outcome),
                            if (open > 0) stringResource(R.string.rev_n_open, open) else stringResource(R.string.rev_all_handled),
                        ),
                        11, 700, c,
                    )
                }
                val about = if (outcome == StepOutcome.SKIPPED && issues.isEmpty()) stringResource(R.string.rev_step_not_applicable) else steps[key]?.description
                about?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
            }
            issues.forEach { IssueRow(it, p, s, vm, locked, fillIn) }
            // The asset's issues are still being read: a step that failed or needs a look shows a placeholder issue.
            if (loading && issues.isEmpty() && outcome.problem) {
                SkeletonList(stringResource(R.string.val_loading), count = 1, lead = SkeletonLead.NONE, lines = 3)
            }
        }
    }
}

/** An issue: a bar in its severity's colour, what it is about and what is wrong, then what can be done (or was). */
@Composable
private fun IssueRow(issue: RunIssue, p: AssetPanelState, s: RunReviewUiState, vm: RunReviewViewModel, locked: Boolean, fillIn: (() -> Unit)?) {
    val busy = issue.id in p.busy
    val open = issue.status == IssueStatus.OPEN
    val sevColor = when (issue.severity) {
        IssueSeverity.ERROR -> C.Red
        IssueSeverity.WARNING -> C.Orange
        IssueSeverity.INFO -> C.Blue
    }
    val bar = if (open) sevColor else C.Border2
    Column(
        Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
            .border(1.dp, C.Border, r(10)).alpha(if (busy) 0.6f else 1f).padding(start = 13.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val about = listOfNotNull(issue.fieldName, issue.ruleName.takeIf { it.isNotBlank() }).joinToString(" · ")
            val fromWeb = issue.suggestions.any { it.sourceUrl != null }
            if (about.isNotBlank() || fromWeb) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(about, 10, 600, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                    if (fromWeb) SmallChip(stringResource(R.string.rev_from_web), C.Blue, C.Blue.copy(alpha = 0.14f))
                }
            }
            Txt(issue.message, 13, 700, C.Text, maxLines = Int.MAX_VALUE)
            if (open) OpenIssue(issue, p, s, vm, busy, locked, fillIn) else ClosedIssue(issue, s, vm, busy)
        }
    }
}

@Composable
private fun OpenIssue(
    issue: RunIssue,
    p: AssetPanelState,
    s: RunReviewUiState,
    vm: RunReviewViewModel,
    busy: Boolean,
    locked: Boolean,
    /** Opens the asset to fill in missing data; null when it isn't on this phone. */
    fillIn: (() -> Unit)?,
) {
    if (!s.canCheck) return
    if (issue.canApply) {
        // A locked asset still shows what is suggested, without Apply (as the web).
        issue.suggestions.forEachIndexed { i, sug ->
            SuggestionRow(sug, best = i == 0, enabled = !busy, onApply = if (locked) null else ({ vm.applySuggestion(issue, sug.value) }))
        }
    }
    if (locked) {
        Txt(stringResource(R.string.rev_locked_note), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (issue.related.isNotEmpty() && s.canValidate) {
            Pill(stringResource(R.string.rev_merge), { if (!busy) vm.openMerge(issue) }, height = 34.dp, hPad = 14.dp, size = 12, weight = 700)
        }
        // Missing data (the Complete step) can't be kept as it is: the next check would find it again. It is filled in.
        if (!issue.canDismiss) {
            fillIn?.let { Pill(stringResource(R.string.rev_fill_in), it, height = 34.dp, hPad = 14.dp, size = 12, weight = 700) }
        } else Pill(
            stringResource(
                when {
                    issue.related.isNotEmpty() -> R.string.rev_not_duplicate
                    issue.likeField -> R.string.rev_keep_value
                    issue.target == IssueTarget.CLASSIFICATION -> R.string.rev_keep_as_is
                    else -> R.string.rev_dismiss
                },
            ),
            { if (!busy) vm.dismiss(issue) }, height = 34.dp, hPad = 14.dp, size = 12, weight = 700,
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
                Txt(sug.shown.ifBlank { "—" }, 13, 700, modifier = Modifier.weight(1f, fill = false), maxLines = Int.MAX_VALUE)
                if (best) Txt(stringResource(R.string.rev_best_match), 10, 600, C.Yellow)
            }
            val pct = (sug.confidence * 100).roundToInt()
            val confColor = when {
                sug.confidence >= 0.8 -> C.Green
                sug.confidence >= 0.5 -> C.Orange
                else -> C.Red
            }
            Txt(stringResource(R.string.rev_sure, pct), 10, 600, confColor)
            sug.reason?.takeIf { it.isNotBlank() }?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
            sug.sourceUrl?.let { url ->
                val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url
                Txt(
                    host, 11, 600, C.Blue,
                    Modifier.pressClickable(shape = r(6), role = Role.Button) { runCatching { uri.openUri(url) } }.padding(vertical = 4.dp),
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
        12, 600, if (issue.status == IssueStatus.DISMISSED) C.Muted else C.Green, maxLines = Int.MAX_VALUE,
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
    GuardedClose(m.changed, vm::closeMerge) { close ->
        BottomSheet(close, maxHeightFraction = 0.95f) {
            SheetHeader(stringResource(R.string.rev_merge_title), stringResource(R.string.rev_merge_sub), close)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (m.loading) SkeletonList(stringResource(R.string.val_loading), count = 3, lead = SkeletonLead.NONE)
                if (m.assets.isNotEmpty()) Txt(stringResource(R.string.rev_merge_keep), 12, 700, C.Yellow)
                m.assets.forEach { a ->
                    OptionCard(a.name.ifBlank { stringResource(R.string.val_unnamed) }, a.place, m.keepId == a.id, { vm.setMergeKeep(a.id) })
                }
                val differing = m.differing
                if (m.assets.isNotEmpty()) {
                    Txt(stringResource(R.string.rev_merge_differ), 12, 700, C.Yellow, Modifier.padding(top = 8.dp))
                    if (differing.isEmpty()) Txt(stringResource(R.string.rev_merge_same), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
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
                                Modifier.fillMaxWidth().pressClickable(shape = r(8), pressed = PRESSED_CARD, role = Role.RadioButton) { vm.chooseMergeValue(f, a.id) }
                                    .heightIn(min = 44.dp).padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioDot(on)
                                Column(Modifier.weight(1f)) {
                                    Txt(v.ifBlank { stringResource(R.string.rev_empty) }, 12, 600, if (v.isBlank()) C.Muted else C.Text, maxLines = Int.MAX_VALUE)
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
                    Modifier.fillMaxWidth().topBorder(C.Border).padding(start = 16.dp, end = 16.dp, top = 8.dp), maxLines = Int.MAX_VALUE,
                )
            }
            Row(Modifier.fillMaxWidth().then(if (m.assets.size > 1) Modifier else Modifier.topBorder(C.Border)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.val_cancel), close, height = 44.dp)
                YellowButton(stringResource(R.string.rev_merge_button), vm::merge, Modifier.weight(1f), enabled = !m.loading && !m.saving && m.keepId != null)
            }
        }
    }
}
