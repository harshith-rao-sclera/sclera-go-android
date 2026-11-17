package com.sclera.sclerago.domain.validation

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
    val rule: String,
    val severity: IssueSeverity,
    val message: String,
    /** The first suggested fix, as text. */
    val suggestion: String?,
    /** open, accepted, dismissed or resolved. */
    val status: String,
) {
    val step: String get() = stepOf(rule)
}

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
