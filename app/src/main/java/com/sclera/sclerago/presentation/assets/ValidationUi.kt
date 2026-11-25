package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.common.issueMessage
import com.sclera.sclerago.presentation.components.RunFab
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.Capsule
import androidx.compose.foundation.layout.height
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.validation.AssetValidation
import com.sclera.sclerago.domain.validation.IssueLabel
import com.sclera.sclerago.domain.validation.IssueNow
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.StepIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.StepResult
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the asset's validation strip and tab. */
object ValidationTags {
    const val NONE = "validation-none"
    const val BANNER = "validation-banner"
    const val FAB = "validation-fab"
    const val OPEN_RESULT = "validation-open-result"
    const val RUN_EVERY = "validation-run-every"
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

/** How wide a step's mark is (its points' dots are centred under it). */
private val MARK_SIZE = 26.dp

/** A round mark of how validation stands: a tick, a cross, "!" or nothing yet. */
@Composable
private fun OutcomeIcon(o: StepOutcome?) {
    Box(Modifier.size(MARK_SIZE).background(toneOf(o).copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
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
        Modifier.pressClickable(pressed = PRESSED_CARD, role = Role.Button, onClick = onOpen).fillMaxWidth().testTag(ValidationTags.BANNER).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border)
            .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutcomeIcon(v.outcome)
        Txt(
            stringResource(R.string.val_banner, v.problems.size, v.steps.size, problemCountText(v)),
            13, 600, modifier = Modifier.weight(1f), maxLines = Int.MAX_VALUE,
        )
        LineIcon(Icons.ChevronRight, 14.dp, C.Muted, 2.4f)
    }
}

/**
 * The Validation tab: a header with how many steps need attention (or that every step passed) and the data quality,
 * then one card with every step in order: its mark, name and outcome, "Starts here" on the first to look at, and what
 * each step that failed or needs a look found. Validating again is the floating ▶ ([ValidateFab]).
 */
@Composable
fun ValidationTab(
    validation: AssetValidation?,
    /**
     * "Open in Validation": the Validation page on the asset's run (its first problem's run, else its latest) with this
     * asset's result open (its checks, issues and decision); null hides it.
     */
    onOpenResult: ((jobId: String) -> Unit)? = null,
    /** The asset is being validated again at the server: its results are about to change, so placeholders show instead. */
    validating: Boolean = false,
    /** The signed-in user may not open the Validation page: "Open in Validation" is greyed out and a tap says why. */
    openBlocked: UiText? = null,
) {
    val v = validation
    if (validating) {
        ValidationSkeleton(steps = v?.steps?.size?.takeIf { it > 0 } ?: PLACEHOLDER_STEPS)
        return
    }
    if (v == null) {
        // Nothing has been run on it: as the web, say so and that Validate (the button below) runs the Quick check.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp).testTag(ValidationTags.NONE),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Txt(stringResource(R.string.val_none_title), 14, 700, C.Text, align = TextAlign.Center)
            Txt(stringResource(R.string.val_none_text), 13, 500, C.Soft, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
        }
        return
    }
    // Header: how many steps need attention and the data quality; "Open in Validation" is the card's last row.
    val problems = v.problems.size
    // As the web's jobToOpen: the run where it first went wrong, else its latest.
    val runToOpen = v.problems.firstOrNull()?.value?.jobId?.takeIf { it.isNotBlank() } ?: v.jobId.takeIf { it.isNotBlank() }
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(r(10)).background(toneOf(v.outcome).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { OutcomeMark(v.outcome, 16.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(
                    if (problems > 0) pluralStringResource(R.plurals.val_steps_attention, problems, problems)
                    else stringResource(R.string.val_passed_every_step),
                    15, 700, maxLines = 2,
                )
                Txt(stringResource(R.string.val_quality, v.score), 12, 400, C.Muted)
            }
        }
        if (v.runs.size > 1) {
            val runs = v.runs.map { stringResource(R.string.scrt_val_run_checked_ago, it.jobName, agoText(it.checkedAt)) }.joinToString(", ")
            Txt(
                stringResource(R.string.val_every_step_from, v.runs.size, runs), 11, 400, C.Muted,
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), maxLines = Int.MAX_VALUE,
            )
        }
        if (runToOpen != null && onOpenResult != null) {
            AccessGate(openBlocked, Modifier.fillMaxWidth().topBorder(C.Border)) {
                Row(
                    Modifier.fillMaxWidth().pressClickable(role = Role.Button) { onOpenResult(runToOpen) }
                        .heightIn(min = 44.dp).padding(horizontal = 12.dp).testTag(ValidationTags.OPEN_RESULT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Txt(stringResource(R.string.val_open_in_validation), 13, 600, C.Yellow, Modifier.weight(1f))
                    LineIcon(Icons.Link, 14.dp, C.Yellow, 2.2f)
                }
            }
        }
    }
    // Every step in one card; as the web, it ends with where Revalidate picks up (Run every step starts again from step 1).
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))) {
        v.steps.forEachIndexed { i, step ->
            StepRow(step, v, startsHere = v.firstProblem == i + 1, divider = i > 0)
        }
        v.firstProblem?.takeIf { it > 1 }?.let { first -> v.steps.getOrNull(first - 1) }?.let { step ->
            Txt(
                stringResource(R.string.valt_resumes_at, stepNameText(step.rule).asString()), 11, 400, C.Muted,
                Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp), maxLines = Int.MAX_VALUE,
            )
        }
    }
}

