package com.sclera.sclerago.data.local

/**
 * Something the device keeps per property in `global_config.db` (its assignees, its type icons), forgotten for a
 * property the user lost access to while the other properties' rows stay. Blocking: call off the main thread.
 */
interface PropertyLocalData {
    fun forget(propertyId: String)
}
