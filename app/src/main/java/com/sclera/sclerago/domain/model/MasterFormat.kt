package com.sclera.sclerago.domain.model

/** One row of the asset type catalogue: system type, then optionally asset type and sub type. */
data class MasterFormat(
    val id: String,
    val systemType: String,
    val assetType: String? = null,
    val subtype: String? = null,
) {
    /** The most specific name the app's [AssetType] is worked out from. */
    val label: String get() = assetType ?: systemType
}

/** What an asset's system, asset and sub type say while it isn't classified (the form's first choice). */
const val UNCLASSIFIED = "Generic"

/** The system types of the catalogue, in its order. */
fun List<MasterFormat>.systemTypes(): List<String> = map { it.systemType }.filter { it.isNotBlank() }.distinct()

/** The asset types filed under [system], in the catalogue's order. */
fun List<MasterFormat>.assetTypes(system: String): List<String> =
    filter { it.systemType.equals(system, true) }.mapNotNull { it.assetType }.distinct()

/** The sub types filed under [system] and [type], in the catalogue's order. */
fun List<MasterFormat>.subtypes(system: String, type: String): List<String> =
    filter { it.systemType.equals(system, true) && it.assetType.equals(type, true) }.mapNotNull { it.subtype }.distinct()

/**
 * The classification values to set when [key] ("sys", "typ" or "sub") becomes [value] in [values]: that value, and
 * [UNCLASSIFIED] for the asset and sub type below it when the catalogue doesn't file them under the new choice.
 */
fun List<MasterFormat>.reclassify(values: Map<String, String>, key: String, value: String): Map<String, String> {
    val sys = if (key == "sys") value else values["sys"].orEmpty()
    val typ = when (key) {
        "sys" -> values["typ"].orEmpty().takeIf { t -> assetTypes(sys).any { it.equals(t, true) } } ?: UNCLASSIFIED
        "typ" -> value
        else -> values["typ"].orEmpty()
    }
    val sub = when (key) {
        "sub" -> value
        else -> values["sub"].orEmpty().takeIf { s -> subtypes(sys, typ).any { it.equals(s, true) } } ?: UNCLASSIFIED
    }
    return mapOf("sys" to sys, "typ" to typ, "sub" to sub)
}
