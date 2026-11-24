package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.answersJson
import com.sclera.sclerago.data.sync.AdcDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// The server's `assets.ai_suggestion`: whether AI suggestions (published specs) could fill any of the asset's empty
// fields (its services/assets.py refresh_ai_suggestion, with extraction/lookup.py can_look_up and IDENTITY_KEYS).

/** The asset's own fields: standard ones and those of this asset (not the types' field rows, which have no asset). */
private const val AI_FIELDS = """SELECT key, name, type, is_active, is_builtin FROM asset_fields
    WHERE (is_standard = 1 OR asset_id = ?) AND %LIVE%"""

private const val AI_CORE = "SELECT name, manufacturer, model, serial_number FROM assets WHERE id = ?"

/** Searched for by these, so they can't be filled from specs. */
private val IDENTITY_KEYS = setOf("manufacturer", "model", "name")

/** A datasheet can't fill these: media, site observations, free-form specs, multi-selects and what this asset cost. */
private val NOT_FROM_DATASHEETS = setOf("image", "video", "voice_memo", "key_value", "checkbox", "yesno", "multiple_checkbox", "currency")

/**
 * The built-in Type field (key "type", or "type_2"… where the server's migration found "type" taken): what the
 * collector calls the asset, not a spec, so it is never looked up (the server's can_look_up).
 */
private fun isBuiltInType(key: String, builtIn: Boolean) = builtIn && key.substringBefore('_') == "type"

/** Words of a field's key or name that describe one physical unit or its site, not the model. */
private val UNIT_WORDS = listOf(
    "serial", "tag", "barcode", "qr", "nfc", "install", "commission", "service", "inspect",
    "condition", "location", "room", "floor", "building", "note", "comment", "photo", "image",
)

/** The answers kept in `assets` columns rather than in `asset_values`. */
private val CORE_COLUMNS = setOf("name", "manufacturer", "model", "serial_number")

private val WORD_SPLIT = Regex("[^a-z0-9]+")

/** Whether AI suggestions could fill one of asset [assetId]'s empty fields (what its `ai_suggestion` flag says). */
internal fun AdcDatabase.aiFillable(assetId: String): Boolean {
    if (!hasTable("asset_fields")) return false
    val core = queryOne(AI_CORE, listOf(assetId))
    val answers = DbJson.obj(queryOne(ASSET_VALUES_ROW, listOf(assetId))?.answersJson())
    return query(AI_FIELDS.replace("%LIVE%", notRemoved("asset_fields")), listOf(assetId)).any { f ->
        val key = f.string("key").orEmpty()
        val name = f.string("name").orEmpty()
        val lookable = f.bool("is_active") && f.string("type") !in NOT_FROM_DATASHEETS && key !in IDENTITY_KEYS &&
            !isBuiltInType(key, f.bool("is_builtin")) &&
            WORD_SPLIT.split("$key $name".lowercase()).filter { it.isNotEmpty() }.none { w -> UNIT_WORDS.any { w.startsWith(it) } }
        val empty = if (key in CORE_COLUMNS) core?.string(key).isNullOrBlank() else isEmpty(answers[key])
        lookable && empty
    }
}

/** The server's is_empty: nothing, blank text or an empty list. */
private fun isEmpty(v: JsonElement?): Boolean = when (v) {
    null, is JsonNull -> true
    is JsonPrimitive -> v.isString && v.content.isBlank()
    is JsonArray -> v.isEmpty()
    else -> false
}