/** How many placeholder steps show while an asset that has no results yet is validated. */
private const val PLACEHOLDER_STEPS = 4

/**
 * The Validation tab while the asset is validated again: the header card and the steps card in placeholders ([steps]
 * rows, each a mark and a name), read out as "Checking".
 */
@Composable
private fun ValidationSkeleton(steps: Int) {
    val label = stringResource(R.string.val_checking)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(Modifier.size(44.dp), r(10))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(0.6f, 14.dp)
                SkeletonLine(0.3f, 9.dp)
            }
        }
        Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))) {
            repeat(steps) { i ->
                Row(
                    Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SkeletonBlock(Modifier.size(24.dp), CircleShape)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonLine(if (i % 2 == 0) 0.5f else 0.4f, 11.dp)
                        SkeletonLine(0.25f, 8.dp)
                    }
                }
            }
        }
    }
}

/**
 * The Validation tab's floating run button, as the web's: ▶ with what it will do: "Validate" for an asset never
 * validated (the server's Quick check), "Revalidate from step 3" when a step needs a look (it resumes there; earlier
 * steps keep their pass), else "Revalidate". Beside it, when that step isn't the first, "Run every step" starts again
 * from step 1. While a run is going the button spins, says "Checking" and can't be tapped. Validating needs the server:
 * [offline], both are dimmed with the offline badge and a tap calls [onOffline] (to say why).
 */
@Composable
fun ValidateFab(
    validation: AssetValidation?,
    validating: Boolean,
    offline: Boolean,
    onValidate: (fromStart: Boolean) -> Unit,
    onOffline: () -> Unit,
    modifier: Modifier = Modifier,
    /** The signed-in user may not run checks on it: both are greyed out and a tap says why (going online won't help). */
    blocked: UiText? = null,
) {
    val first = validation?.firstProblem
    val label = when {
        validating -> stringResource(R.string.val_checking)
        validation == null -> stringResource(R.string.val_validate)
        first != null -> stringResource(R.string.val_revalidate_from, first)
        else -> stringResource(R.string.val_revalidate)
    }
    // One above the other at the end, so "Revalidate from step 3" and "Run every step" both show in full on a phone.
    Column(
        modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (first != null && first > 1 && !validating) {
            AccessGate(blocked) {
                OfflineGate(offline, onOffline) {
                    Pill(
                        stringResource(R.string.val_run_every), { onValidate(true) },
                        Modifier.fabShadow(Capsule).testTag(ValidationTags.RUN_EVERY), bg = C.Surface, height = 44.dp, hPad = 16.dp, size = 13, weight = 700,
                    )
                }
            }
        }
        AccessGate(blocked) {
            OfflineGate(offline && !validating, onOffline) {
                RunFab(label, validating, { onValidate(false) }, Modifier.testTag(ValidationTags.FAB))
            }
        }
    }
}

/**
 * One step in the steps card: its mark, name, outcome and "Starts here" on the first to look at; a step that failed or
 * needs a look lists what it found underneath.
 */
@Composable
private fun StepRow(step: StepResult, v: AssetValidation, startsHere: Boolean, divider: Boolean) {
    val tone = if (step.outcome == StepOutcome.SKIPPED) C.Soft else toneOf(step.outcome)
    Column(
        Modifier.fillMaxWidth().testTag(ValidationTags.step(step.rule)).then(if (divider) Modifier.topBorder(C.Border) else Modifier)
            .alpha(if (step.outcome == StepOutcome.SKIPPED) 0.55f else 1f).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The mark and the name, then its outcome and "Starts here" as small pills.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutcomeIcon(step.outcome)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(stepNameText(step.rule).asString(), 14, 600, modifier = Modifier.weight(1f, fill = false))
                    StepPill(stringResource(outcomeRes(step.outcome)), tone)
                    if (startsHere) StepPill(stringResource(R.string.val_starts_here), C.Yellow)
                }
                if (v.runs.size > 1) Txt(step.jobName, 11, 400, C.Muted)
            }
        }
        // What it found: each point's dot under the step's mark, its words under the step's name.
        if (step.outcome.problem) {
            val issues = v.issuesFor(step)
            if (issues.isEmpty()) Txt(stringResource(R.string.val_no_issues), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            issues.forEach { IssueRow(it) }
        }
    }
}

