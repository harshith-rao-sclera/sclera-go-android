package com.sclera.sclerago.domain.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps offline copies of properties' data on the device, each in step with the server (one database and sync per
 * property, as inspection has one per vdms). The app reads the [select]ed property's data.
 */
interface OfflineSync {
    /** Where each property handed to [download] stands. */
    val states: StateFlow<Map<String, SyncState>>

    /**
     * Downloads [propertyId]'s data (or reopens it when it is on the device) and keeps it in sync alongside the other
     * properties. Does nothing while that property is already downloading; after [SyncState.Failed] it starts over.
     * Its state is [SyncState.Downloading] (or already settled) when this returns.
     */
    fun download(propertyId: String)

    /** Makes [propertyId] (null: none) the property whose data the app reads; the others keep syncing. */
    fun select(propertyId: String?)

    /** The properties whose data is fully downloaded on the device. Blocking but short. */
    fun downloadedProperties(): Set<String>

    /** Stops following every property; what was downloaded stays on the device until the user data is wiped. */
    fun stop()
}

sealed interface SyncState {
    data object NotStarted : SyncState

    /** [progressPercent] and the sizes are null while the total size is not known yet. */
    data class Downloading(
        val progressPercent: Int? = null,
        val downloadedMb: Double? = null,
        val totalMb: Double? = null,
    ) : SyncState

    /** Downloaded; the database is being unzipped and set up. How long it takes isn't known. */
    data object Unpacking : SyncState

    /** The data is on the device and can be read; later changes are synced in the background. */
    data object Ready : SyncState

    data class Failed(val reason: SyncFailure) : SyncState
}

enum class SyncFailure { NO_NETWORK, SESSION_EXPIRED, SERVER_ERROR, STORAGE_FULL }
