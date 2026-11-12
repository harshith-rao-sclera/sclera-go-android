package com.sclera.sclerago.data

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

val DEFAULT_FIELDS = listOf(
    FieldCfg("name", "Name", FieldKind.TEXT, FieldSection.IDENT, required = true, locked = true),
    FieldCfg("make", "Manufacturer", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("model", "Model", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("serial", "Serial number", FieldKind.TEXT, FieldSection.IDENT, flagEmpty = true),
    FieldCfg("power", "Rated power (kW)", FieldKind.NUMBER, FieldSection.IDENT, rule = ValueRule.POSITIVE),
    FieldCfg("voltage", "Voltage (V)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("speed", "Speed (RPM)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("sys", "System type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("typ", "Asset type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("sub", "Sub type", FieldKind.DROPDOWN, FieldSection.CLASS),
    FieldCfg("jacs", "JACS code", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("label", "Asset tag / QR", FieldKind.TEXT, FieldSection.TAGS, rule = ValueRule.CODE),
    FieldCfg("ble", "BLE tag", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("ip", "IP address", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("condition", "Condition", FieldKind.DROPDOWN, FieldSection.MORE, visible = false, options = listOf("Very good", "Good", "Fair", "Poor", "Very poor")),
    FieldCfg("criticality", "Criticality", FieldKind.DROPDOWN, FieldSection.MORE, visible = false, options = listOf("Low", "Medium", "High", "Critical")),
    FieldCfg("install", "Install date", FieldKind.DATE, FieldSection.MORE, visible = false, rule = ValueRule.PAST),
    FieldCfg("warranty", "Warranty expiry", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("lastService", "Last service date", FieldKind.DATE, FieldSection.MORE, visible = false, rule = ValueRule.PAST),
    FieldCfg("nextService", "Next service date", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("note", "Notes", FieldKind.TEXTAREA, FieldSection.NOTES),
)

/** Choices for the dropdown fields that aren't covered by the classification lists. */
val FIELD_OPTIONS: Map<String, List<String>> = mapOf(
    "condition" to listOf("Very good", "Good", "Fair", "Poor", "Very poor"),
    "criticality" to listOf("Low", "Medium", "High", "Critical"),
)

/** A kind of photo collected for each asset, with how many are needed and allowed. */
data class PhotoReq(val id: String, val name: String, val hint: String, val min: Int, val max: Int)

val DEFAULT_PHOTO_REQS = listOf(
    PhotoReq("plate", "Nameplate", "Manufacturer's plate with model and serial number", 1, 2),
    PhotoReq("asset", "Asset", "The equipment itself, in place", 1, 3),
    PhotoReq("label", "Label / tag", "The asset tag or QR label", 0, 1),
    PhotoReq("site", "Surroundings", "Where the asset sits, to find it again", 0, 2),
)

// ---------------------------------------------------------------- Location tree edits

/** Returns the tree with [f] applied to the node [id] (null from [f] removes it). */
fun List<LocNode>.mapNode(id: String, f: (LocNode) -> LocNode?): List<LocNode> = mapNotNull { n ->
    if (n.id == id) f(n) else n.copy(kids = n.kids?.mapNode(id, f))
}

fun List<LocNode>.removeNodes(ids: Set<String>): List<LocNode> =
    filter { it.id !in ids }.map { it.copy(kids = it.kids?.removeNodes(ids)) }

fun List<LocNode>.findNode(id: String): LocNode? {
    forEach { n ->
        if (n.id == id) return n
        n.kids?.findNode(id)?.let { return it }
    }
    return null
}

// ---------------------------------------------------------------- Buildings

/** A building's details, from the Edit building sheet. Times are HH:mm. */
data class BuildingInfo(
    val type: String = "",
    val address1: String = "",
    val address2: String = "",
    val country: String = "",
    val state: String = "",
    val region: String = "",
    val city: String = "",
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    val notes: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val accessCode: String = "",
)

val BUILDING_TYPES = listOf(
    "Office", "Retail", "Industrial", "Warehouse", "Data centre", "Healthcare", "Education", "Hospitality",
    "Residential", "Laboratory", "Mixed use", "Other",
)

val COUNTRIES = listOf(
    "India", "United States", "United Kingdom", "United Arab Emirates", "Saudi Arabia", "Qatar", "Singapore",
    "Malaysia", "Australia", "New Zealand", "Canada", "Germany", "France", "Netherlands", "Ireland", "Spain",
    "Italy", "Japan", "South Africa", "Brazil", "Mexico",
)

val REGIONS = listOf("Asia Pacific", "Middle East", "Africa", "Europe", "North America", "Latin America")
