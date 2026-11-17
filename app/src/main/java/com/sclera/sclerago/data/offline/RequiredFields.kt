package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.CORE_FIELD_KEYS
import com.sclera.sclerago.domain.model.missingRequiredKeys

/** The active mandatory fields: the standard ones (no asset) and those one asset carries. */
internal const val REQUIRED_FIELDS = "SELECT key, asset_id, is_standard FROM asset_fields WHERE is_required = 1 AND is_active = 1 AND %LIVE%"

/** The keys of the mandatory fields: those every asset has, and each asset's own. Read once for a whole list of assets. */
internal class RequiredKeys(private val standard: List<String>, private val own: Map<String, List<String>>) {

    /** The mandatory fields of asset [id] ([row] has its core columns) that have no answer ([answers]: its `asset_values.values`, by field key). */
    fun missingFor(id: String, row: DbRow, answers: Map<String, String>): List<String> =
        missingRequiredKeys(standard + own[id].orEmpty(), CORE_FIELD_KEYS.associateWith { row.string(it) }, answers)
}

internal fun AdcDatabase.requiredKeys(): RequiredKeys = whenTable("asset_fields", RequiredKeys(emptyList(), emptyMap())) {
    val rows = query(REQUIRED_FIELDS.replace("%LIVE%", notRemoved("asset_fields")))
    // Standard fields are on every asset; another is only on the asset it names (none: on no asset), as the server.
    val standard = rows.filter { it.bool("is_standard") }
    val own = rows.filter { !it.bool("is_standard") && !it.string("asset_id").isNullOrBlank() }
    RequiredKeys(
        standard.mapNotNull { it.string("key") },
        own.groupBy({ it.string("asset_id").orEmpty() }, { it.string("key").orEmpty() }),
    )
}
