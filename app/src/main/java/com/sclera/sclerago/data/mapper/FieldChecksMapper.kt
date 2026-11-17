package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.TEXT_KINDS
import com.sclera.sclerago.domain.model.ValueRule
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// `asset_fields.checks` as the server stores it (snake_case): {"highlight_empty": true, "format": {"kind", "pattern",
// "min_length", "max_length", "min", "max", "whole_number", "dates"}}. Formats exist for text, number and date fields
// only. The web sends camelCase, so both spellings are read.

/** The server's text format kinds (`checks.format.kind`) and the app's rule for each. */
private val RULE_OF_KIND = mapOf(
    "letters" to ValueRule.LETTERS, "digits" to ValueRule.DIGITS, "alphanumeric" to ValueRule.ALNUM, "email" to ValueRule.EMAIL,
    "phone" to ValueRule.PHONE, "uppercase" to ValueRule.UPPERCASE, "pattern" to ValueRule.PATTERN,
)
private val KIND_OF_RULE = RULE_OF_KIND.entries.associate { (kind, rule) -> rule to kind }


private fun JsonObject.pick(snake: String, camel: String): JsonElement? = get(snake) ?: get(camel)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true || text() == "true"

/** A stored number as typed: 5 rather than 5.0. */
private fun JsonElement?.number(): String = text()?.toDoubleOrNull()?.let(::plainNumber).orEmpty()

private fun plainNumber(d: Double): String = if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

/** [checks] (the column text) as the app's checks for a field of [kind]. */
internal fun readChecks(checks: String?, kind: FieldKind): FieldChecks {
    val root = DbJson.obj(checks)
    val format = root["format"] as? JsonObject ?: JsonObject(emptyMap())
    val flagEmpty = root.pick("highlight_empty", "highlightEmpty").flag()
    return when (kind) {
        in TEXT_KINDS -> {
            val rule = RULE_OF_KIND[format["kind"].text()] ?: ValueRule.NONE
            FieldChecks(
                flagEmpty = flagEmpty,
                rule = rule,
                pattern = if (rule == ValueRule.PATTERN) format["pattern"].text().orEmpty() else "",
                min = format.pick("min_length", "minLength").number(),
                max = format.pick("max_length", "maxLength").number(),
            )
        }
        FieldKind.NUMBER -> FieldChecks(
            flagEmpty = flagEmpty,
            min = format["min"].number(),
            max = format["max"].number(),
            wholeNumber = format.pick("whole_number", "wholeNumber").flag(),
        )
        FieldKind.DATE -> FieldChecks(
            flagEmpty = flagEmpty,
            rule = when (format["dates"].text()) {
                "past" -> ValueRule.PAST
                "future" -> ValueRule.FUTURE
                else -> ValueRule.NONE
            },
        )
        else -> FieldChecks(flagEmpty = flagEmpty)
    }
}

/**
 * [checks] of a field of [kind] as the API takes them (camelCase; unknown keys are refused): `{highlightEmpty?,
 * format?: {kind, pattern, minLength, maxLength} | {min, max, wholeNumber} | {dates}}`. Nothing set is `{}`.
 */
fun checksApiJson(kind: FieldKind, checks: FieldChecks): JsonObject {
    val format = buildJsonObject {
        when (kind) {
            in TEXT_KINDS -> {
                KIND_OF_RULE[checks.rule]?.let { put("kind", it) }
                if (checks.rule == ValueRule.PATTERN) put("pattern", checks.pattern)
                checks.min.trim().toIntOrNull()?.let { put("minLength", it) }
                checks.max.trim().toIntOrNull()?.let { put("maxLength", it) }
            }
            FieldKind.NUMBER -> {
                checks.min.trim().toDoubleOrNull()?.let { putNumber("min", it) }
                checks.max.trim().toDoubleOrNull()?.let { putNumber("max", it) }
                if (checks.wholeNumber) put("wholeNumber", true)
            }
            FieldKind.DATE -> when (checks.rule) {
                ValueRule.PAST -> put("dates", "past")
                ValueRule.FUTURE -> put("dates", "future")
                else -> Unit
            }
            else -> Unit
        }
    }
    return buildJsonObject {
        if (checks.flagEmpty) put("highlightEmpty", true)
        if (format.isNotEmpty()) put("format", format)
    }
}

/** Whole numbers as integers (5, not 5.0), as the web sends them. */
private fun kotlinx.serialization.json.JsonObjectBuilder.putNumber(key: String, value: Double) {
    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) put(key, value.toLong()) else put(key, value)
}
