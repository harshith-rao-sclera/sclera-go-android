package com.sclera.sclerago.domain.sync

import kotlinx.coroutines.flow.StateFlow

/** Keeps the offline copy of one property's data downloaded and in step with the server. */
interface OfflineSync {
    /** Where the latest [download] stands. */
    val state: StateFlow<SyncState>

    /**
     * Downloads [propertyId]'s data and keeps it in sync, replacing another property's data on the device.
     * Does nothing while that property is already downloading; after [SyncState.Failed] it starts over.
     * [state] is [SyncState.Downloading] (or already settled) when this returns.
     */
    fun download(propertyId: String)

    /** Stops syncing; what was downloaded stays on the device until the user data is wiped. */
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
