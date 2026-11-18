package com.sclera.sclerago.presentation.assets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.StepIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepResult
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.util.Locale

/** Test tags of the asset's validation strip and tab. */
object ValidationTags {
    const val RUN = "validation-run"
    const val NONE = "validation-none"
    const val BANNER = "validation-banner"
    const val FAB = "validation-fab"
    fun step(rule: String) = "validation-step-$rule"
}

/** The server's validation steps, by key, and their names. */
private val STEP_NAMES = mapOf(
    "complete" to R.string.val_step_complete, "valid" to R.string.val_step_valid, "unique" to R.string.val_step_unique,
    "consistent" to R.string.val_step_consistent, "verified" to R.string.val_step_verified,
)

/** A step's name ("Complete", "Valid"…); a step the app doesn't know by its key. */
internal fun stepNameText(rule: String): UiText = STEP_NAMES[rule]?.let { UiText.Res(it) } ?: UiText.Raw(rule)

/** An outcome as the web words it: Passed, Needs a look, Failed, Not applicable. */
@StringRes
internal fun outcomeRes(o: StepOutcome): Int = when (o) {
    StepOutcome.PASSED -> R.string.val_outcome_passed
    StepOutcome.WARNING -> R.string.val_outcome_warning
    StepOutcome.FAILED -> R.string.val_outcome_failed
    StepOutcome.SKIPPED -> R.string.val_outcome_skipped
}

private fun toneOf(o: StepOutcome?): Color = when (o) {
    StepOutcome.FAILED -> C.Red
    StepOutcome.WARNING -> C.Orange
    StepOutcome.PASSED -> C.Green
    else -> C.Muted
}

/** The run button's words: Validate, Revalidate from step n, or Revalidate. */
@Composable
private fun runLabel(v: AssetValidation?): String = when {
    v == null -> stringResource(R.string.val_validate)
    v.firstProblem != null -> stringResource(R.string.val_revalidate_from, v.firstProblem)
    else -> stringResource(R.string.val_revalidate)
}

/** A round mark of how validation stands: a tick, a cross, "!" or nothing yet. */
@Composable
private fun OutcomeIcon(o: StepOutcome?) {
    Box(Modifier.size(26.dp).background(toneOf(o).copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        OutcomeMark(o, 12.dp)
    }
}

/** "1 failed", "2 to look at" or "1 failed, 2 to look at": the counts that aren't zero, as the web words them. */
@Composable
private fun problemCountText(v: AssetValidation): String = when {
    v.failedCount > 0 && v.warningCount > 0 -> stringResource(R.string.val_problems_both, v.failedCount, v.warningCount)
    v.failedCount > 0 -> stringResource(R.string.val_problems_failed, v.failedCount)
    else -> stringResource(R.string.val_problems_look, v.warningCount)
}

/** The outcome's mark: a tick, a cross, "!" or a shield for not applicable, in the outcome's colour. */
@Composable
private fun OutcomeMark(o: StepOutcome?, size: Dp) {
    val color = toneOf(o)
    when (o) {
        StepOutcome.PASSED -> LineIcon(Icons.Check, size, color, 3f)
        StepOutcome.FAILED -> LineIcon(Icons.Close, size, color, 3f)
        StepOutcome.WARNING -> Txt("!", size.value.toInt() + 2, 800, color)
        else -> LineIcon(Icons.ShieldCheck, size, color, 2.4f)
    }
}

/**
 * The Info tab's validation line when a step failed or needs a look: how many steps need attention and why, with a
 * chevron; tapping it opens the Validation tab. Nothing shows while every step passed or none has run.
 */
@Composable
fun ValidationBanner(validation: AssetValidation?, onOpen: () -> Unit) {
    val v = validation?.takeIf { it.problems.isNotEmpty() } ?: return
    Row(
        // A full-width strip like the Info sections under it.
        Modifier.fillMaxWidth().testTag(ValidationTags.BANNER).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border)
            .clickable(role = Role.Button, onClick = onOpen).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutcomeIcon(v.outcome)
        Txt(
            stringResource(R.string.val_banner, v.problems.size, v.steps.size, problemCountText(v)),
            13, 600, modifier = Modifier.weight(1f), maxLines = 3,
        )
        LineIcon(Icons.ChevronRight, 14.dp, C.Muted, 2.4f)
    }
}

/**
 * The Validation tab: a header with how many steps need attention (or that every step passed) and the data quality,
 * then one card with every step in order: its mark, name and outcome, "Starts here" on the first to look at, and what
 * each step that failed or needs a look found. Then the run buttons and what a run keeps. [onRun] is null for someone
 * without the check permission: the run buttons are left out.
 */
