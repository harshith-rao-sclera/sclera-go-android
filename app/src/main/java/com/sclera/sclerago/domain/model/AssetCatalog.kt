package com.sclera.sclerago.domain.model

/**
 * A location given to assets by hand (Assign location). [placeId] is the id of the building, floor or location
 * picked; [loc] and [where] are the texts the lists show for it.
 */
data class LocValue(val loc: String?, val where: String, val placeId: String? = null)

/**
 * Every asset of the downloaded property (newest first) and what the screens look up about them: the asset each
 * scanned device was made into, and the text of each asset's tag fields (BLE tag, IP address, label, notes) that
 * a scan result is matched against.
 */
data class AssetCatalog(
    val assets: List<Asset> = emptyList(),
    /** Scanned device id → the asset made from it. */
    val deviceAsset: Map<String, String> = emptyMap(),
    /** Asset id → its BLE tag, IP address, label and notes, one per line. */
    val scanText: Map<String, String> = emptyMap(),
) {
    /** Scanned devices that have been made into assets. */
    val addedDevices: Set<String> get() = deviceAsset.keys
}
