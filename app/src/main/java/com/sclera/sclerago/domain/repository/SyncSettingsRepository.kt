package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.SyncSettings
import kotlinx.coroutines.flow.Flow

/** A downloaded property's sync on this device: what is waiting to upload, the last sync, and the two switches. */
interface SyncSettingsRepository {
    /**
     * [propertyId]'s sync state, kept up to date while collected. Its counts are read from the open property's database,
     * so they are 0 for any other property.
     */
    fun observe(propertyId: String): Flow<SyncSettings>

    /** Data sync on or off for [propertyId]: whether changes from the server come down (the device's always upload). */
    fun setDataSync(propertyId: String, on: Boolean)

    /** Photo and video sync on or off for [propertyId]: whether the server's photos and videos come down. */
    fun setMediaSync(propertyId: String, on: Boolean)
}