@Composable
fun ValidationTab(validation: AssetValidation?, validating: Boolean, onRun: ((fromStart: Boolean) -> Unit)?) {
    val v = validation
    if (v == null) {
        // Nothing has been run on it: just say so, as the web does (runs are started from the Validation page).
        Txt(
            stringResource(R.string.val_none_message), 13, 500, C.Soft,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp).testTag(ValidationTags.NONE),
            maxLines = 4, align = TextAlign.Center,
        )
        return
    }
    // Header: how many steps need attention, and the data quality.
    val problems = v.problems.size
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(r(10)).background(toneOf(v.outcome).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { OutcomeMark(v.outcome, 18.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(
                if (problems > 0) pluralStringResource(R.plurals.val_steps_attention, problems, problems)
                else stringResource(R.string.val_passed_every_step),
                16, 800, maxLines = 2,
            )
            Txt(stringResource(R.string.val_quality, v.score), 12, 500, C.Muted)
        }
    }
    if (v.runs.size > 1) {
        val runs = v.runs.map { "${it.jobName} ${agoText(it.checkedAt)}" }.joinToString(", ")
        Txt(stringResource(R.string.val_every_step_from, v.runs.size, runs), 11, 400, C.Soft, maxLines = 4)
    }
    // Every step in one card.
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))) {
        v.steps.forEachIndexed { i, step ->
            StepRow(step, v, startsHere = v.firstProblem == i + 1, divider = i > 0)
        }
    }
    // Actions: only for someone who may run checks (the server's check permission).
    if (onRun == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill(
                if (validating) stringResource(R.string.val_checking) else runLabel(v), { if (!validating) onRun(false) },
                Modifier.testTag(ValidationTags.RUN).alpha(if (validating) 0.6f else 1f),
                bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 16.dp, size = 12, weight = 700,
            )
            val first = v.firstProblem
            if (first != null && first >= 2 && !validating) {
                Txt(
                    stringResource(R.string.val_run_every), 12, 700, C.Yellow,
                    Modifier.clip(r(8)).clickable { onRun(true) }.heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 12.dp),
                )
            }
        }
        val first = v.firstProblem
        val firstName = first?.let { stepNameText(v.steps[it - 1].rule).asString() }
        Txt(
            when {
                first != null && first >= 3 -> stringResource(R.string.val_hint_from, first - 1, firstName.orEmpty())
                first == 2 -> stringResource(R.string.val_hint_step1, firstName.orEmpty())
                else -> stringResource(R.string.val_hint_any)
            },
            11, 400, C.Muted, maxLines = 4,
        )
    }
}

/**
 * The Validation tab's floating ▶, like the Info tab's yellow round buttons: validates the asset at the server (from its
 * first step to look at, as Revalidate; an asset never validated gets the server's Quick check). While a run is going it
 * spins and can't be tapped. Validating needs the server: [offline], it is dimmed with the offline badge and a tap calls
 * [onOffline] (to say why).
 */
@Composable
fun ValidateFab(validating: Boolean, offline: Boolean, onValidate: () -> Unit, onOffline: () -> Unit, modifier: Modifier = Modifier) {
    OfflineGate(offline && !validating, onOffline, modifier.padding(end = 16.dp, bottom = 16.dp), badgeOffset = 2.dp) {
        CircleFab(
            Icons.Play, stringResource(if (validating) R.string.val_checking else R.string.val_validate), onValidate,
            Modifier.testTag(ValidationTags.FAB), stroke = 2.2f, busy = validating,
        )
    }
}

/**
 * One step in the steps card: its mark, name, outcome and "Starts here" on the first to look at; a step that failed or
 * needs a look lists what it found underneath.
 */
@Composable
private fun StepRow(step: StepResult, v: AssetValidation, startsHere: Boolean, divider: Boolean) {
    Row(
        Modifier.fillMaxWidth().testTag(ValidationTags.step(step.rule)).then(if (divider) Modifier.topBorder(C.Border) else Modifier)
            .alpha(if (step.outcome == StepOutcome.SKIPPED) 0.55f else 1f).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutcomeIcon(step.outcome)
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(stepNameText(step.rule).asString(), 15, 800)
                Txt(stringResource(outcomeRes(step.outcome)), 11, 700, if (step.outcome == StepOutcome.SKIPPED) C.Soft else toneOf(step.outcome))
                if (startsHere) Txt(stringResource(R.string.val_starts_here).uppercase(Locale.getDefault()), 10, 800, C.Yellow)
            }
            if (v.runs.size > 1) Txt(step.jobName, 10, 500, C.Muted)
            if (step.outcome.problem) {
                val issues = v.issuesFor(step)
                if (issues.isEmpty()) Txt(stringResource(R.string.val_no_issues), 12, 400, C.Muted)
                issues.forEach { IssueRow(it) }
            }
        }
    }
}

/** One problem a step found: a dot for how serious, the message, the suggested fix and, once handled, its status. */
@Composable
private fun IssueRow(issue: StepIssue) {
    val dot = when (issue.severity) {
        IssueSeverity.ERROR -> C.Red
        IssueSeverity.WARNING -> C.Orange
        IssueSeverity.INFO -> C.Muted
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 5.dp).size(6.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(issue.message, 12, 400, maxLines = 6)
            issue.suggestion?.let { Txt(stringResource(R.string.val_suggested, it), 11, 500, C.Soft, maxLines = 3) }
            if (issue.status != "open") Txt("(${issue.status})", 10, 500, C.Muted)
        }
    }
}

