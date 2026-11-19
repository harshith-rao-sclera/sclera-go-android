package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetIcons
import kotlinx.coroutines.flow.StateFlow

/** The asset icons, kept on the device (in `global_config.db`) and brought up to date from the server when online. */
interface AssetIconRepository {
    /** The icons and the open property's type icons, as kept on the device. */
    val icons: StateFlow<AssetIcons>

    /**
     * Shows the icons kept on the device, then (online) downloads the server's icons that are new or changed. They
     * are the same for every property, so this runs when the app opens, whether or not a property is open.
     */
    suspend fun refreshIcons()

    /**
     * Shows the icons kept for [propertyId], then (online) downloads the server's icons that are new or changed and its
     * types' icon names. Without a connection what is kept stays; nothing is thrown.
     */
    suspend fun open(propertyId: String)
}
