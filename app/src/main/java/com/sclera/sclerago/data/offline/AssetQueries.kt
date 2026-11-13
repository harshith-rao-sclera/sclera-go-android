package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.AssetFilter
import com.sclera.sclerago.domain.model.sourceToDb

/** The app's status as SQL, the same rules as `statusFromDb` (first match wins). Names are the [com.sclera.sclerago.domain.model.Status] names. */
internal const val STATUS_SQL = """CASE
    WHEN a.status = 'exception' THEN 'EXCEPTION'
    WHEN a.status = 'saved' OR a.validated = 1 THEN 'VALIDATED'
    WHEN a.ai_suggestion = 1 AND a.status IN ('drafted', 'submitted') THEN 'AI'
    WHEN a.status = 'submitted' THEN 'SUBMITTED'
    WHEN a.needs_review = 1 THEN 'INPUT'
    ELSE 'DRAFT' END"""

internal const val ASSET_ORDER = "ORDER BY a.created_at DESC, a.id DESC"

internal const val ASSET_BY_ID = "SELECT a.* FROM assets a WHERE a.id = ? AND %LIVE%"

internal const val ASSET_SUMMARY = """
    SELECT $STATUS_SQL AS st, COUNT(*) AS n, COALESCE(SUM(a.ai_suggestion), 0) AS ai, COALESCE(SUM(a.validated), 0) AS ok
    FROM assets a WHERE %WHERE% GROUP BY st"""

internal const val ASSET_VALUES_FOR = "SELECT asset_id, \"values\" FROM asset_values WHERE asset_id IN (%IN%)"

internal const val ASSET_VALUES_ONE = "SELECT * FROM asset_values WHERE asset_id = ?"

internal const val MEDIA_FOR = "SELECT * FROM asset_media WHERE asset_id IN (%IN%) AND %LIVE% ORDER BY position, created_at, id"

internal const val ACTIVITY_FOR = "SELECT * FROM asset_activity WHERE asset_id = ? AND %LIVE% ORDER BY created_at DESC, id DESC"

/** A `WHERE` condition and the values bound to it. */
internal class Where(val sql: String, val args: List<String>)

/** `assets` (alias `a`) rows [filter] keeps. [withStatus] false leaves the status out (for the status chips' counts). */
internal fun AdcDatabase.assetWhere(filter: AssetFilter, withStatus: Boolean = true): Where {
    val parts = mutableListOf(notRemoved("assets", "a"))
    val args = mutableListOf<String>()
    filter.status?.takeIf { withStatus }?.let {
        parts += "($STATUS_SQL) = ?"
        args += it.name
    }
    filter.sources?.let { sources ->
        val db = sources.map(::sourceToDb).distinct()
        parts += if (db.isEmpty()) "1 = 0" else "a.source IN (${placeholders(db.size)})"
        args += db
    }
    if (filter.placeIds.isNotEmpty()) {
        val marks = placeholders(filter.placeIds.size)
        parts += "(a.building_id IN ($marks) OR a.floor_id IN ($marks) OR a.location_id IN ($marks))"
        repeat(3) { args += filter.placeIds }
    }
    if (filter.unmappedOnly) parts += "(a.location_id IS NULL OR a.location_id = '')"
    if (filter.text.isNotBlank()) {
        val like = likeContains(filter.text)
        val inValues = if (hasTable("asset_values")) {
            " OR EXISTS (SELECT 1 FROM asset_values v WHERE v.asset_id = a.id AND v.\"values\" LIKE ? ESCAPE '\\')"
        } else ""
        parts += "(a.name LIKE ? ESCAPE '\\' OR a.manufacturer LIKE ? ESCAPE '\\' OR a.model LIKE ? ESCAPE '\\' " +
            "OR a.serial_number LIKE ? ESCAPE '\\'$inValues)"
        repeat(if (inValues.isEmpty()) 4 else 5) { args += like }
    }
    return Where(parts.joinToString(" AND "), args)
}
