package com.sclera.sclerago.domain.model

/** What kind of input an asset field takes. */
enum class FieldKind(val label: String) {
    TEXT("Text"), CHECKBOX("Check Box"), MULTI("Multiple Check Boxes"), YESNO("Yes/No"), DATE("Date"), TIME("Time"),
    NUMBER("Number"), URL("URL"), DROPDOWN("Dropdown"), TEXTAREA("Textarea"),

    /** An amount of money: a number whose currency is the field's [FieldCfg.unit] (USD), as the server's `currency` type. */
    CURRENCY("Currency"),

    // Kinds the phone's asset form has no input for: only Configuration shows them (mandatory, visible, checks, edit,
    // delete), as the web's field list does.
    IMAGE("Image"), VIDEO("Video"), VOICE_MEMO("Voice memo"), KEY_VALUE("Key value"),
}

/** Field kinds the asset form has no input for; Configuration lists and manages them, the form leaves them out. */
val NO_INPUT_KINDS = setOf(FieldKind.IMAGE, FieldKind.VIDEO, FieldKind.VOICE_MEMO, FieldKind.KEY_VALUE)

/** How yes / no answers (the words) and checkboxes (true / false) are stored, as the web stores them. */
const val ANSWER_YES = "Yes"
const val ANSWER_NO = "No"
const val CHECKED = "true"
const val UNCHECKED = "false"

/** True only for the value the API uses for a checked box. Legacy numeric defaults such as "1" are unchecked. */
fun checkboxIsChecked(value: String): Boolean = value.equals(CHECKED, ignoreCase = true)

/** The server's field type text (`asset_fields.type`) of each kind. */
private val TYPE_OF_KIND = mapOf(
    FieldKind.TEXT to "text", FieldKind.TEXTAREA to "textarea", FieldKind.NUMBER to "number", FieldKind.URL to "url",
    FieldKind.DATE to "date", FieldKind.TIME to "time", FieldKind.DROPDOWN to "dropdown",
    FieldKind.MULTI to "multiple_checkbox", FieldKind.CHECKBOX to "checkbox", FieldKind.YESNO to "yesno",
    FieldKind.IMAGE to "image", FieldKind.VIDEO to "video", FieldKind.VOICE_MEMO to "voice_memo", FieldKind.KEY_VALUE to "key_value",
    FieldKind.CURRENCY to "currency",
)
private val KIND_OF_TYPE = TYPE_OF_KIND.entries.associate { (kind, type) -> type to kind }

/** The kind of a server field type for the asset form, or null for the types it has no input for ([NO_INPUT_KINDS]). */
fun fieldKindOf(type: String?): FieldKind? = KIND_OF_TYPE[type]?.takeIf { it !in NO_INPUT_KINDS }

/** The kind of any server field type, for Configuration (which lists every field, as the web does); null when unknown. */
fun configFieldKindOf(type: String?): FieldKind? = KIND_OF_TYPE[type]

/** The server's field type text of this kind. */
val FieldKind.serverType: String get() = TYPE_OF_KIND.getValue(this)

/** Kinds whose values come from a list of options. */
val OPTION_KINDS = setOf(FieldKind.DROPDOWN, FieldKind.MULTI)

/** Where a field sits on the asset details form. */
enum class FieldSection(val title: String) { IDENT("Identification"), CLASS("Classification"), TAGS("Tags & connectivity"), MORE("More details"), NOTES("Notes") }

/** Field kinds whose values are free text (their checks are a text format and lengths). */
val TEXT_KINDS = setOf(FieldKind.TEXT, FieldKind.TEXTAREA)

/**
 * Field kinds whose answer is a number: number fields and a currency field's amount (the server's `is_number`). Their
 * checks are a range and whole numbers only, and their answers are kept as numbers.
 */
val NUMBER_KINDS = setOf(FieldKind.NUMBER, FieldKind.CURRENCY)

/** Field kinds that can have a format in their checks (text, number, currency and date fields), as on the server. */
val FORMAT_KINDS = TEXT_KINDS + NUMBER_KINDS + FieldKind.DATE

/**
 * What a field's values must be, beyond its kind (Configuration → Asset fields → Checks), as the server's
 * `checks.format`: a text format (`kind`) for text fields, or past / future for dates. [kinds] are the field kinds it
 * applies to. Numbers have no rule here: their checks are a range and "whole numbers only" ([FieldCfg.wholeNumber]).
 */
