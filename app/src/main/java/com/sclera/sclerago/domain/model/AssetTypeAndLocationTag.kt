package com.sclera.sclerago.domain.model

// Type and Location tag: the asset's own (the server's `assets.type` and `assets.location_tag` columns, t0030), not
// fields. The web shows them above the asset's fields. The details form keeps them under [ASSET_KIND_KEY] and
// [LOCATION_TAG_KEY], keys no field can have, so they never go into the asset's answers.

/** Whether an asset is already there or only planned: the server's ASSET_KINDS. */
enum class AssetKind(val db: String) {
    EXISTING("existing"),
    PROPOSED("proposed"),
    ;

    companion object {
        /** [value] (any case) as a kind, or null when it is none of them. */
        fun of(value: String?): AssetKind? = value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.db == v } }
    }
}

/** Where on a surface a location tag puts the asset. */
enum class TagSpot(val db: String) {
    BELOW("below"), ABOVE("above"), NORTH("north"), SOUTH("south"), EAST("east"), WEST("west"), ON("on"), NONE("none"),
}

/** A surface a location tag is on, with its spots in the server's order (its LOCATION_TAG_SPOTS). */
enum class TagSurface(val db: String, val spots: List<TagSpot>) {
    CEILING("ceiling", listOf(TagSpot.BELOW, TagSpot.ABOVE, TagSpot.NONE)),
    WALL("wall", listOf(TagSpot.NORTH, TagSpot.SOUTH, TagSpot.EAST, TagSpot.WEST, TagSpot.NONE)),
    FLOOR("floor", listOf(TagSpot.ON, TagSpot.BELOW, TagSpot.NONE)),
}

/** A location tag: one surface and where on it, kept as "wall:north". */
data class LocationTag(val surface: TagSurface, val spot: TagSpot) {
    val db: String get() = "${surface.db}:${spot.db}"

    companion object {
        /** [value] ("wall:north", any case) as a tag the server takes, or null (a spot its surface doesn't have too). */
        fun of(value: String?): LocationTag? {
            val parts = value?.trim()?.lowercase()?.split(":")?.takeIf { it.size == 2 } ?: return null
            val surface = TagSurface.entries.firstOrNull { it.db == parts[0].trim() } ?: return null
            val spot = surface.spots.firstOrNull { it.db == parts[1].trim() } ?: return null
            return LocationTag(surface, spot)
        }
    }
}

/** The details form key of the asset's Type ([AssetKind.db], empty for none). */
const val ASSET_KIND_KEY = "detail:type"

/** The details form key of the asset's Location tag ([LocationTag.db], empty for none). */
const val LOCATION_TAG_KEY = "detail:location_tag"

/** The form keys of the asset's own details that aren't fields. */
val ASSET_DETAIL_KEYS = setOf(ASSET_KIND_KEY, LOCATION_TAG_KEY)

/**
 * The detail [key]'s [value] as the server takes it ("existing", "wall:north"), or null for none and for anything it
 * doesn't take (left out rather than refused, as the web's assetDetailsBody).
 */
fun assetDetailValue(key: String, value: String?): String? = when (key) {
    ASSET_KIND_KEY -> AssetKind.of(value)?.db
    LOCATION_TAG_KEY -> LocationTag.of(value)?.db
    else -> null
}
