package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.validation.AssetResult
import com.sclera.sclerago.domain.validation.Finding
import com.sclera.sclerago.domain.validation.RuleOutcome

/** The outcome a validation result, check or issue severity is stored as: passed, warning, failed (error). */
internal fun outcomeFromDb(text: String?): RuleOutcome = when (text) {
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
