package com.sclera.sclerago.domain.model

/** Separates a system type from an asset type in a [typeKey] (a character no type name holds). */
private const val TYPE_SEPARATOR = '\u001F'

/** One key for an asset type under its system type, as [AssetFilter.types] keeps it. */
fun typeKey(system: String, type: String): String = "$system$TYPE_SEPARATOR$type"

/** The system type and asset type of a [typeKey]. */
fun typeKeyParts(key: String): Pair<String, String> = key.substringBefore(TYPE_SEPARATOR) to key.substringAfter(TYPE_SEPARATOR, "")

/**
 * The asset list's filter (the web's people filter and its type filters); empty means no filter.
 * [assignees]: assets assigned to any of these people (user ids).
 * [systems] (system type names), [types] ([typeKey]s) and [subtypes] (master format ids): assets of those types. A choice
 * under a chosen system or asset type narrows it: Plumbing with Pumps (in Plumbing) picked is Plumbing's pumps only.
 * [untyped]: assets with no type yet. Any of the type choices may match; the people and the types must both.
 */
data class AssetFilter(
    val assignees: Set<String> = emptySet(),
    val systems: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val subtypes: Set<String> = emptySet(),
    val untyped: Boolean = false,
) {
    /** Some type choice is made (at any level, or "no type"). */
    val typed: Boolean get() = systems.isNotEmpty() || types.isNotEmpty() || subtypes.isNotEmpty() || untyped

    val isEmpty: Boolean get() = assignees.isEmpty() && !typed

    /** How many choices are made (the filter button's badge). */
    val count: Int get() = assignees.size + systems.size + types.size + subtypes.size + (if (untyped) 1 else 0)

    /** [asset] is assigned to one of the people chosen (or nobody is chosen). */
    fun peopleMatch(asset: Asset): Boolean = assignees.isEmpty() || asset.assignedTo in assignees

    /** An asset of the type [formatId] (null: no type) passes the type choices; [formats] is the type catalogue by id. */
    fun typeMatches(formatId: String?, formats: Map<String, MasterFormat>): Boolean {
        if (!typed) return true
        val f = formatId?.let(formats::get) ?: return untyped
        if (f.id in subtypes) return true
        val type = f.assetType
        if (type != null && typeKey(f.systemType, type) in types) {
            // A sub type chosen under this asset type narrows it to those sub types.
            return subtypes.none { formats[it]?.let { s -> s.systemType == f.systemType && s.assetType == type } == true }
        }
        if (f.systemType in systems) {
            // An asset type or sub type chosen under this system narrows it to those.
            val narrower = types.any { typeKeyParts(it).first == f.systemType } || subtypes.any { formats[it]?.systemType == f.systemType }
            return !narrower
        }
        return false
    }

    fun matches(asset: Asset, formats: Map<String, MasterFormat>): Boolean =
        peopleMatch(asset) && typeMatches(asset.masterFormatId, formats)
}

/** The filter sheet's side rail: the type levels and "Other" (assets with no type). */
enum class FilterCategory { SYSTEM, TYPE, SUBTYPE, OTHER }

/** [userId] chosen in the people filter, or no longer chosen. */
fun AssetFilter.togglePerson(userId: String): AssetFilter =
    copy(assignees = if (userId in assignees) assignees - userId else assignees + userId)

/**
 * [key] of [category] chosen, or no longer chosen ([FilterCategory.OTHER] has one choice, "no type", and ignores [key]).
 * Un-choosing a system type or an asset type also drops what was chosen under it, so nothing hidden stays chosen.
 */
fun AssetFilter.toggle(category: FilterCategory, key: String, formats: Map<String, MasterFormat>): AssetFilter = when (category) {
    FilterCategory.SYSTEM -> if (key in systems) {
        copy(
            systems = systems - key,
            types = types.filterTo(HashSet()) { typeKeyParts(it).first != key },
            subtypes = subtypes.filterTo(HashSet()) { formats[it]?.systemType != key },
        )
    } else {
        copy(systems = systems + key)
    }
    FilterCategory.TYPE -> if (key in types) {
        val (system, type) = typeKeyParts(key)
        copy(types = types - key, subtypes = subtypes.filterTo(HashSet()) { id -> formats[id]?.let { it.systemType == system && it.assetType == type } != true })
    } else {
        copy(types = types + key)
    }
    FilterCategory.SUBTYPE -> copy(subtypes = if (key in subtypes) subtypes - key else subtypes + key)
    FilterCategory.OTHER -> copy(untyped = !untyped)
}

/**
 * One choice in the filter sheet: [key] is what goes into [AssetFilter]; [parent] is what it sits under ("HVAC" for an asset
 * type, "HVAC › Pumps" for a sub type), null when every choice in the list sits under the same one. [count]: how many assets
 * it would show, given the people chosen.
 */
data class FilterChoice(val key: String, val label: String, val parent: String?, val count: Int, val selected: Boolean)

/**
 * Someone the assets are assigned to. [name] is null when this device doesn't know it (the sheet then shows initials of
 * nothing); [me] is the signed-in user, listed first. [count]: how many of the assets they have, given the types chosen.
 */
data class FilterPerson(val userId: String, val name: String?, val me: Boolean, val count: Int, val selected: Boolean)

/** What the filter sheet offers: the people, the choices of each type level, and the assets with no type. */
data class AssetFilterOptions(
    val people: List<FilterPerson> = emptyList(),
    val systems: List<FilterChoice> = emptyList(),
    val types: List<FilterChoice> = emptyList(),
    val subtypes: List<FilterChoice> = emptyList(),
    val untypedCount: Int = 0,
    val untypedSelected: Boolean = false,
)
