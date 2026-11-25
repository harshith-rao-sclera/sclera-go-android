package com.sclera.sclerago.domain.model

/**
 * Where a downloaded property's sync stands on this device (its Sync settings page). Changes made here always upload;
 * the two switches only stop what comes down from the server.
 */
data class SyncSettings(
    /** Assets changed here whose changes the server doesn't have yet (the `assets` rows waiting to be pushed). */
    val assetsWaiting: Int = 0,
    /** Photos and videos added or changed here that the server doesn't have yet (`asset_media` rows or their files). */
    val mediaWaiting: Int = 0,
    /** When changes last came down from the server (epoch millis); null before the first sync. */
    val lastSyncedAt: Long? = null,
    /** Data sync: changes from the server come down. */
    val dataSync: Boolean = true,
    /** Photo and video sync: photos and videos from the server come down. */
    val mediaSync: Boolean = true,
)
