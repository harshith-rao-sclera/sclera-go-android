package com.sclera.sclerago.domain.validation

import com.sclera.sclerago.domain.model.ValidationMark

/**
 * An asset's validation as the server works it out (its `combined_checks` and `latest_validations`) from the
 * validation runs synced to the phone: every step any run checked, each with its latest outcome.
 */

/** How a validation step came out (the server's `validation_results.checks` values). */
enum class StepOutcome {
    PASSED, WARNING, FAILED, SKIPPED;

    /** Failed or to look at: the step needs attention. */
    val problem: Boolean get() = this == WARNING || this == FAILED

    companion object {
        fun of(text: String?): StepOutcome = when (text) {
            "passed" -> PASSED
            "warning" -> WARNING
            "failed", "error" -> FAILED
            else -> SKIPPED
        }
    }
}

/** How serious a problem a check found is. */
enum class IssueSeverity { ERROR, WARNING, INFO }

/** One validation result of an asset, with its run's name and steps in their order ([rules] are step keys). */
data class AssetRunRecord(
    val resultId: String,
    val jobId: String,
    val jobName: String,
    val rules: List<String>,
    val checks: Map<String, StepOutcome>,
    val outcome: StepOutcome,
    val checkedAt: Long?,
)

/** One problem a check of a run found on the asset ([rule] is the check, [step] the step it belongs to). */
data class StepIssue(
    val id: String,
    val jobId: String,
    val target: IssueTarget,
    /** The field it is about (the server's fieldName: "JACS code" for the code), null when about no field. */
    val fieldName: String?,
    /** The field's type ("date", "text"…), as the run's issue has it; null when about no field. */
    val fieldType: String? = null,
    val rule: String,
    val severity: IssueSeverity,
    /** A short fragment that doesn't repeat the field name: the field is shown as [label]. */
    val message: String,
    val currentValue: IssueValue?,
    /** The best suggested fix. */
    val suggestion: IssueSuggestion?,
    val status: IssueStatus,
) {
    val step: String get() = stepOf(rule)

    val closed: Boolean get() = status != IssueStatus.OPEN

    /** What it is about, as the web's short label: the field, the asset's type, or what a check with no field looks at. */
    val label: IssueLabel?
        get() = when {
            target == IssueTarget.CLASSIFICATION -> IssueLabel.Type
            !fieldName.isNullOrBlank() -> IssueLabel.Field(fieldName)
            else -> NO_FIELD_LABEL[rule]
        }

    /** The value it has now, shown before the arrow (null: not shown), as the web's currentText. */
    val now: IssueNow?
        get() = when {
            target == IssueTarget.CLASSIFICATION -> when {
                // Same model, another type: the message starts with the type it has ("HVAC › Fans: differs from…").
                rule != CLASSIFICATION_SET -> message.substringBefore(": ").takeIf { it.isNotEmpty() }?.let(IssueNow::Text)
                severity == IssueSeverity.ERROR -> IssueNow.NotSet
                else -> IssueNow.Partial
            }
            currentValue == null || (currentValue.json.startsWith("\"") && currentValue.text.isBlank()) -> IssueNow.Empty
            currentValue.json.startsWith("{") -> null
            else -> currentValue.text.takeIf { it.isNotEmpty() }?.let(IssueNow::Text)
        }

    /** The value the fix would give, shown after the arrow (null: no fix to offer). */
    val next: String? get() = suggestion?.shown?.takeIf { it.isNotBlank() }
}

/** What an issue is about, shown as its label. */
sealed interface IssueLabel {
    data class Field(val name: String) : IssueLabel
    data object Type : IssueLabel
    data object Location : IssueLabel
    data object Duplicates : IssueLabel
    data object Name : IssueLabel
}

/** An issue's value now: a value, or one of the web's "none" words. */
sealed interface IssueNow {
    data class Text(val text: String) : IssueNow
    data object Empty : IssueNow
    data object NotSet : IssueNow
    data object Partial : IssueNow
}

private const val CLASSIFICATION_SET = "classification_set"

/** The label of a check that is about no field (the web's NO_FIELD_LABEL). */
private val NO_FIELD_LABEL = mapOf(
    "location_set" to IssueLabel.Location,
    "possible_duplicates" to IssueLabel.Duplicates,
    "consistent_naming" to IssueLabel.Name,
)

/** One step of the asset's validation: its latest outcome and the run it came from. */
data class StepResult(val rule: String, val outcome: StepOutcome, val jobId: String, val jobName: String, val checkedAt: Long?)

/** One run that gave the asset some of its steps. */
data class RunResult(val jobId: String, val jobName: String, val outcome: StepOutcome, val checkedAt: Long?)

