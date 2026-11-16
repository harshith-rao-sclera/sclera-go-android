package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.mapper.ruleIdsOf
import com.sclera.sclerago.data.mapper.toAssetResult
import com.sclera.sclerago.data.mapper.toJobStats
import com.sclera.sclerago.data.mapper.toSavedValidation
import com.sclera.sclerago.data.mapper.toValJob
import com.sclera.sclerago.data.mapper.toValidationIssue
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.validation.AssetResult
import com.sclera.sclerago.domain.validation.AssetRunRecord
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.RECENT_RESULTS
import com.sclera.sclerago.domain.validation.StepIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import com.sclera.sclerago.domain.validation.JobStats
import com.sclera.sclerago.domain.validation.RuleOutcome
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.ValidationIssue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.withContext

private const val SAVED = "SELECT * FROM validations WHERE %LIVE% ORDER BY created_at DESC, id DESC"

private const val LAST_RUNS = """
    SELECT validation_id, MAX(started_at) AS last_run FROM validation_jobs
    WHERE validation_id IS NOT NULL AND validation_id <> '' AND %LIVE% GROUP BY validation_id"""

private const val RULE_ORDER = "SELECT rule_order FROM validation_preferences WHERE user_id = ?"

private const val RECENT_JOBS = "SELECT * FROM validation_jobs WHERE %LIVE% ORDER BY created_at DESC, id DESC LIMIT %LIMIT%"

private const val JOB_BY_ID = "SELECT * FROM validation_jobs WHERE id = ? AND %LIVE%"

/** Results of the recent jobs without their checks (the jobs list only counts outcomes). */
private const val RESULT_OUTCOMES = """
    SELECT job_id, asset_id, asset_name, outcome, '{}' AS checks FROM validation_results
    WHERE job_id IN (%IN%) AND %LIVE%"""

private const val JOB_RESULTS = """
    SELECT * FROM validation_results WHERE job_id = ? AND %LIVE%%OUTCOME% ORDER BY score, asset_name, id"""

private const val LATEST_RESULT = """
    SELECT * FROM validation_results WHERE asset_id = ? AND %LIVE% ORDER BY created_at DESC, id DESC LIMIT 1"""

private const val JOB_ISSUES = "SELECT * FROM validation_issues WHERE job_id = ? AND %LIVE%%ASSET% ORDER BY created_at, id"

/** An asset's newest results with their runs' names and steps, newest first (ids are time-ordered), as the server reads them. */
private const val ASSET_RUNS = """
    SELECT r.id AS result_id, r.job_id, r.checks, r.outcome, r.created_at, j.name AS job_name, j.rules AS job_rules
    FROM validation_results r JOIN validation_jobs j ON j.id = r.job_id
    WHERE r.asset_id = ? AND %LIVE_R% AND %LIVE_J% ORDER BY r.id DESC LIMIT $RECENT_RESULTS"""

private const val ASSET_ISSUES = "SELECT * FROM validation_issues WHERE asset_id = ? AND %LIVE% ORDER BY id"

private val VALIDATION_TABLES = arrayOf("validations", "validation_jobs", "validation_results", "validation_issues", "validation_preferences")

/** How an outcome is stored in `validation_results.outcome`. */
private val OUTCOME_TEXT = mapOf(
    RuleOutcome.PASS to listOf("passed"),
    RuleOutcome.LOOK to listOf("warning"),
    RuleOutcome.FAIL to listOf("failed", "error"),
    RuleOutcome.NA to listOf("skipped"),
)

