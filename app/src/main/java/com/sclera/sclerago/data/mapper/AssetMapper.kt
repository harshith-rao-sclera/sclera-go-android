package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.sourceFromDb
import com.sclera.sclerago.domain.model.sourceToDb
import com.sclera.sclerago.domain.model.statusFromDb
import com.sclera.sclerago.domain.model.statusToDb
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.typeOf

/** A tagged flag column: a number other than 0, or the text 1, true, yes or tagged (any case); anything else, null or missing is untagged. */
private fun DbRow.isTagFlagSet(column: String): Boolean {
    int(column)?.let { return it != 0 }
    return string(column)?.trim()?.lowercase() in setOf("true", "yes", "y", "tagged")
}

/** Standard fields that are columns of the assets table, as app field key to column. */
private val ASSET_COLUMNS = mapOf("name" to "name", "make" to "manufacturer", "model" to "model", "serial" to "serial_number")

/**
 * The Description field's `assets` column, which the server adds as a built-in field. A database downloaded before has
 * no such column: there Description stays in `asset_values` with the other added fields (check with hasColumn).
 */
const val DESCRIPTION_COLUMN = "description"

/**
 * The server's VdmsState MOVED in the `assets` table's `vdms_state`: Managed Assets (the linked VDMS) took the asset. It
 * is read-only here until Managed Assets sends it back (RETURNING 3 / RETURNED 4, a draft again).
 */
const val VDMS_MOVED = 2

/** The `assets` columns a text search looks in: the API's SEARCHED_COLUMNS (services/values.py). */
private val SEARCHED_COLUMNS = listOf("name", "manufacturer", "model", "serial_number", DESCRIPTION_COLUMN)

/**
 * What the asset list's search looks in ([Asset.searchText]), lowercase: the searched columns of this `assets` row and
 * every answer in [answers] (its `asset_values`), as the API's `q` does.
 */
fun DbRow.searchText(answers: Collection<String>): String =
    (SEARCHED_COLUMNS.mapNotNull { string(it) } + answers)
        .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(SEARCH_SEPARATOR).lowercase()

/** Between the parts of [searchText], so a search can't match across two of them. */
private const val SEARCH_SEPARATOR = "\n"

/** An answer below this confidence is shown as "low confidence" on the details screen. */
private const val LOW_CONFIDENCE = 0.7

/** Where an asset is, as the app shows it: the location's name and "location · floor". */
data class PlaceLabel(val loc: String, val where: String)

/** The asset-table columns and the extra values that [splitAssetValues] makes of a details form. */
data class AssetValueSplit(val columns: Map<String, String>, val extra: Map<String, String>)

/**
 * An `assets` row as the app's [Asset]. [place] and [format] come from the location and master format
 * lookups, [media] from asset_media; all are optional. [missingRequired]: a mandatory field is still empty (makes a drafted asset "Input needed").
 */
fun DbRow.toAsset(
    propertyId: String,
    place: PlaceLabel? = null,
    format: MasterFormat? = null,
    media: List<Media> = emptyList(),
): Asset {
    val name = string("name").orEmpty()
    val validated = bool("validated")
    val aiSuggestion = bool("ai_suggestion")
    val locationId = string("location_id")?.takeIf { it.isNotBlank() }
    val makeModel = listOfNotNull(string("manufacturer"), string("model")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
    return Asset(
        id = string("id").orEmpty(),
        name = name,
        detail = makeModel,
        makeModel = makeModel,
        loc = place?.loc ?: locationId,
        where = place?.where.orEmpty(),
        src = sourceFromDb(string("source")),
        st = statusFromDb(string("status"), validated),
        type = format?.let { typeOf(it.label) }?.takeIf { it != AssetType.GENERIC } ?: typeOf(name),
        propertyId = propertyId,
        media = media,
        updatedAt = parseDbTime(string("updated_at")),
        buildingId = string("building_id")?.takeIf { it.isNotBlank() },
        floorId = string("floor_id")?.takeIf { it.isNotBlank() },
        locationId = locationId,
        masterFormatId = string("master_format_id")?.takeIf { it.isNotBlank() },
        needsReview = bool("needs_review"),
        aiSuggestion = aiSuggestion,
        validated = validated,
        version = int("version") ?: 1,
        changeRequest = string("change_requested_at")?.takeIf { it.isNotBlank() }?.let { at ->
            ChangeRequest(
                string("change_requested_by")?.takeIf { it.isNotBlank() }, parseDbTime(at),
                string("change_request_note")?.takeIf { it.isNotBlank() },
            )
        },
        assignedTo = string("assigned_to")?.takeIf { it.isNotBlank() },
        managed = int("vdms_state") == VDMS_MOVED,
        tagged = buildSet {
            if (isTagFlagSet("qr_code_tag")) add(TagKind.QR)
            if (isTagFlagSet("barcode_tag")) add(TagKind.BARCODE)
            if (isTagFlagSet("nfc_tag")) add(TagKind.NFC)
        },
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
        valuesRow?.let { putAll(DbJson.stringMap(it.answersJson()).mapKeys { (k, _) -> FieldKeys.toApp(k) }) }
        // The Description column once the server has it; an answer kept in asset_values before shows until it is set.
        string(DESCRIPTION_COLUMN)?.takeIf { it.isNotBlank() }?.let { put(DESCRIPTION_COLUMN, it) }
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

/**
 * The answers JSON of an `asset_values` row read whole: `field_values` since the server renamed the column (t0008),
 * `values` in a database downloaded before that.
 */
fun DbRow.answersJson(): String? = string("field_values") ?: string("values")

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
        "needs_review" to needsReview.toInt(),
        "ai_suggestion" to (db.aiSuggestion ?: aiSuggestion).toInt(),
        "validated" to db.validated.toInt(),
        "version" to version,
    )
}

/**
 * Splits a details form into the values that are `assets` columns (name, manufacturer, model, serial number, and
 * [moreColumns] the database has, such as [DESCRIPTION_COLUMN]) and the rest.
 */
fun splitAssetValues(values: Map<String, String>, moreColumns: Set<String> = emptySet()): AssetValueSplit {
    val columnOf = ASSET_COLUMNS + moreColumns.associateWith { it }
    val columns = values.filterKeys { it in columnOf }.mapKeys { (k, _) -> columnOf.getValue(k) }
    val extra = values.filterKeys { it !in columnOf && it !in CLASSIFICATION_KEYS }.mapKeys { (k, _) -> FieldKeys.toDb(k) }
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
