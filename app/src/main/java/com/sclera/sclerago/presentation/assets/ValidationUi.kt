package com.sclera.sclerago.presentation.assets

import android.text.format.DateFormat
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.StepIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepResult
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Test tags of the asset's validation strip and tab. */
object ValidationTags {
    const val RUN = "validation-run"
    const val NONE = "validation-none"
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

/** "3 min ago", "2 h ago", or the day for anything older, as the web says when a run was. */
@Composable
private fun agoText(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return ""
    val minutes = ((now - at) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.val_ago_now)
        minutes < 60 -> stringResource(R.string.val_ago_min, minutes.toInt())
        minutes < 24 * 60 -> stringResource(R.string.val_ago_hours, (minutes / 60).toInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
                .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        }
    }
}

/** The run button's words: Validate, Revalidate from step n, or Revalidate. */
@Composable
private fun runLabel(v: AssetValidation?): String = when {
    v == null -> stringResource(R.string.val_validate)
    v.firstProblem != null -> stringResource(R.string.val_revalidate_from, v.firstProblem)
    else -> stringResource(R.string.val_revalidate)
}

/** "Quick check, 5 min ago", or "3 validations, latest 5 min ago". */
@Composable
private fun runsLabel(v: AssetValidation): String =
    if (v.runs.size > 1) stringResource(R.string.val_runs_many, v.runs.size, agoText(v.checkedAt))
    else stringResource(R.string.val_run_one, v.jobName, agoText(v.checkedAt))

/** A round mark of how validation stands: a tick, a cross, "!" or nothing yet. */
@Composable
private fun OutcomeIcon(o: StepOutcome?) {
    val color = toneOf(o)
    Box(Modifier.size(26.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        when (o) {
            StepOutcome.PASSED -> LineIcon(Icons.Check, 13.dp, color, 3f)
            StepOutcome.FAILED -> LineIcon(Icons.Close, 12.dp, color, 3f)
            StepOutcome.WARNING -> Txt("!", 13, 800, color)
            else -> LineIcon(Icons.ShieldCheck, 13.dp, color, 2.4f)
        }
    }
}

/**
 * The Validation tab, as the web's: the outcome, data quality and runs, then every step with its latest outcome (the
 * steps to look at open, showing what each found and the suggested fix), then the run buttons and what a run keeps.
 */
@Composable
fun ValidationTab(validation: AssetValidation?, validating: Boolean, onRun: (fromStart: Boolean) -> Unit) {
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
    // Summary: outcome, data quality, which runs.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.clip(Capsule).background(toneOf(v.outcome).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutcomeIcon(v.outcome)
            Txt(stringResource(outcomeRes(v.outcome)), 11, 800, toneOf(v.outcome))
        }
        Column(Modifier.weight(1f)) {
            Txt(stringResource(R.string.val_quality, v.score), 12, 700)
            Txt(runsLabel(v), 10, 500, C.Muted, maxLines = 2)
        }
    }
    if (v.runs.size > 1) {
        val runs = v.runs.map { "${it.jobName} ${agoText(it.checkedAt)}" }.joinToString(", ")
        Txt(stringResource(R.string.val_every_step_from, v.runs.size, runs), 11, 400, C.Soft, maxLines = 4)
    }
    v.steps.forEachIndexed { i, step -> StepCard(i + 1, step, v, startsHere = v.firstProblem == i + 1, open = step.outcome.problem) }
    // Actions
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

/** One step: its number (a tick once passed), name, "Starts here" on the first to look at, and its outcome; open, what it found. */
@Composable
private fun StepCard(number: Int, step: StepResult, v: AssetValidation, startsHere: Boolean, open: Boolean) {
    val expandable = step.outcome.problem
    var shown by rememberSaveable(step.rule, step.jobId) { mutableStateOf(open) }
    Column(
        Modifier.fillMaxWidth().testTag(ValidationTags.step(step.rule)).clip(r(12)).background(C.Surface)
            .border(1.dp, if (startsHere) C.Yellow else C.Border, r(12)).alpha(if (step.outcome == StepOutcome.SKIPPED) 0.55f else 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (expandable) Modifier.clickable { shown = !shown } else Modifier)
                .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(24.dp).background(toneOf(step.outcome).copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                if (step.outcome == StepOutcome.PASSED) LineIcon(Icons.Check, 12.dp, C.Green, 3f) else Txt("$number", 11, 800, toneOf(step.outcome))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(stepNameText(step.rule).asString(), 14, 700)
                    if (startsHere) {
                        Txt(stringResource(R.string.val_starts_here), 10, 800, C.OnYellow, Modifier.background(C.Yellow, Capsule).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                if (v.runs.size > 1) Txt(step.jobName, 10, 500, C.Muted)
            }
            Txt(stringResource(outcomeRes(step.outcome)), 11, 700, if (step.outcome.problem) toneOf(step.outcome) else C.Soft)
            if (expandable) LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f, Modifier.rotate(if (shown) 180f else 0f))
        }
        if (expandable && shown) {
            val issues = v.issuesFor(step)
            Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (issues.isEmpty()) Txt(stringResource(R.string.val_no_issues), 11, 400, C.Muted)
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

