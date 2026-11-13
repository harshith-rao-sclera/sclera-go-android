package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.sourceFromDb
import com.sclera.sclerago.domain.model.sourceToDb
import com.sclera.sclerago.domain.model.statusFromDb
import com.sclera.sclerago.domain.model.statusToDb
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.typeOf

/** Standard fields that are columns of the assets table, as app field key to column. */
private val ASSET_COLUMNS = mapOf("name" to "name", "make" to "manufacturer", "model" to "model", "serial" to "serial_number")

/** An answer below this confidence is shown as "low confidence" on the details screen. */
private const val LOW_CONFIDENCE = 0.7

/** Where an asset is, as the app shows it: the location's name and "location · floor". */
data class PlaceLabel(val loc: String, val where: String)

/** The asset-table columns and the extra values that [splitAssetValues] makes of a details form. */
data class AssetValueSplit(val columns: Map<String, String>, val extra: Map<String, String>)

/**
 * An `assets` row as the app's [Asset]. [place] and [format] come from the location and master format
 * lookups, [media] from asset_media; all are optional.
 */
fun DbRow.toAsset(
    propertyId: String,
    place: PlaceLabel? = null,
    format: MasterFormat? = null,
    media: List<Media> = emptyList(),
): Asset {
    val name = string("name").orEmpty()
    val needsReview = bool("needs_review")
    val validated = bool("validated")
    val aiSuggestion = bool("ai_suggestion")
    val locationId = string("location_id")?.takeIf { it.isNotBlank() }
    return Asset(
        id = string("id").orEmpty(),
        name = name,
        detail = listOfNotNull(string("manufacturer"), string("model")).filter { it.isNotBlank() }.joinToString(" · "),
        loc = place?.loc ?: locationId,
        where = place?.where.orEmpty(),
        src = sourceFromDb(string("source")),
        st = statusFromDb(string("status"), needsReview, validated, aiSuggestion),
        type = format?.let { typeOf(it.label) }?.takeIf { it != AssetType.GENERIC } ?: typeOf(name),
        propertyId = propertyId,
        media = media,
        updatedAt = parseDbTime(string("updated_at")),
        buildingId = string("building_id")?.takeIf { it.isNotBlank() },
        floorId = string("floor_id")?.takeIf { it.isNotBlank() },
        locationId = locationId,
        masterFormatId = string("master_format_id")?.takeIf { it.isNotBlank() },
        needsReview = needsReview,
        aiSuggestion = aiSuggestion,
        validated = validated,
        version = int("version") ?: 1,
    )
}

/**
 * The details form values of an `assets` row: the standard columns, the JACS code, the type from [format],
 * and every answer in [valuesRow] (an `asset_values` row), all under the app's field keys.
 */
fun DbRow.toAssetDetails(valuesRow: DbRow? = null, format: MasterFormat? = null): AssetDetails {
    val fields = buildMap {
        ASSET_COLUMNS.forEach { (key, column) -> string(column)?.let { put(key, it) } }
        string("jacs_code_id")?.let { put("jacs", it) }
        format?.let {
            put("sys", it.systemType)
            it.assetType?.let { t -> put("typ", t) }
            it.subtype?.let { t -> put("sub", t) }
        }
        valuesRow?.let { putAll(DbJson.stringMap(it.string("values")).mapKeys { (k, _) -> FieldKeys.toApp(k) }) }
    }
    val confidence = DbJson.doubleMap(valuesRow?.string("confidence")).mapKeys { (k, _) -> FieldKeys.toApp(k) }
    val sources = DbJson.stringMap(valuesRow?.string("sources")).mapKeys { (k, _) -> FieldKeys.toApp(k) }
    return AssetDetails(
        code = fields["label"].orEmpty(),
        fields = fields,
        lowConfidence = confidence.filterValues { it < LOW_CONFIDENCE }.minByOrNull { it.value }?.key,
        confidence = confidence,
        sources = sources,
    )
}

/** The `assets` columns to insert or update for [this] (timestamps are left to the caller). */
fun Asset.toAssetRow(): Map<String, Any?> {
    val db = statusToDb(st)
    return mapOf(
        "id" to id,
        "name" to name,
        "building_id" to buildingId,
        "floor_id" to floorId,
        "location_id" to locationId,
        "status" to db.status,
        "source" to sourceToDb(src),
        "master_format_id" to masterFormatId,
        "needs_review" to db.needsReview.toInt(),
        "ai_suggestion" to (db.aiSuggestion ?: aiSuggestion).toInt(),
        "validated" to db.validated.toInt(),
        "version" to version,
    )
}

/** Splits a details form into the values that are `assets` columns (name, manufacturer, model, serial number) and the rest. */
fun splitAssetValues(values: Map<String, String>): AssetValueSplit {
    val columns = values.filterKeys { it in ASSET_COLUMNS }.mapKeys { (k, _) -> ASSET_COLUMNS.getValue(k) }
    val extra = values.filterKeys { it !in ASSET_COLUMNS && it !in CLASSIFICATION_KEYS }.mapKeys { (k, _) -> FieldKeys.toDb(k) }
    return AssetValueSplit(columns, extra)
}

/** The `asset_values` columns for one asset's extra [values] (confidence and sources are kept as they were). */
fun assetValuesRow(
    assetId: String,
    values: Map<String, String>,
    confidence: Map<String, Double> = emptyMap(),
    sources: Map<String, String> = emptyMap(),
): Map<String, Any?> = mapOf(
    "asset_id" to assetId,
    "values" to DbJson.write(values),
    "confidence" to DbJson.writeDoubles(confidence.mapKeys { (k, _) -> FieldKeys.toDb(k) }),
    "sources" to DbJson.write(sources.mapKeys { (k, _) -> FieldKeys.toDb(k) }),
)

/** Form keys stored as other columns (the type comes from the master format, the JACS code has its own column). */
private val CLASSIFICATION_KEYS = setOf("sys", "typ", "sub", "jacs")

private fun Boolean.toInt() = if (this) 1 else 0
