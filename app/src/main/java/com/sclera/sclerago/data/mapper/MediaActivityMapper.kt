package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.FieldChange
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An `asset_media` row as [Media]. [uri] is where the file can be opened (a local file or a signed link);
 * it defaults to the stored object path. Documents and audio keep their file name.
 */
fun DbRow.toMedia(uri: String? = null): Media {
    val kind = string("kind")
    val isFile = kind == "document" || kind == "audio"
    return Media(
        id = string("id").orEmpty(),
        uri = uri ?: string("object_path").orEmpty(),
        video = kind == "video",
        durationMs = ((double("duration_seconds") ?: 0.0) * 1000).toLong(),
        createdAt = parseDbTime(string("created_at")) ?: 0L,
        fileName = if (isFile) string("filename") else null,
        category = PhotoKind.of(string("category")),
        fieldId = string("field_id")?.takeIf { it.isNotBlank() },
        audio = kind == "audio",
        sizeBytes = string("size_bytes")?.toDoubleOrNull()?.toLong(),
    )
}

/** An `asset_activity` row as an [AssetActivity]. */
fun DbRow.toAssetActivity(fieldTypes: Map<String, String> = emptyMap()) = AssetActivity(
    id = string("id").orEmpty(),
    assetId = string("asset_id").orEmpty(),
    kind = if (string("kind") == "comment") ActivityKind.COMMENT else ActivityKind.HISTORY,
    action = string("action"),
    text = string("text").orEmpty(),
    changes = historyChanges(),
    createdBy = string("created_by"),
    createdAt = parseDbTime(string("created_at")),
    fieldChanges = fieldChanges(historyChanges(), fieldTypes),
    // As the server: a comment whose row moved past its first version was changed (not the timestamps: clocks step back).
    edited = (int("version") ?: 1) > 1,
)

/**
 * A history line's field changes (`[{field, fieldId, from, to}]`): `field_changes`, as the server now calls it, or in a
 * database from before the rename `changes` when it holds that list (it is otherwise the sync engine's own column).
 */
private fun DbRow.historyChanges(): String? =
    string("field_changes") ?: string("changes")?.takeIf { it.trimStart().startsWith("[") }

/** A history line's `changes` JSON (`[{field, fieldId, from, to}]`, values of any JSON type) as text changes. */
internal fun fieldChanges(json: String?, fieldTypes: Map<String, String> = emptyMap()): List<FieldChange> =
    (runCatching { Json.parseToJsonElement(json.orEmpty()) }.getOrNull() as? JsonArray).orEmpty().mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val field = (o["field"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val id = (o["fieldId"] as? JsonPrimitive)?.contentOrNull
        val type = (o["type"] as? JsonPrimitive)?.contentOrNull ?: fieldTypes[id]
        FieldChange(field, valueText(o["from"]), valueText(o["to"]), type)
    }

/** A changed value as text: a list joined with commas, null for empty. */
private fun valueText(e: JsonElement?): String? = when (e) {
    null, is JsonNull -> null
    is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotBlank() }
    is JsonArray -> e.mapNotNull { valueText(it) }.joinToString(", ").takeIf { it.isNotBlank() }
    is JsonObject -> e.toString()
}
