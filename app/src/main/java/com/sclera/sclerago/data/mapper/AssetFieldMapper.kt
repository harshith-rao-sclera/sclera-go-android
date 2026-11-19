package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.fieldKindOf
import com.sclera.sclerago.domain.model.serverType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal val SECTION_BY_KEY = mapOf(
    "name" to FieldSection.IDENT, "make" to FieldSection.IDENT, "model" to FieldSection.IDENT,
    "serial" to FieldSection.IDENT, "power" to FieldSection.IDENT, "voltage" to FieldSection.IDENT,
    "speed" to FieldSection.IDENT,
    "sys" to FieldSection.CLASS, "typ" to FieldSection.CLASS, "sub" to FieldSection.CLASS, "jacs" to FieldSection.CLASS,
    "label" to FieldSection.TAGS,
    "note" to FieldSection.NOTES,
)

/**
 * An `asset_fields` row as the app's [FieldCfg], or null for kinds the app has no input for
 * (image, video, voice memo, key value). Keys use the app's names (manufacturer is "make").
 */
fun DbRow.toFieldCfg(): FieldCfg? {
    val kind = fieldKindOf(string("type")) ?: return null
    val key = FieldKeys.toApp(string("key").orEmpty())
    return FieldCfg(
        key = key,
        label = string("name").orEmpty(),
        kind = kind,
        section = SECTION_BY_KEY[key] ?: FieldSection.MORE,
        visible = bool("is_active"),
        required = bool("is_required"),
        locked = key == "name",
        custom = !bool("is_builtin"),
        ownField = !string("asset_id").isNullOrBlank(),
        options = DbJson.stringList(string("options")),
        defaultValue = string("default_value").orEmpty(),
        tooltip = string("help_text").orEmpty(),
    ).withChecks(readChecks(string("checks"), kind))
}

/** A `master_formats` row as the app's [MasterFormat]. */
fun DbRow.toMasterFormat() = MasterFormat(
    id = string("id").orEmpty(),
    systemType = string("system_type").orEmpty(),
    assetType = string("asset_type")?.takeIf { it.isNotBlank() },
    subtype = string("subtype")?.takeIf { it.isNotBlank() },
)
