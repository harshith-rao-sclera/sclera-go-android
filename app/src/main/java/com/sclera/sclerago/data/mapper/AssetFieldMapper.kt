package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.OPTION_KINDS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    val checks = readChecks(string("checks"), kind)
    return FieldCfg(
        key = key,
        label = string("name").orEmpty(),
        kind = kind,
        section = SECTION_BY_KEY[key] ?: FieldSection.MORE,
        visible = bool("is_active"),
        required = bool("is_required"),
        locked = key == "name",
        custom = !bool("is_builtin"),
        options = DbJson.stringList(string("options")),
        defaultValue = string("default_value").orEmpty(),
        tooltip = string("help_text").orEmpty(),
        flagEmpty = checks.flagEmpty,
        rule = checks.rule,
        min = checks.min,
        max = checks.max,
    )
}

/** A `master_formats` row as the app's [MasterFormat]. */
fun DbRow.toMasterFormat() = MasterFormat(
    id = string("id").orEmpty(),
    systemType = string("system_type").orEmpty(),
    assetType = string("asset_type")?.takeIf { it.isNotBlank() },
    subtype = string("subtype")?.takeIf { it.isNotBlank() },
)

/** The `asset_fields.type` text of a field kind. */
fun FieldKind.toDbType(): String = TYPE_BY_KIND.getValue(this)

/**
 * The `options` column text for a field's choices: a list of `{label}` as the server writes them; null when it has none.
 * A choice that was already in [existing] keeps what the server stored with it (its colour).
 */
fun FieldCfg.optionsJson(existing: String? = null): String? {
    if (kind !in OPTION_KINDS || options.isEmpty()) return null
    val old = (runCatching { Json.parseToJsonElement(existing.orEmpty()) }.getOrNull() as? JsonArray).orEmpty()
        .mapNotNull { it as? JsonObject }.associateBy { (it["label"] as? JsonPrimitive)?.contentOrNull }
    return JsonArray(options.map { label -> old[label] ?: buildJsonObject { put("label", label) } }).toString()
}

