package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
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

/** What a field's checks say, in the app's terms. */
internal class ChecksView(val flagEmpty: Boolean, val rule: ValueRule, val min: String, val max: String)

private const val NO_SPACES_PATTERN = "^\\S+$"

private val RULE_OF_KIND = mapOf("alphanumeric" to ValueRule.ALNUM, "digits" to ValueRule.DIGITS, "uppercase" to ValueRule.CODE, "email" to ValueRule.EMAIL)
private val KIND_OF_RULE = RULE_OF_KIND.entries.associate { (kind, rule) -> rule to kind }

/** The keys of a `checks.format` that a [FieldCfg] sets itself (both spellings); the others the server keeps are left alone. */
private val OWN_KEYS = setOf(
    "dates", "min", "max", "min_length", "max_length", "minLength", "maxLength", "whole_number", "wholeNumber", "kind", "pattern",
)

private fun JsonObject.pick(snake: String, camel: String): JsonElement? = get(snake) ?: get(camel)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true || text() == "true"

private fun isText(kind: FieldKind) = kind == FieldKind.TEXT || kind == FieldKind.TEXTAREA

/** [checks] (the column text) as the app shows it for a field of [kind]. */
internal fun readChecks(checks: String?, kind: FieldKind): ChecksView {
    val root = DbJson.obj(checks)
    val format = root["format"] as? JsonObject ?: JsonObject(emptyMap())
    val rule = when {
        kind == FieldKind.DATE -> when (format["dates"].text()) {
            "past" -> ValueRule.PAST
            "future" -> ValueRule.FUTURE
            else -> ValueRule.NONE
        }
        kind == FieldKind.NUMBER -> if (format.pick("whole_number", "wholeNumber").flag()) ValueRule.WHOLE else ValueRule.NONE
        isText(kind) -> when {
            format["kind"].text() == "pattern" && format["pattern"].text() == NO_SPACES_PATTERN -> ValueRule.NO_SPACES
            else -> RULE_OF_KIND[format["kind"].text()] ?: ValueRule.NONE
        }
        else -> ValueRule.NONE
    }
    val (min, max) = when {
        isText(kind) -> format.pick("min_length", "minLength").text().orEmpty() to format.pick("max_length", "maxLength").text().orEmpty()
        kind == FieldKind.NUMBER -> format["min"].text().orEmpty() to format["max"].text().orEmpty()
        else -> "" to ""
    }
    return ChecksView(root.pick("highlight_empty", "highlightEmpty").flag(), rule, min, max)
}

/**
 * The `checks` column text for this field: [existing] with the highlight-empty flag and the format set from the field
 * (text: kind and length limits; number: limits and whole numbers; date: past or future). Other checks the server keeps
 * (a format kind the app has no rule for, a pattern) stay as they are unless this field now sets its own rule.
 */
fun FieldCfg.mergeChecks(existing: String?): String {
    val old = DbJson.obj(existing)
    val oldFormat = old["format"] as? JsonObject ?: JsonObject(emptyMap())
    val format = buildJsonObject {
        if (kind in FORMAT_KINDS) oldFormat.filterKeys { it !in OWN_KEYS }.forEach { (k, v) -> put(k, v) }
        else oldFormat.forEach { (k, v) -> put(k, v) }
        when {
            isText(kind) -> {
                when {
                    rule == ValueRule.NO_SPACES -> { put("kind", "pattern"); put("pattern", NO_SPACES_PATTERN) }
                    rule in KIND_OF_RULE -> put("kind", KIND_OF_RULE.getValue(rule))
                    else -> oldFormat.entries.filter { it.key == "kind" || it.key == "pattern" }.forEach { (k, v) -> put(k, v) }
                }
                min.toDoubleOrNull()?.let { put("min_length", it.toInt()) }
                max.toDoubleOrNull()?.let { put("max_length", it.toInt()) }
            }
            kind == FieldKind.NUMBER -> {
                if (rule == ValueRule.WHOLE) put("whole_number", true)
                val low = min.toDoubleOrNull() ?: if (rule == ValueRule.POSITIVE) 0.0 else null
                low?.let { put("min", if (rule == ValueRule.POSITIVE) maxOf(it, 0.0) else it) }
                max.toDoubleOrNull()?.let { put("max", it) }
            }
            kind == FieldKind.DATE -> when (rule) {
                ValueRule.PAST -> put("dates", "past")
                ValueRule.FUTURE -> put("dates", "future")
                else -> Unit
            }
        }
    }
    return buildJsonObject {
        old.filterKeys { it != "format" && it != "highlight_empty" && it != "highlightEmpty" }.forEach { (k, v) -> put(k, v) }
        if (flagEmpty) put("highlight_empty", true)
        if (format.isNotEmpty()) put("format", format)
    }.toString()
}

private val FORMAT_KINDS = setOf(FieldKind.TEXT, FieldKind.TEXTAREA, FieldKind.NUMBER, FieldKind.DATE)
