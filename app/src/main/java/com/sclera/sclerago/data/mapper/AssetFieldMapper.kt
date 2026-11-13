package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.ValueRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private val KIND_BY_TYPE = mapOf(
    "text" to FieldKind.TEXT, "textarea" to FieldKind.TEXTAREA, "number" to FieldKind.NUMBER, "url" to FieldKind.URL,
    "date" to FieldKind.DATE, "time" to FieldKind.TIME, "dropdown" to FieldKind.DROPDOWN,
    "multiple_checkbox" to FieldKind.MULTI, "checkbox" to FieldKind.CHECKBOX, "yesno" to FieldKind.YESNO,
)
private val TYPE_BY_KIND = KIND_BY_TYPE.entries.associate { (type, kind) -> kind to type }

private val SECTION_BY_KEY = mapOf(
    "name" to FieldSection.IDENT, "make" to FieldSection.IDENT, "model" to FieldSection.IDENT,
    "serial" to FieldSection.IDENT, "power" to FieldSection.IDENT, "voltage" to FieldSection.IDENT,
    "speed" to FieldSection.IDENT,
    "sys" to FieldSection.CLASS, "typ" to FieldSection.CLASS, "sub" to FieldSection.CLASS, "jacs" to FieldSection.CLASS,
    "label" to FieldSection.TAGS, "ble" to FieldSection.TAGS, "ip" to FieldSection.TAGS,
    "note" to FieldSection.NOTES,
)

/**
 * An `asset_fields` row as the app's [FieldCfg], or null for kinds the app has no input for
 * (image, video, voice memo, key value). Keys use the app's names (manufacturer is "make").
 */
fun DbRow.toFieldCfg(): FieldCfg? {
    val kind = KIND_BY_TYPE[string("type")] ?: return null
    val key = FieldKeys.toApp(string("key").orEmpty())
    val checks = DbJson.obj(string("checks"))
    val format = checks["format"] as? JsonObject
    val dates = (format?.get("dates") as? JsonPrimitive)?.contentOrNull
    return FieldCfg(
        key = key,
        label = string("name").orEmpty(),
        kind = kind,
        section = SECTION_BY_KEY[key] ?: FieldSection.MORE,
        visible = bool("is_active"),
        required = bool("is_required"),
        locked = key == "name",
        custom = !bool("is_builtin") && int("is_standard") != 1,
        options = DbJson.stringList(string("options")),
        defaultValue = string("default_value").orEmpty(),
        tooltip = string("help_text").orEmpty(),
        flagEmpty = (checks["highlight_empty"] as? JsonPrimitive)?.contentOrNull == "true",
        rule = when (dates) {
            "past" -> ValueRule.PAST
            "future" -> ValueRule.FUTURE
            else -> ValueRule.NONE
        },
        min = (format?.get("min") as? JsonPrimitive)?.contentOrNull.orEmpty(),
        max = (format?.get("max") as? JsonPrimitive)?.contentOrNull.orEmpty(),
    )
}

/** A `master_formats` row as the app's [MasterFormat]. */
fun DbRow.toMasterFormat() = MasterFormat(
    id = string("id").orEmpty(),
    systemType = string("system_type").orEmpty(),
    assetType = string("asset_type")?.takeIf { it.isNotBlank() },
    subtype = string("subtype")?.takeIf { it.isNotBlank() },
)

/** The `asset_fields` columns to insert or update for [this]; [id] is the row id and [position] its order. */
fun FieldCfg.toFieldRow(id: String, position: Int, version: Int = 1): Map<String, Any?> = mapOf(
    "id" to id,
    "key" to FieldKeys.toDb(key),
    "name" to label,
    "type" to TYPE_BY_KIND.getValue(kind),
    "options" to options.takeIf { it.isNotEmpty() }?.let(DbJson::writeList),
    "is_required" to if (required) 1 else 0,
    "is_active" to if (visible) 1 else 0,
    "position" to position,
    "help_text" to tooltip.ifBlank { null },
    "default_value" to defaultValue.ifBlank { null },
    "checks" to checksJson(),
    "version" to version,
)

private fun FieldCfg.checksJson(): String = buildJsonObject {
    if (flagEmpty) put("highlight_empty", true)
    val dates = when (rule) {
        ValueRule.PAST -> "past"
        ValueRule.FUTURE -> "future"
        else -> null
    }
    if (dates != null || min.isNotBlank() || max.isNotBlank()) {
        put("format", buildJsonObject {
            dates?.let { put("dates", it) }
            min.toDoubleOrNull()?.let { put("min", it) }
            max.toDoubleOrNull()?.let { put("max", it) }
        })
    }
}.toString()