/** The asset's validation across every run done on it. [firstProblem] is the 1-based number of the first step to look at. */
data class AssetValidation(
    val jobId: String,
    val jobName: String,
    val outcome: StepOutcome,
    val score: Int,
    val steps: List<StepResult>,
    val runs: List<RunResult>,
    val firstProblem: Int?,
    val checkedAt: Long?,
    val issues: List<StepIssue>,
) {
    /** The steps that need attention, with their 1-based numbers. */
    val problems: List<IndexedValue<StepResult>> get() = steps.withIndex().filter { it.value.outcome.problem }.map { IndexedValue(it.index + 1, it.value) }
    val failedCount: Int get() = steps.count { it.outcome == StepOutcome.FAILED }
    val warningCount: Int get() = steps.count { it.outcome == StepOutcome.WARNING }

    /** The problems found for [step] in the run that gave it its outcome, the most serious first. */
    fun issuesFor(step: StepResult): List<StepIssue> =
        issues.filter { it.step == step.rule && it.jobId == step.jobId }.sortedWith(compareBy({ it.severity.ordinal }, { it.id }))
}

/** The mark its row shows: failed when a step failed, needs a look when one only warned; null when nothing needs attention. */
fun AssetValidation.mark(): ValidationMark? = when {
    problems.isEmpty() -> null
    outcome == StepOutcome.FAILED -> ValidationMark.FAILED
    else -> ValidationMark.NEEDS_LOOK
}

/** How far back the server looks per asset for steps later runs didn't repeat (its `RECENT_RESULTS`). */
const val RECENT_RESULTS = 25

/** The step each server check belongs to (the server's validation catalogue). */
private val STEP_OF = mapOf(
    "required_fields" to "complete", "location_set" to "complete", "photos_present" to "complete", "classification_set" to "complete",
    "field_formats" to "valid", "placeholder_values" to "valid", "plausible_dates" to "valid", "number_ranges" to "valid",
    "cross_field" to "valid", "lifecycle_dates" to "valid",
    "duplicate_serials" to "unique", "possible_duplicates" to "unique",
    "consistent_manufacturers" to "consistent", "consistent_naming" to "consistent", "consistent_types" to "consistent",
    "model_consistency" to "consistent", "outlier_values" to "consistent",
    "photo_match" to "verified", "published_specs" to "verified", "jacs_prediction" to "verified", "ai_review" to "verified",
    "spelling" to "verified",
)

/** The step of the check [rule]; a step key stays itself. */
fun stepOf(rule: String): String = STEP_OF[rule] ?: rule

/** Steps whose findings are about something missing: they are filled in, never kept as they are (the server's). */
private val NOT_DISMISSABLE_STEPS = setOf("complete")

/** Whether a finding of the check [rule] may be kept as it is (dismissed), as the server's `can_dismiss`. */
fun canDismiss(rule: String): Boolean = stepOf(rule) !in NOT_DISMISSABLE_STEPS

/**
 * The asset's validation from its results ([records], newest first, at most [RECENT_RESULTS]) and their problems,
 * as the server builds it: the newest run's steps in its order, then steps only older runs had, each with the outcome
 * of the newest run that checked it. Null when the asset was never validated.
 */
fun combineValidation(records: List<AssetRunRecord>, issues: List<StepIssue>): AssetValidation? {
    val steps = mutableListOf<StepResult>()
    val seen = HashSet<String>()
    records.take(RECENT_RESULTS).forEach { r ->
        r.rules.forEach { rule ->
            if (seen.add(rule)) steps += StepResult(rule, r.checks[rule] ?: StepOutcome.SKIPPED, r.jobId, r.jobName, r.checkedAt)
        }
    }
    if (steps.isEmpty()) return null
    val latest = records.first { it.jobId == steps.first().jobId }
    val worst = steps.map { it.outcome }.maxByOrNull { rank(it) } ?: StepOutcome.PASSED
    val runs = steps.map { it.jobId }.distinct().map { id ->
        val r = records.first { it.jobId == id }
        RunResult(r.jobId, r.jobName, r.outcome, r.checkedAt)
    }
    return AssetValidation(
        jobId = latest.jobId,
        jobName = latest.jobName,
        outcome = if (worst == StepOutcome.SKIPPED) StepOutcome.PASSED else worst,
        score = score(steps.map { it.outcome }),
        steps = steps,
        runs = runs,
        firstProblem = steps.indexOfFirst { it.outcome.problem }.takeIf { it >= 0 }?.plus(1),
        checkedAt = latest.checkedAt,
        issues = issues,
    )
}

/** skipped < passed < warning < failed, as the server ranks outcomes. */
private fun rank(o: StepOutcome) = when (o) {
    StepOutcome.SKIPPED -> -1
    StepOutcome.PASSED -> 0
    StepOutcome.WARNING -> 1
    StepOutcome.FAILED -> 2
}

/** Data quality 0..100 as the server scores it: a failed step loses 1, a warning 0.4; skipped steps don't count. */
fun score(outcomes: List<StepOutcome>): Int {
    val counted = outcomes.filter { it != StepOutcome.SKIPPED }
    if (counted.isEmpty()) return 100
    val lost = counted.sumOf { if (it == StepOutcome.FAILED) 1.0 else if (it == StepOutcome.WARNING) 0.4 else 0.0 }
    return Math.round(100 * (1 - lost / counted.size)).toInt()
}
