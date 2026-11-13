package com.sclera.sclerago.domain.validation

/** One problem a validation job found on an asset ([outcome] is FAIL for an error, LOOK for a warning). */
data class ValidationIssue(
    val id: String,
    val jobId: String,
    val assetId: String,
    val rule: String,
    val outcome: RuleOutcome,
    val message: String,
    val currentValue: String?,
    val status: String,
)

/** How many assets one rule passed, flagged or failed in a job. */
data class RuleStats(val passed: Int, val warning: Int, val failed: Int, val skipped: Int)

/** A job's totals and its per-rule counts. */
data class JobStats(val passed: Int, val warning: Int, val failed: Int, val rules: Map<String, RuleStats>)
