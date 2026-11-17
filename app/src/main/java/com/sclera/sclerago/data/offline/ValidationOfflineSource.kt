package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.mapper.toAssetResult
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.validation.AssetResult
import com.sclera.sclerago.domain.validation.AssetRunRecord
import com.sclera.sclerago.domain.validation.IssueSeverity
import com.sclera.sclerago.domain.validation.RECENT_RESULTS
import com.sclera.sclerago.domain.validation.StepIssue
import com.sclera.sclerago.domain.validation.StepOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val LATEST_RESULT = """
    SELECT * FROM validation_results WHERE asset_id = ? AND %LIVE% ORDER BY created_at DESC, id DESC LIMIT 1"""

private const val JOB_ISSUES = "SELECT * FROM validation_issues WHERE job_id = ? AND %LIVE%%ASSET% ORDER BY created_at, id"

/** An asset's newest results with their runs' names and steps, newest first (ids are time-ordered), as the server reads them. */
private const val ASSET_RUNS = """
    SELECT r.id AS result_id, r.job_id, r.checks, r.outcome, r.created_at, j.name AS job_name, j.rules AS job_rules
    FROM validation_results r JOIN validation_jobs j ON j.id = r.job_id
    WHERE r.asset_id = ? AND %LIVE_R% AND %LIVE_J% ORDER BY r.id DESC LIMIT $RECENT_RESULTS"""

private const val ASSET_ISSUES = "SELECT * FROM validation_issues WHERE asset_id = ? AND %LIVE% ORDER BY id"

/** An asset's validation results and issues as sync brought them (its Validation tab and its list badge). Reads only. */
class ValidationOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

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

    private fun issueRows(jobId: String, assetId: String?): List<DbRow> = db.whenTable("validation_issues", emptyList()) {
        db.query(issuesSql(withAsset = assetId != null), listOfNotNull(jobId, assetId))
    }

    private fun issuesSql(withAsset: Boolean) =
        JOB_ISSUES.replace("%LIVE%", db.notRemoved("validation_issues")).replace("%ASSET%", if (withAsset) " AND asset_id = ?" else "")
}
