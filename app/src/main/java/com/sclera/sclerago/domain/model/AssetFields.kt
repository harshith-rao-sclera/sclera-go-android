package com.sclera.sclerago.domain.model

/** What kind of input an asset field takes. */
enum class FieldKind(val label: String) {
    TEXT("Text"), CHECKBOX("Check Box"), MULTI("Multiple Check Boxes"), YESNO("Yes/No"), DATE("Date"), TIME("Time"),
    NUMBER("Number"), URL("URL"), DROPDOWN("Dropdown"), TEXTAREA("Textarea"),
}

/** Kinds whose values come from a list of options. */
val OPTION_KINDS = setOf(FieldKind.DROPDOWN, FieldKind.MULTI)

/** Display formats for date and time fields ("" = no format). */
val DATE_DISPLAY = listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD", "MMM YYYY")
val TIME_DISPLAY = listOf("24-hour", "12-hour")

/** Where a field sits on the asset details form. */
enum class FieldSection(val title: String) { IDENT("Identification"), CLASS("Classification"), TAGS("Tags & connectivity"), MORE("More details"), NOTES("Notes") }

/**
 * What a field's values must be, beyond its kind (Configuration → Asset fields → Checks).
 * [kinds] are the field kinds it applies to.
 */
enum class ValueRule(val label: String, val kinds: Set<FieldKind>) {
    NONE("None", FieldKind.entries.toSet()),
    ALNUM("Letters and numbers only", setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    DIGITS("Digits only", setOf(FieldKind.TEXT)),
    CODE("Capitals, digits and dashes", setOf(FieldKind.TEXT)),
    NO_SPACES("No spaces", setOf(FieldKind.TEXT)),
    EMAIL("An email address", setOf(FieldKind.TEXT)),
    WHOLE("Whole numbers", setOf(FieldKind.NUMBER)),
    POSITIVE("Above zero", setOf(FieldKind.NUMBER)),
    PAST("Not in the future", setOf(FieldKind.DATE)),
    FUTURE("In the future", setOf(FieldKind.DATE)),
}

/** What "no rule" is called for [kind]. */
fun noRuleLabel(kind: FieldKind) = when (kind) {
    FieldKind.NUMBER -> "None (any number)"
    FieldKind.DATE -> "None (any date)"
    else -> "None (any text)"
}

/**
 * One asset field as configured in Configuration → Asset fields: whether field workers see it,
 * whether it must be filled, what it starts with, and what validation checks in it.
 * [locked] fields (the name) are always shown and mandatory. Mandatory fields are always visible.
 */
data class FieldCfg(
    val key: String,
    val label: String,
    val kind: FieldKind,
    val section: FieldSection,
    val visible: Boolean = true,
    val required: Boolean = false,
    val locked: Boolean = false,
    val custom: Boolean = false,
    /** A dropdown's own choices (the classification dropdowns use the fixed lists in Model.kt). */
    val options: List<String> = emptyList(),
    /** Filled in on new assets. */
    val defaultValue: String = "",
    /** Help shown with the field on the asset form. */
    val tooltip: String = "",
    /** Validation flags the field when it's left empty (mandatory fields always are). */
    val flagEmpty: Boolean = false,
    val rule: ValueRule = ValueRule.NONE,
    /** Date / time fields: how the value is shown (one of [DATE_DISPLAY] / [TIME_DISPLAY]); blank = no format. */
    val display: String = "",
    /** Text: min / max length; number: min / max value; date: earliest / latest year. Blank = no limit. */
    val min: String = "",
    val max: String = "",
) {
    /** True when validation checks the value's format (rule or limits), not just that it's there. */
    val hasFormat: Boolean get() = rule != ValueRule.NONE || min.isNotBlank() || max.isNotBlank()
}

/** Choices for the dropdown fields that aren't covered by the classification lists. */
val FIELD_OPTIONS: Map<String, List<String>> = mapOf(
    "condition" to listOf("Very good", "Good", "Fair", "Poor", "Very poor"),
    "criticality" to listOf("Low", "Medium", "High", "Critical"),
)

/** A kind of photo collected for each asset, with how many are needed and allowed. */
data class PhotoReq(val id: String, val name: String, val hint: String, val min: Int, val max: Int)

