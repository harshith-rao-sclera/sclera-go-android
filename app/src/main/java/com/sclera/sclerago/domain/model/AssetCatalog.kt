package com.sclera.sclerago.domain.model

/**
 * A location given to assets by hand (Assign location). [placeId] is the id of the building, floor or location
 * picked; [loc] and [where] are the texts the lists show for it.
 */
data class LocValue(val loc: String?, val where: String, val placeId: String? = null)

/** Every asset of the downloaded property (newest first). */
data class AssetCatalog(
    val assets: List<Asset> = emptyList(),
    /**
     * The assets were read from the property's open database. False while it is still being opened (just after the
     * property is opened, switched to or reopened): the assets aren't known yet, rather than there being none.
     */
    val loaded: Boolean = true,
    /**
     * The assets in Managed Assets ([Asset.managed]), newest first, kept apart from [assets] as the server leaves them out
     * of every list and count: only the Managed assets chip lists them, and they open read-only.
     */
    val managed: List<Asset> = emptyList(),
)
