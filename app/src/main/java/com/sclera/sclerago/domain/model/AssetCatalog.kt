package com.sclera.sclerago.domain.model

/** A location given to assets by hand (Assign location), overriding the one they came with. */
data class LocValue(val loc: String?, val where: String)

/**
 * Every asset and the edits made to it since: the assets themselves (newest first) and, per asset id,
 * the status, location, details and media changed, which were deleted, and when each was last added
 * or edited (for "New or changed" validation runs).
 */
data class AssetCatalog(
    val assets: List<Asset> = emptyList(),
    val deleted: Set<String> = emptySet(),
    val statusOverride: Map<String, Status> = emptyMap(),
    val locationOverride: Map<String, LocValue> = emptyMap(),
    val savedDetails: Map<String, Map<String, String>> = emptyMap(),
    val mediaAdded: Map<String, List<Media>> = emptyMap(),
    val touched: Map<String, Long> = emptyMap(),
    /** Scanned device id → the asset made from it. */
    val deviceAsset: Map<String, String> = emptyMap(),
    /** Scanned devices that have been made into assets. */
    val addedDevices: Set<String> = emptySet(),
) {
    /** [prop]'s assets as they are now, with every edit applied. */
    fun assetsIn(prop: String): List<Asset> = assets
        .filter { it.id !in deleted && it.propertyId == prop }
        .map { a ->
            var x = a
            locationOverride[a.id]?.let { x = x.copy(loc = it.loc, where = it.where) }
            statusOverride[a.id]?.let { x = x.copy(st = it) }
            savedDetails[a.id]?.get("name")?.takeIf { it.isNotBlank() }?.let { x = x.copy(name = it) }
            mediaAdded[a.id]?.let { x = x.copy(media = x.media + it) }
            touched[a.id]?.let { x = x.copy(updatedAt = it) }
            x
        }

    /** How many assets [prop] has, any status. */
    fun countIn(prop: String): Int = assets.count { it.id !in deleted && it.propertyId == prop }
}