/** Saved validations, the user's rule order, validation jobs with their results and issues. Reads only. */
class ValidationOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    /** The user's own validations, newest first, each with when a job of it last started. */
    suspend fun saved(): List<SavedValidation> = withContext(io) { loadSaved() }

    fun savedFlow(): Flow<List<SavedValidation>> = db.watch(io, "validations", "validation_jobs") { loadSaved() }

    /** The order [userId] arranged the rules in; empty when never arranged. */
    suspend fun ruleOrder(userId: String): List<String> = withContext(io) { loadRuleOrder(userId) }

    fun ruleOrderFlow(userId: String): Flow<List<String>> = db.watch(io, "validation_preferences") { loadRuleOrder(userId) }

    /** The latest [limit] jobs, newest first. Their results carry the outcome per asset only; [job] has the checks. */
    suspend fun recentJobs(limit: Int = 20, propertyId: String = ""): List<ValJob> = withContext(io) { loadRecent(limit, propertyId) }

    fun recentJobsFlow(limit: Int = 20, propertyId: String = ""): Flow<List<ValJob>> =
        db.watch(io, "validation_jobs", "validation_results") { loadRecent(limit, propertyId) }

    /** One job with all its results and their findings; null when it is not there. */
    suspend fun job(jobId: String, propertyId: String = ""): ValJob? = withContext(io) { loadJob(jobId, propertyId) }

    fun jobFlow(jobId: String, propertyId: String = ""): Flow<ValJob?> =
        db.watch(io, "validation_jobs", "validation_results", "validation_issues") { loadJob(jobId, propertyId) }

    /**
     * The results of [jobId], optionally only those with [outcome]. With a [rule], only assets where that rule
     * came out as [outcome] (or any outcome when null).
     */
    suspend fun results(jobId: String, outcome: RuleOutcome? = null, rule: String? = null): List<AssetResult> =
        withContext(io) { loadResults(jobId, outcome, rule) }

    /** The problems of [jobId], or only those of [assetId] in it. */
    suspend fun issues(jobId: String, assetId: String? = null): List<ValidationIssue> = withContext(io) { loadIssues(jobId, assetId) }

    /** Passed, warning and failed counts of [jobId], and the same per rule. */
    suspend fun stats(jobId: String): JobStats? = withContext(io) {
        db.whenTable("validation_jobs", null) {
            db.queryOne(JOB_BY_ID.replace("%LIVE%", db.notRemoved("validation_jobs")), listOf(jobId))?.toJobStats()
        }
    }

    /** The first problem found for each asset of [jobId], as the issue's message. */
    suspend fun firstProblems(jobId: String): Map<String, String> = withContext(io) {
        loadIssues(jobId, null).groupBy { it.assetId }.mapValues { (_, issues) -> issues.first().message }
    }

    /**
     * [assetId]'s recent results (newest first) and every problem found on it, again whenever validation data changes:
     * what the asset's Validation tab is built from (see `combineValidation`).
     */
    fun assetRunsFlow(assetId: String): Flow<Pair<List<AssetRunRecord>, List<StepIssue>>> =
        db.watch(io, "validation_jobs", "validation_results", "validation_issues") { loadAssetRuns(assetId) to loadAssetIssues(assetId) }

    private fun loadAssetRuns(assetId: String): List<AssetRunRecord> = db.whenTable("validation_results", emptyList()) {
        if (!db.hasTable("validation_jobs")) return@whenTable emptyList()
        val sql = ASSET_RUNS.replace("%LIVE_R%", db.notRemoved("validation_results", "r")).replace("%LIVE_J%", db.notRemoved("validation_jobs", "j"))
        db.query(sql, listOf(assetId)).map { r ->
            AssetRunRecord(
                resultId = r.string("result_id").orEmpty(),
                jobId = r.string("job_id").orEmpty(),
                jobName = r.string("job_name").orEmpty(),
                rules = DbJson.stringList(r.string("job_rules")),
                checks = DbJson.stringMap(r.string("checks")).mapValues { (_, v) -> StepOutcome.of(v) },
                outcome = StepOutcome.of(r.string("outcome")),
                checkedAt = parseDbTime(r.string("created_at")),
            )
        }
    }

    private fun loadAssetIssues(assetId: String): List<StepIssue> = db.whenTable("validation_issues", emptyList()) {
        db.query(ASSET_ISSUES.replace("%LIVE%", db.notRemoved("validation_issues")), listOf(assetId)).map { r ->
            StepIssue(
                id = r.string("id").orEmpty(),
                jobId = r.string("job_id").orEmpty(),
                rule = r.string("rule").orEmpty(),
                severity = when (r.string("severity")) {
                    "error" -> IssueSeverity.ERROR
                    "warning" -> IssueSeverity.WARNING
                    else -> IssueSeverity.INFO
                },
                message = r.string("message").orEmpty(),
                suggestion = firstSuggestion(r.string("suggestions")),
                status = r.string("status") ?: "open",
            )
        }
    }

    /** The first suggested fix of an issue's `suggestions` JSON (`[{value, label, …}]`), as text. */
    private fun firstSuggestion(json: String?): String? {
        val first = (runCatching { Json.parseToJsonElement(json.orEmpty()) }.getOrNull() as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        fun text(key: String) = (first[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return text("label") ?: text("value")
    }

    /** [assetId]'s result in the newest job that checked it. Blocking. */
    internal fun latestResultNow(assetId: String): AssetResult? = db.whenTable("validation_results", null) {
        val row = db.queryOne(LATEST_RESULT.replace("%LIVE%", db.notRemoved("validation_results")), listOf(assetId))
        row?.toAssetResult(issueRows(row.string("job_id").orEmpty(), assetId))
    }

    private fun loadSaved(): List<SavedValidation> = db.whenTable("validations", emptyList()) {
        val lastRuns = db.whenTable("validation_jobs", emptyMap()) {
            db.query(LAST_RUNS.replace("%LIVE%", db.notRemoved("validation_jobs")))
                .associate { it.string("validation_id").orEmpty() to parseDbTime(it.string("last_run")) }
        }
        db.query(SAVED.replace("%LIVE%", db.notRemoved("validations"))).map { it.toSavedValidation(lastRuns[it.string("id")]) }
    }

    private fun loadRuleOrder(userId: String): List<String> = db.whenTable("validation_preferences", emptyList()) {
        ruleIdsOf(DbJson.stringList(db.queryOne(RULE_ORDER, listOf(userId))?.string("rule_order")))
    }

    private fun loadRecent(limit: Int, propertyId: String): List<ValJob> = db.whenTable("validation_jobs", emptyList()) {
        val jobs = db.query(RECENT_JOBS.replace("%LIVE%", db.notRemoved("validation_jobs")).replace("%LIMIT%", limit.coerceAtLeast(0).toString()))
        val results = db.whenTable("validation_results", emptyMap()) {
            db.queryIn(RESULT_OUTCOMES.replace("%LIVE%", db.notRemoved("validation_results")), jobs.mapNotNull { it.string("id") })
                .groupBy({ it.string("job_id").orEmpty() }, { it.toAssetResult() })
        }
        jobs.map { it.toValJob(propertyId, results[it.string("id")].orEmpty()) }
    }

    private fun loadJob(jobId: String, propertyId: String): ValJob? = db.whenTable("validation_jobs", null) {
        db.queryOne(JOB_BY_ID.replace("%LIVE%", db.notRemoved("validation_jobs")), listOf(jobId))
            ?.toValJob(propertyId, loadResults(jobId, null, null))
    }

    private fun loadResults(jobId: String, outcome: RuleOutcome?, rule: String?): List<AssetResult> =
        db.whenTable("validation_results", emptyList()) {
            val byRule = rule != null
            val texts = if (outcome != null && !byRule) OUTCOME_TEXT.getValue(outcome) else emptyList()
            val outcomeSql = if (texts.isEmpty()) "" else " AND outcome IN (${placeholders(texts.size)})"
            val rows = db.query(
                JOB_RESULTS.replace("%LIVE%", db.notRemoved("validation_results")).replace("%OUTCOME%", outcomeSql),
                listOf(jobId) + texts,
            )
            val issuesByAsset = issueRows(jobId, null).groupBy { it.string("asset_id").orEmpty() }
            rows.map { it.toAssetResult(issuesByAsset[it.string("asset_id")].orEmpty()) }
                .filter { r -> !byRule || r.findings.any { it.rule == rule && (outcome == null || it.outcome == outcome) } }
        }

    private fun issueRows(jobId: String, assetId: String?): List<DbRow> = db.whenTable("validation_issues", emptyList()) {
        db.query(issuesSql(withAsset = assetId != null), listOfNotNull(jobId, assetId))
    }

    private fun loadIssues(jobId: String, assetId: String?): List<ValidationIssue> = issueRows(jobId, assetId).map { it.toValidationIssue() }

    private fun issuesSql(withAsset: Boolean) =
        JOB_ISSUES.replace("%LIVE%", db.notRemoved("validation_issues")).replace("%ASSET%", if (withAsset) " AND asset_id = ?" else "")
}
