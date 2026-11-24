package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PropertyDownloads
import kotlinx.coroutines.flow.StateFlow

/**
 * Downloads properties' data for working offline. Several can be on the device and all of them keep syncing; the app
 * works on one at a time, the current property.
 */
interface PropertyDownloadRepository {
    val downloads: StateFlow<PropertyDownloads>

    /**
     * Starts downloading [propertyId] alongside any other downloads; does nothing while it already runs. When it
     * finishes it is downloaded, and it becomes the current property only if there is none yet.
     */
    fun start(propertyId: String)

    /**
     * Makes the downloaded [propertyId] the current property straight away (no download, so it works offline); the
     * other properties keep syncing. A property that isn't downloaded is ignored.
     */
    fun open(propertyId: String)

    /** Restarts the sync of every downloaded property and reopens the current one (after sign-in or an app start). */
    fun resume()

    /**
     * Deletes [propertyIds]' offline data from the device and stops their sync (properties the user lost access to);
     * the current property stops being current when it is one of them. The others carry on.
     */
    suspend fun remove(propertyIds: Set<String>) {}
}
