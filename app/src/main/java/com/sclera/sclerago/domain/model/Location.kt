package com.sclera.sclerago.domain.model

enum class NodeKind(val title: String) { BUILDING("Building"), FLOOR("Floor"), LOCATION("Location") }

/** Building → floor → location tree. Locations have kids == null. */
data class LocNode(
    val id: String,
    val name: String,
    val kind: NodeKind,
    val kids: List<LocNode>? = null,
    val count: Int = 0,
    /** Locations only: what kind of space it is (e.g. Plant room) and its short code. */
    val type: String = "",
    val code: String = "",
    /** Buildings only: type, address, contact and access details (Configuration → Building info). */
    val info: BuildingInfo? = null,
)

/** What the location picker hands back on Confirm. */
data class LocPick(val id: String, val label: String, val locs: List<String>, val where: String, val path: List<String>)

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