enum class ValueRule(val kinds: Set<FieldKind>) {
    NONE(FieldKind.entries.toSet()),
    LETTERS(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    DIGITS(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    ALNUM(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    EMAIL(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    PHONE(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),
    UPPERCASE(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),

    /** The whole value must match the field's own regular expression ([FieldCfg.pattern]). */
    PATTERN(setOf(FieldKind.TEXT, FieldKind.TEXTAREA)),

    /** Dates: not in the future. */
    PAST(setOf(FieldKind.DATE)),

    /** Dates: not in the past. */
    FUTURE(setOf(FieldKind.DATE)),
}

/** The longest regular expression a field's checks may hold, and the largest length limit (the server's limits). */
const val MAX_PATTERN_LENGTH = 200
const val MAX_CHECK_LENGTH = 10_000

/** The longest tooltip a field may have (the web's limit). */
const val MAX_TOOLTIP_LENGTH = 256

/** Date and time default values, as the web stores them: picked on the form, or today / now. */
const val DEFAULT_DATE_PICKER = "datepicker"
const val DEFAULT_TODAY = "today"
const val DEFAULT_TIME_PICKER = "timepicker"
const val DEFAULT_NOW = "now"

/**
 * What validation looks for in one field (its `checks`): flag it when it's left empty, and its format. Text: [rule]
 * ([pattern] for [ValueRule.PATTERN]) and the length limits [min] / [max]. Number: the range [min] / [max] and
 * [wholeNumber]. Date: [rule] past or future. Blank limits = none.
 */
data class FieldChecks(
    val flagEmpty: Boolean = false,
    val rule: ValueRule = ValueRule.NONE,
    val pattern: String = "",
    val min: String = "",
    val max: String = "",
    val wholeNumber: Boolean = false,
) {
    /** True when a format is set (not just the highlight-empty flag). */
    val hasFormat: Boolean get() = rule != ValueRule.NONE || min.isNotBlank() || max.isNotBlank() || wholeNumber

    /** True when anything at all is set. */
    val isSet: Boolean get() = flagEmpty || hasFormat
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
    /** Each option's colour ("#rrggbb"), by option name; an option without one gets [DEFAULT_OPTION_COLOR] when saved (as the web). */
    val optionColors: Map<String, String> = emptyMap(),
    /** Filled in on new assets (dates / times: [DEFAULT_TODAY], [DEFAULT_NOW]; multiple checkboxes: option names joined with ","). */
    val defaultValue: String = "",
    /** Help shown with the field on the asset form. */
    val tooltip: String = "",
    /** Validation flags the field when it's left empty (mandatory fields always are). */
    val flagEmpty: Boolean = false,
    val rule: ValueRule = ValueRule.NONE,
    /** Text: min / max length; number and currency: min / max value. Blank = no limit. */
    val min: String = "",
    val max: String = "",
    /** Text fields with [ValueRule.PATTERN]: the regular expression the whole value must match. */
    val pattern: String = "",
    /** Number and currency fields: only whole numbers pass. */
    val wholeNumber: Boolean = false,
    /**
     * The unit its answer is in, as the server keeps it (`asset_fields.unit`; blank for none). A currency field's is its
     * currency, one of the server's currency codes (USD), and it always has one.
     */
    val unit: String = "",
    /** The field's id at the server (Configuration reads and writes fields there); blank for a field not made yet. */
    val id: String = "",
    /** The field belongs to one asset alone (added to it, or brought by its type), not to every asset. */
    val ownField: Boolean = false,
) {
    /** What validation looks for in this field. */
    val checks: FieldChecks get() = FieldChecks(flagEmpty, rule, pattern, min, max, wholeNumber)

    /** True when validation checks the value's format (rule, limits or whole numbers), not just that it's there. */
    val hasFormat: Boolean get() = checks.hasFormat

    /** This field with [c] as its checks. */
    fun withChecks(c: FieldChecks): FieldCfg =
        copy(flagEmpty = c.flagEmpty, rule = c.rule, pattern = c.pattern, min = c.min, max = c.max, wholeNumber = c.wholeNumber)
}

/** The colour a new dropdown / multiple-checkbox option gets (the web's default). */
const val DEFAULT_OPTION_COLOR = "#5e72e4"

/** The colours offered for an option (the web lets any colour be picked; these cover the usual ones). */
val OPTION_COLORS: List<String> = listOf(
    DEFAULT_OPTION_COLOR, "#11cdef", "#2dce89", "#fb6340", "#f5365c", "#ffd600", "#8965e0", "#f3a4b5", "#172b4d", "#8898aa",
)

private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

/** [c] when it is a "#rrggbb" colour, else null (colours from the server are checked before they are shown or sent). */
fun optionColorOrNull(c: String?): String? = c?.trim()?.takeIf { HEX_COLOR.matches(it) }?.lowercase()

private val WHITESPACE = Regex("\\s+")

/** A field name as compared for duplicates: lower case, without any whitespace (as the web compares them). */
fun fieldNameKey(name: String): String = name.lowercase().replace(WHITESPACE, "")

/** Why an option name can't be added to a dropdown or multiple-checkbox field. */
enum class OptionProblem { INVALID, DUPLICATE }

/** Why [name] can't be added next to [existing] options (blank, or holding `/ , { }`, or already there); null when it can. */
fun optionProblem(name: String, existing: List<String>): OptionProblem? {
    val o = name.trim()
    return when {
        o.isEmpty() || o.any { it in "/,{}" } -> OptionProblem.INVALID
        o in existing -> OptionProblem.DUPLICATE
        else -> null
    }
}

/** Configuration → Asset fields: the fields field workers fill in. Photo rules and the geofence are property settings at the server. */
data class AssetConfig(val fields: List<FieldCfg>) {
    /** Fields that must be filled, as key to label. */
    val requiredFields: List<Pair<String, String>> get() = fields.filter { it.required && it.visible }.map { it.key to it.label }
}

/** How many choices a dropdown field offers (its own, as the property's data stores them). */
val FieldCfg.optionCount: Int get() = options.size

/** One row of an asset's fields, in their order: a field with an input ([key] of a [FieldCfg]), or a photo, video or voice memo field. */
sealed interface FormEntry {
    data class Input(val key: String) : FormEntry

    /** A field answered by files kept in the Media tab ([id] is matched by `asset_media.field_id`). */
    data class MediaField(val id: String, val name: String, val required: Boolean) : FormEntry
}
