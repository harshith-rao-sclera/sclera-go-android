package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.toMasterFormat
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.model.MasterFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val ACTIVE_FORMATS =
    "SELECT * FROM master_formats WHERE is_active = 1 AND %LIVE% ORDER BY position, system_type, asset_type, subtype"

private const val ALL_FORMATS = "SELECT * FROM master_formats WHERE %LIVE%"

private const val FORMAT_COUNTS = """
    SELECT m.id, m.system_type, m.asset_type, m.subtype, COUNT(a.id) AS n
    FROM master_formats m LEFT JOIN assets a ON a.master_format_id = m.id AND %LIVE_ASSETS%
    WHERE m.is_active = 1 AND %LIVE%
    GROUP BY m.id ORDER BY m.position, m.system_type, m.asset_type, m.subtype"""

/** The asset type catalogue (system type, asset type, sub type). Reads only. */
class MasterFormatsOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    /** Active formats in their order. */
    suspend fun active(): List<MasterFormat> = withContext(io) { loadActive() }

    fun activeFlow(): Flow<List<MasterFormat>> = db.watch(io, TABLE) { loadActive() }

    /** The catalogue as a tree, each level counting the assets filed under it. */
    suspend fun classification(): List<ClassCount> = withContext(io) { loadClassification() }

    fun classificationFlow(): Flow<List<ClassCount>> = db.watch(io, TABLE, "assets") { loadClassification() }

    /** Every format, active or not, by id: assets keep pointing at a format that was switched off. Blocking. */
    internal fun byId(): Map<String, MasterFormat> = db.whenTable(TABLE, emptyMap()) {
        db.query(ALL_FORMATS.replace("%LIVE%", db.notRemoved(TABLE))).map { it.toMasterFormat() }.associateBy { it.id }
    }

    private fun loadActive(): List<MasterFormat> = db.whenTable(TABLE, emptyList()) {
        db.query(ACTIVE_FORMATS.replace("%LIVE%", db.notRemoved(TABLE))).map { it.toMasterFormat() }
    }

    private fun loadClassification(): List<ClassCount> = db.whenTable(TABLE, emptyList()) {
        val assetsLive = if (db.hasTable("assets")) db.notRemoved("assets", "a") else "1 = 1"
        val sql = if (db.hasTable("assets")) FORMAT_COUNTS else FORMAT_COUNTS_NO_ASSETS
        val rows = db.query(sql.replace("%LIVE_ASSETS%", assetsLive).replace("%LIVE%", db.notRemoved(TABLE, "m")))
        group(rows)
    }

    private fun group(rows: List<DbRow>): List<ClassCount> =
        rows.groupBy { it.string("system_type").orEmpty() }.map { (system, inSystem) ->
            val own = inSystem.firstOrNull { it.blank("asset_type") }
            val types = inSystem.filterNot { it.blank("asset_type") }.groupBy { it.string("asset_type").orEmpty() }.map { (type, inType) ->
                val ownType = inType.firstOrNull { it.blank("subtype") }
                val subs = inType.filterNot { it.blank("subtype") }.map { leaf(it.string("subtype").orEmpty(), it) }
                ClassCount(type, (ownType?.count() ?: 0) + subs.sumOf { it.assets }, ownType?.string("id"), subs)
            }
            ClassCount(system, (own?.count() ?: 0) + types.sumOf { it.assets }, own?.string("id"), types)
        }

    private fun leaf(name: String, row: DbRow) = ClassCount(name, row.count(), row.string("id"))

    private fun DbRow.blank(column: String) = string(column).isNullOrBlank()
    private fun DbRow.count() = int("n") ?: 0

    private companion object {
        const val TABLE = "master_formats"
        const val FORMAT_COUNTS_NO_ASSETS = """
            SELECT m.id, m.system_type, m.asset_type, m.subtype, 0 AS n FROM master_formats m
            WHERE m.is_active = 1 AND %LIVE% ORDER BY m.position, m.system_type, m.asset_type, m.subtype"""
    }
}
