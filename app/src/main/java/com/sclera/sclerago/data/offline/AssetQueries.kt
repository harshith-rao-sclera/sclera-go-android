package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.AssetQueryFilter
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.sourceToDb

/** The app's status as SQL, the same rules as `statusFromDb` (first match wins) except that "Input needed" cannot be told in SQL: a drafted asset is DRAFT here, and the ones with a mandatory field empty are found in code (see `RequiredKeys`). Names are the [com.sclera.sclerago.domain.model.Status] names. */
internal const val STATUS_SQL = """CASE
    WHEN a.status = 'exception' THEN 'EXCEPTION'
    WHEN a.status = 'saved' OR a.validated = 1 THEN 'VALIDATED'
    WHEN a.status = 'submitted' THEN 'SUBMITTED'
    ELSE 'DRAFT' END"""

internal const val ASSET_ORDER = "ORDER BY a.created_at DESC, a.id DESC"

internal const val ASSET_BY_ID = "SELECT a.* FROM assets a WHERE a.id = ? AND %LIVE%"

internal const val ASSET_SUMMARY = """
    SELECT $STATUS_SQL AS st, COUNT(*) AS n, COALESCE(SUM(a.ai_suggestion), 0) AS ai, COALESCE(SUM(a.validated), 0) AS ok
    FROM assets a WHERE %WHERE% GROUP BY st"""

internal const val ASSET_VALUES_FOR = "SELECT asset_id, %ANSWERS% AS answers FROM asset_values WHERE asset_id IN (%IN%)"

internal const val ASSETS_BY_IDS = "SELECT a.* FROM assets a WHERE a.id IN (%IN%) AND %LIVE%"

internal const val ASSET_VALUES_ROWS = "SELECT * FROM asset_values WHERE asset_id IN (%IN%)"

internal const val ASSET_VALUES_ONE = "SELECT * FROM asset_values WHERE asset_id = ?"

/**
 * The decisions reviewers made on the assets (results nobody decided on are left out, as the server's recollect rule
 * does), newest decision first per asset (the first row of each asset is its latest); `%DECIDED%` is when it was decided.
 */
internal const val LATEST_DECISIONS =
    """SELECT r.asset_id, r.decision FROM validation_results r WHERE r.asset_id IN (%IN%) AND r.decision IS NOT NULL
    AND %LIVE% ORDER BY %DECIDED% DESC, r.id DESC"""

internal const val MEDIA_FOR = "SELECT * FROM asset_media WHERE asset_id IN (%IN%) AND %LIVE% ORDER BY position, created_at, id"

internal const val ACTIVITY_FOR = "SELECT * FROM asset_activity WHERE asset_id = ? AND %LIVE% ORDER BY created_at DESC, id DESC"

/** A `WHERE` condition and the values bound to it. */
internal class Where(val sql: String, val args: List<String>)

/** `assets` (alias `a`) rows [filter] keeps. [withStatus] false leaves the status out (for the status chips' counts). */
internal fun AdcDatabase.assetWhere(filter: AssetQueryFilter, withStatus: Boolean = true): Where {
    // In ADC (not removed, not in Managed Assets) and not a file import still waiting for review: what the server lists.
    val parts = mutableListOf(listedAssets("a"))
    val args = mutableListOf<String>()
    filter.status?.takeIf { withStatus }?.let {
        // "AI suggestion" goes by the flag whatever the status, as the server's aiFillable count does (see Asset.isIn).
        if (it == Status.AI) {
            parts += "a.ai_suggestion = 1"
        } else {
            parts += "($STATUS_SQL) = ?"
            args += (if (it == Status.INPUT) Status.DRAFT else it).name
        }
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
            " OR EXISTS (SELECT 1 FROM asset_values v WHERE v.asset_id = a.id AND v.${answersColumn()} LIKE ? ESCAPE '\\')"
        } else ""
        parts += "(a.name LIKE ? ESCAPE '\\' OR a.manufacturer LIKE ? ESCAPE '\\' OR a.model LIKE ? ESCAPE '\\' " +
            "OR a.serial_number LIKE ? ESCAPE '\\'$inValues)"
        repeat(if (inValues.isEmpty()) 4 else 5) { args += like }
    }
    return Where(parts.joinToString(" AND "), args)
}
