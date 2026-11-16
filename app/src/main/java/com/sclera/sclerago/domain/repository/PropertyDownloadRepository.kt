package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PropertyDownloads
import kotlinx.coroutines.flow.StateFlow

/** Downloads a property's data for working offline, one property at a time. */
interface PropertyDownloadRepository {
    val downloads: StateFlow<PropertyDownloads>

    /**
     * Starts downloading [propertyId] (replacing the property downloaded before); does nothing while it already runs.
     * When it finishes, it becomes the recently used property.
     */
    fun start(propertyId: String)

    /** Reopens the recently used property's data and keeps it syncing (after sign-in or an app start); nothing when there is none. */
    fun resume()
}
