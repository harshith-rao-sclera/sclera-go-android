package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PropertyDownloads
import kotlinx.coroutines.flow.StateFlow

/** Downloads a property's data for working offline, one property at a time. */
interface PropertyDownloadRepository {
    val downloads: StateFlow<PropertyDownloads>

    /** Starts downloading [propertyId] (replacing the property downloaded before); does nothing while it already runs. */
    fun start(propertyId: String)

    /** Re-reads which property is downloaded (the offline sync may have finished on its own). */
    fun refresh()
}