/** A step's outcome or "Starts here": its words in a small pill of its colour. */
@Composable
private fun StepPill(text: String, tint: Color) {
    Box(Modifier.height(20.dp).clip(Capsule).background(tint.copy(alpha = 0.14f)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Txt(text, 10, 700, tint)
    }
}

/**
 * One problem a step found, as the web shows it: "Field  old → new" when there's a fix to offer, else the label and the
 * message. Tapping it shows the full message and the fix's reason (the web's tooltip); a handled one shows its status.
 */
@Composable
private fun IssueRow(issue: StepIssue) {
    val dot = when (issue.severity) {
        IssueSeverity.ERROR -> C.Red
        IssueSeverity.WARNING -> C.Orange
        IssueSeverity.INFO -> C.Muted
    }
    val label = issue.label?.let { issueLabelText(it) }
    val now = issue.now
    val next = issue.next
    // A date problem with the field's day in local time, as the web's issueMessage.
    val message = issueMessage(issue.message, issue.fieldType, issue.currentValue?.text)
    val details = listOfNotNull(message.takeIf { label != null && next != null }, issue.suggestion?.reason?.takeIf { it.isNotBlank() })
    var open by remember(issue.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().alpha(if (issue.closed) 0.6f else 1f)
            .then(if (details.isNotEmpty()) Modifier.pressClickable(role = Role.Button) { open = !open } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The dot under the step's mark (as wide as it), level with the first line.
        Box(Modifier.width(MARK_SIZE).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label != null) Txt(label, 12, 600, maxLines = 2)
            if (label != null && next != null) {
                val nowText = now?.let { issueNowText(it) }
                val changeTo = stringResource(R.string.val_issue_change_to)
                // What it would become, after an arrow, taking the rest of its line.
                val becomes: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Txt("→", 12, 400, C.Muted, modifier = Modifier.semantics { contentDescription = changeTo })
                        Txt(next, 12, 600, maxLines = Int.MAX_VALUE, modifier = Modifier.weight(1f))
                    }
                }
                if (nowText == null || nowText.length <= SHORT_NOW) {
                    // A short value ("Empty", a code): the fix on one line, what it is now, an arrow, what it would become.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        if (nowText != null) Txt(nowText, 12, 400, C.Muted, maxLines = 2)
                        Box(Modifier.weight(1f)) { becomes() }
                    }
                } else {
                    // A long value (a description): it would take the whole line and squeeze the new one to a letter a
                    // line, so it goes above, and the arrow and what it would become below it.
                    Txt(nowText, 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    becomes()
                }
            } else {
                Txt(message, 12, 400, if (label != null) C.Muted else C.Text, maxLines = Int.MAX_VALUE)
            }
            if (open) details.forEach { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
        }
        issueStatusText(issue.status)?.let { Txt(it, 10, 500, C.Muted) }
    }
}

/** The longest current value an issue's fix shows on the same line as the arrow and the new value. */
private const val SHORT_NOW = 24

@Composable
private fun issueLabelText(label: IssueLabel): String = when (label) {
    is IssueLabel.Field -> label.name
    IssueLabel.Type -> stringResource(R.string.val_issue_type)
    IssueLabel.Location -> stringResource(R.string.val_issue_location)
    IssueLabel.Duplicates -> stringResource(R.string.val_issue_duplicates)
    IssueLabel.Name -> stringResource(R.string.val_issue_name)
}

@Composable
private fun issueNowText(now: IssueNow): String = when (now) {
    is IssueNow.Text -> now.text
    IssueNow.Empty -> stringResource(R.string.val_issue_empty)
    IssueNow.NotSet -> stringResource(R.string.val_issue_not_set)
    IssueNow.Partial -> stringResource(R.string.val_issue_partial)
}

@Composable
private fun issueStatusText(status: IssueStatus): String? = when (status) {
    IssueStatus.OPEN -> null
    IssueStatus.ACCEPTED -> stringResource(R.string.val_issue_accepted)
    IssueStatus.DISMISSED -> stringResource(R.string.val_issue_dismissed)
    IssueStatus.RESOLVED -> stringResource(R.string.val_issue_resolved)
}

