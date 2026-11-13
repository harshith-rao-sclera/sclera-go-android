package com.sclera.sclerago.data.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** Reads and writes the JSON text columns (values, confidence, options, checks, rules, ...). Bad JSON reads as empty. */
internal object DbJson {
    private fun parse(text: String?): JsonElement? =
        text?.takeIf { it.isNotBlank() }?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }

    fun obj(text: String?): JsonObject = parse(text) as? JsonObject ?: JsonObject(emptyMap())

    /** Object as key to text; an array value (multiple checkbox) becomes comma separated text; nulls are left out. */
    fun stringMap(text: String?): Map<String, String> = obj(text).mapNotNull { (k, v) -> text(v)?.let { k to it } }.toMap()

    fun doubleMap(text: String?): Map<String, Double> =
        obj(text).mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }.toMap()

    /** An array as texts; objects in it use their label, name or value. */
    fun stringList(text: String?): List<String> = (parse(text) as? JsonArray).orEmpty().mapNotNull(::text)

    /** Object of objects of numbers, as key to key to whole number (rule_stats). */
    fun intMaps(text: String?): Map<String, Map<String, Int>> = obj(text).mapNotNull { (k, v) ->
        (v as? JsonObject)?.let { inner ->
            k to inner.mapNotNull { (name, n) -> (n as? JsonPrimitive)?.doubleOrNull?.toInt()?.let { name to it } }.toMap()
        }
    }.toMap()

    fun text(v: JsonElement): String? = when (v) {
        is JsonNull -> null
        is JsonPrimitive -> v.contentOrNull
        is JsonArray -> v.mapNotNull(::text).joinToString(",")
        is JsonObject -> listOf("label", "name", "value").firstNotNullOfOrNull { (v[it] as? JsonPrimitive)?.contentOrNull }
    }

    fun write(values: Map<String, String>): String = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }.toString()

    fun writeList(values: List<String>): String = JsonArray(values.map { JsonPrimitive(it) }).toString()

    fun writeDoubles(values: Map<String, Double>): String = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }.toString()
}
