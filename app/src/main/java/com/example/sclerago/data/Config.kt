package com.example.sclerago.data

/** What kind of input an asset field takes. */
enum class FieldKind(val label: String) { TEXT("Text"), DROPDOWN("Dropdown"), NUMBER("Number"), DATE("Date"), TEXTAREA("Textarea") }

/** Where a field sits on the asset details form. */
enum class FieldSection(val title: String) { IDENT("Identification"), CLASS("Classification"), TAGS("Tags & connectivity"), MORE("More details"), NOTES("Notes") }

/**
 * One asset field as configured in Configuration → Asset fields: whether field workers see it and
 * whether it must be filled. [locked] fields (the name) are always shown and required.
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
)

val DEFAULT_FIELDS = listOf(
    FieldCfg("name", "Name", FieldKind.TEXT, FieldSection.IDENT, required = true, locked = true),
    FieldCfg("make", "Manufacturer", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("model", "Model", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("serial", "Serial number", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("power", "Rated power (kW)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("voltage", "Voltage (V)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("speed", "Speed (RPM)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("sys", "System type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("typ", "Asset type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("sub", "Sub type", FieldKind.DROPDOWN, FieldSection.CLASS),
    FieldCfg("jacs", "JACS code", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("label", "Asset tag / QR", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("ble", "BLE tag", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("ip", "IP address", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("condition", "Condition", FieldKind.DROPDOWN, FieldSection.MORE, visible = false),
    FieldCfg("criticality", "Criticality", FieldKind.DROPDOWN, FieldSection.MORE, visible = false),
    FieldCfg("install", "Install date", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("warranty", "Warranty expiry", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("lastService", "Last service date", FieldKind.DATE, FieldSection.MORE, visible = false),
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
