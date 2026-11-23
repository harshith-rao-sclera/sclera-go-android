package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.toRunAssetResult
import com.sclera.sclerago.data.mapper.toRunIssue
import com.sclera.sclerago.data.mapper.toValRun
import com.sclera.sclerago.data.mapper.toValidationDef
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.statusFromDb
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.RunAssetResult
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValidationDef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val VR_VALIDATIONS = "SELECT * FROM validations WHERE %LIVE% ORDER BY created_at DESC, id DESC"

/** The newest run of each saved validation that is not waiting to start. */
private const val VR_LAST_RUNS = """
    SELECT * FROM validation_jobs WHERE validation_id IS NOT NULL AND validation_id <> '' AND status <> 'ready' AND %LIVE%
    ORDER BY created_at DESC, id DESC"""

/** The saved validations that have runs (ready ones too): they can't be edited any more (the server's `locked`). */
private const val VR_RUN_VALIDATIONS = """
    SELECT DISTINCT validation_id FROM validation_jobs WHERE validation_id IS NOT NULL AND validation_id <> '' AND %LIVE%"""

/** The newest runs; a run checking one asset of another again (`recheck_of` in its settings) is part of that one, as the server lists them. */
private const val VR_RUNS = """
    SELECT * FROM validation_jobs WHERE %LIVE% AND (settings IS NULL OR settings NOT LIKE '%recheck_of%')
    ORDER BY created_at DESC, id DESC LIMIT %LIMIT%"""

private const val VR_RUN = "SELECT * FROM validation_jobs WHERE id = ? AND %LIVE%"

private const val VR_RESULTS = "SELECT * FROM validation_results WHERE job_id = ? AND %LIVE% ORDER BY created_at DESC, id DESC"

/** The status of each asset in [VR_RESULTS] now, so a run on drafts and exceptions reads as a check (the server's assetStatus). */
private const val VR_ASSET_STATUSES = """
    SELECT id, status, validated FROM assets WHERE id IN (SELECT asset_id FROM validation_results WHERE job_id = ?) AND %LIVE%"""

private const val VR_ISSUES ="SELECT * FROM validation_issues WHERE job_id = ? AND %LIVE%%ASSET% ORDER BY id"

private const val VR_OPEN_COUNTS = """
    SELECT asset_id, COUNT(*) AS n FROM validation_issues WHERE job_id = ? AND status = 'open' AND %LIVE% GROUP BY asset_id"""


private const val VR_FIELD_NAMES_TYPES = "SELECT id, name, type FROM asset_fields WHERE %LIVE%"

/**
 * The Validation page's data as sync last brought it to the phone (saved validations, runs, their results and issues),
 * for showing when there is no connection. Reads only.
 */
class ValidationRunsOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    suspend fun validations(): List<ValidationDef> = withContext(io) {
        db.whenTable("validations", emptyList()) {
            val last = db.whenTable("validation_jobs", emptyMap()) {
                db.query(VR_LAST_RUNS.live("validation_jobs")).map { it.toValRun() }.groupBy { it.validationId }.mapValues { it.value.first() }
            }
            val ran = db.whenTable("validation_jobs", emptySet<String>()) {
                db.query(VR_RUN_VALIDATIONS.live("validation_jobs")).mapNotNull { it.string("validation_id") }.toSet()
            }
            db.query(VR_VALIDATIONS.live("validations")).map { it.toValidationDef(last[it.string("id")], it.string("id") in ran) }
        }
    }

    suspend fun runs(limit: Int): List<ValRun> = withContext(io) {
        db.whenTable("validation_jobs", emptyList()) {
            db.query(VR_RUNS.live("validation_jobs").replace("%LIMIT%", limit.coerceIn(1, 100).toString())).map { it.toValRun() }
        }
    }

    suspend fun run(id: String): ValRun? = withContext(io) {
        db.whenTable("validation_jobs", null) { db.queryOne(VR_RUN.live("validation_jobs"), listOf(id))?.toValRun() }
    }

    /** Every result of [jobId], newest checked first, with each asset's open issue count. */
    suspend fun results(jobId: String): List<RunAssetResult> = withContext(io) {
        db.whenTable("validation_results", emptyList()) {
            val open = db.whenTable("validation_issues", emptyMap()) {
                db.query(VR_OPEN_COUNTS.live("validation_issues"), listOf(jobId)).associate { it.string("asset_id").orEmpty() to (it.int("n") ?: 0) }
            }
            val statuses = db.whenTable("assets", emptyMap()) {
                db.query(VR_ASSET_STATUSES.live("assets"), listOf(jobId))
                    .associate { it.string("id").orEmpty() to statusFromDb(it.string("status"), it.bool("validated")) }
            }
            db.query(VR_RESULTS.live("validation_results"), listOf(jobId)).map { r ->
                r.toRunAssetResult().let {
                    it.copy(
                        openCount = if (it.decision != null && it.decision.name == "EXCLUDED") 0 else open[it.assetId] ?: 0,
                        assetStatus = statuses[it.assetId],
                    )
                }
            }
        }
    }

    /** [jobId]'s issues ([assetId]'s only when given) with [status] (all when null). */
    suspend fun issues(jobId: String, assetId: String?, status: IssueStatus?): List<RunIssue> = withContext(io) {
        db.whenTable("validation_issues", emptyList()) {
            val fields = db.whenTable("asset_fields", emptyMap()) {
                db.query(VR_FIELD_NAMES_TYPES.live("asset_fields")).associate { it.string("id").orEmpty() to (it.string("name").orEmpty() to it.string("type").orEmpty()) }
            }
            val sql = VR_ISSUES.live("validation_issues").replace("%ASSET%", if (assetId != null) " AND asset_id = ?" else "")
            db.query(sql, listOfNotNull(jobId, assetId)).map { it.toRunIssue(fields) }.filter { status == null || it.status == status }
        }
    }

    private fun String.live(table: String) = replace("%LIVE%", db.notRemoved(table))
}
