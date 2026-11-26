package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.ASSET_DETAIL_COLUMNS
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.assetDetailValue

// The asset's Type and Location tag (`assets.type` and `assets.location_tag`, the server's t0030): written as the
// server's POST and PATCH /assets write them, and named in history as its activity.changes_since does.

/** How history names each column (the server's DETAIL_LABELS). */
private val DETAIL_LABELS = mapOf("type" to "Type", "location_tag" to "Location tag")

/** One of the asset's Type and Location tag that an edit changes: its [column], history [label], and values (null: none). */
internal class DetailEdit(val column: String, val label: String, val old: String?, val new: String?)

/**
 * What the details form [values] change of asset [id]'s Type and Location tag ([id] null: a new asset, which has none
 * yet), as the server's PATCH: a key left out leaves the column as it is, an empty value clears it, and a value the server
 * doesn't take is left out. A database synced before the server had the columns can't keep them: nothing changes.
 */
internal fun AdcDatabase.detailEdits(id: String?, values: Map<String, String>): List<DetailEdit> {
    val columns = ASSET_DETAIL_COLUMNS.filter { (key, column) -> key in values && hasColumn("assets", column) }
    if (columns.isEmpty()) return emptyList()
    val row = id?.let { queryOne("SELECT ${columns.values.joinToString()} FROM assets WHERE id = ?", listOf(it)) }
    return columns.mapNotNull { (key, column) ->
        val text = values.getValue(key).trim()
        val new = if (text.isEmpty()) null else assetDetailValue(key, text) ?: return@mapNotNull null
        val old = row?.string(column)?.takeIf { it.isNotBlank() }
        if (new == old) null else DetailEdit(column, DETAIL_LABELS.getValue(column), old, new)
    }
}

/**
 * Writes [edits] to asset [id]'s columns. With [now] nothing else of the asset changed, so its version and time go up
 * here (a Type or Location tag isn't an answer: no new values revision, as on the server).
 */
internal fun AdcDatabase.writeDetails(id: String, edits: List<DetailEdit>, now: String? = null) {
    if (edits.isEmpty()) return
    val sets = edits.joinToString { "${it.column} = ?" } + if (now != null) ", version = version + 1, updated_at = ?" else ""
    execute("UPDATE assets SET $sets WHERE id = ?", edits.map { it.new } + listOfNotNull(now) + id, setOf("assets"))
}

/** A Type or Location tag as history shows it, the server's detail_text: "Existing", "Wall: North"; null for none. */
internal fun detailText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    ?.split(":", limit = 2)?.joinToString(": ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
