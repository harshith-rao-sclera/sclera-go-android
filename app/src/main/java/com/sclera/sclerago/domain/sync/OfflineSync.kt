package com.sclera.sclerago.domain.sync

import kotlinx.coroutines.flow.StateFlow

/** Keeps the offline copy of the signed-in user's data downloaded and in step with the server. */
interface OfflineSync {
    val state: StateFlow<SyncState>

    /** Starts (or resumes) the download and the background sync for the signed-in user; does nothing if already running. */
    fun start()

    /** Tries again after [SyncState.Failed]. */
    fun retry()

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

    /** The data is on the device and can be read; later changes are synced in the background. */
    data object Ready : SyncState

    data class Failed(val reason: SyncFailure) : SyncState
}

enum class SyncFailure { NO_NETWORK, SESSION_EXPIRED, SERVER_ERROR, STORAGE_FULL }
