package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.validation.AssetResult
import com.sclera.sclerago.domain.validation.Finding
import com.sclera.sclerago.domain.validation.JobStats
import com.sclera.sclerago.domain.validation.RuleStats
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.ValidationIssue
import com.sclera.sclerago.domain.validation.RuleOutcome
import com.sclera.sclerago.domain.validation.ValJob

/** The outcome a validation result, check or issue severity is stored as: passed, warning, failed (error). */
private fun outcomeFromDb(text: String?): RuleOutcome = when (text) {
    "passed" -> RuleOutcome.PASS
    "warning" -> RuleOutcome.LOOK
    "failed", "error" -> RuleOutcome.FAIL
    else -> RuleOutcome.NA
}

/**
 * A `validation_results` row as an [AssetResult]: its checks (rule to outcome) as findings, with the
 * message of the matching row in [issues] (`validation_issues` of this job and asset); an issue for a
 * rule that has no check entry becomes a finding of its own.
 */
fun DbRow.toAssetResult(issues: List<DbRow> = emptyList()): AssetResult {
    val checks = DbJson.stringMap(string("checks"))
    val messages = issues.groupBy { it.string("rule") }
    val findings = checks.map { (rule, outcome) ->
        Finding(rule, outcomeFromDb(outcome), messages[rule]?.firstOrNull()?.string("message"))
    } + issues.filter { it.string("rule") !in checks }.map {
        Finding(it.string("rule").orEmpty(), outcomeFromDb(it.string("severity")), it.string("message"))
    }
    return AssetResult(string("asset_id").orEmpty(), string("asset_name").orEmpty(), outcomeFromDb(string("outcome")), findings)
}

/**
 * A `validation_jobs` row as a [ValJob] of [propertyId]. [results] are its finished results (already mapped),
 * [scopeLabel] the display text of what it covered (the table doesn't keep one). A job with no stored asset
 * list covered the assets in [results].
 */
fun DbRow.toValJob(propertyId: String, results: List<AssetResult> = emptyList(), scopeLabel: String = ""): ValJob {
    val rules = DbJson.stringList(string("rules"))
    val assetIds = (DbJson.stringList(string("asset_ids"))).ifEmpty { results.map { it.assetId } }
    return ValJob(
        id = string("id").orEmpty(),
        title = string("name").orEmpty(),
        sourceId = string("validation_id").orEmpty(),
        checks = rules.size,
        scopeLabel = scopeLabel,
        assetIds = assetIds,
        startedAt = parseDbTime(string("started_at")) ?: parseDbTime(string("created_at")) ?: 0L,
        prop = propertyId,
        running = string("status") in setOf("queued", "running"),
        results = results,
    )
}

/** A `validations` row as a [SavedValidation]; [lastRun] is when a job of it last started. */
fun DbRow.toSavedValidation(lastRun: Long? = null) = SavedValidation(
    id = string("id").orEmpty(),
    name = string("name").orEmpty(),
    ruleIds = DbJson.stringList(string("rules")),
    lastRun = lastRun,
    description = string("description").orEmpty(),
)

/** A `validation_issues` row as a [ValidationIssue]. */
fun DbRow.toValidationIssue() = ValidationIssue(
    id = string("id").orEmpty(),
    jobId = string("job_id").orEmpty(),
    assetId = string("asset_id").orEmpty(),
    rule = string("rule").orEmpty(),
    outcome = outcomeFromDb(string("severity")),
    message = string("message").orEmpty(),
    currentValue = string("current_value"),
    status = string("status").orEmpty(),
)

/** The totals and per-rule counts of a `validation_jobs` row. */
fun DbRow.toJobStats() = JobStats(
    passed = int("passed_count") ?: 0,
    warning = int("warning_count") ?: 0,
    failed = int("failed_count") ?: 0,
    rules = DbJson.intMaps(string("rule_stats")).mapValues { (_, c) ->
        RuleStats(c["passed"] ?: 0, c["warning"] ?: 0, c["failed"] ?: 0, c["skipped"] ?: 0)
    },
)
