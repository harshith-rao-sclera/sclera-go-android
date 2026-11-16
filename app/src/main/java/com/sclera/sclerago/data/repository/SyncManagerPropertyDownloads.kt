package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.model.inProgress
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.sync.OfflineSync
import com.sclera.sclerago.domain.sync.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Property downloads through the offline sync: [start] hands the property to [OfflineSync] (which calls the
 * engine's `initAssetDataSync()` with its id) and follows its state until the database is on the device or it fails.
 * The device holds one property's data at a time, so starting another property replaces the downloaded one.
 *
 * The property downloaded last is the recently used one: its id is kept ([EncryptedCache], wiped on sign-out) so the
 * app opens on it next time, and [resume] reopens its data then.
 */
@Singleton
class SyncManagerPropertyDownloads @Inject constructor(
    private val sync: OfflineSync,
    private val cache: EncryptedCache,
    @IoDispatcher io: CoroutineDispatcher,
) : PropertyDownloadRepository, UserDataCleaner {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val state = MutableStateFlow(PropertyDownloads())
    override val downloads: StateFlow<PropertyDownloads> = state.asStateFlow()

    private var following: Job? = null

    /** The property handed to the sync last (being downloaded, reopened or open). */
    private var current: String? = null

    init {
        scope.launch {
            val saved = cache.read(RECENT)?.takeIf { it.isNotBlank() }
            state.update { it.copy(downloadedId = it.downloadedId ?: saved, restored = true) }
        }
    }

    @Synchronized
    override fun start(propertyId: String) {
        if (propertyId == current && state.value.active[propertyId].inProgress) return
        following?.cancel()
        val previous = current
        current = propertyId
        // Another property's data is dropped by this download, so it is no longer the recent one.
        val replacing = state.value.downloadedId != propertyId
        state.update { s ->
            s.copy(
                downloadedId = s.downloadedId.takeIf { !replacing },
                active = s.active - setOfNotNull(previous) + (propertyId to PropertyDownload.Running(0f, 0.0, 0.0)),
            )
        }
        sync.download(propertyId)
        following = scope.launch {
            if (replacing) save("")
            follow(propertyId, quiet = false)
        }
    }

    override fun resume() {
        scope.launch {
            val id = state.first { it.restored }.downloadedId ?: return@launch
            reopen(id)
        }
    }

    @Synchronized
    private fun reopen(propertyId: String) {
        if (current != null) return
        current = propertyId
        // The engine reuses the database already on the device (or downloads it again if it is gone).
        sync.download(propertyId)
        following = scope.launch { follow(propertyId, quiet = true) }
    }

    /** Follows the sync for [propertyId]; [quiet] (reopening at start) shows no progress and no failure. */
    private suspend fun follow(propertyId: String, quiet: Boolean) {
        // The engine never got going (no account yet, or it failed before reporting a size).
        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
            sync.state.first { it !is SyncState.Downloading || it.totalMb != null }
        }
        if (started == null) {
            if (!quiet) set(propertyId, PropertyDownload.Failed)
            return
        }
        val end = sync.state.first { s ->
            val busy = s is SyncState.Downloading || s is SyncState.Unpacking
            if (busy && !quiet) show(propertyId, s)
            !busy
        }
        when (end) {
            SyncState.Ready -> {
                state.update { it.copy(downloadedId = propertyId) }
                save(propertyId)
                if (quiet) return
                set(propertyId, PropertyDownload.Finished)
                delay(FINISHED_SHOWN_MS)
                remove(propertyId)
            }
            is SyncState.Failed -> if (!quiet) set(propertyId, PropertyDownload.Failed)
            // Stopped (signed out): nothing to show any more.
            else -> remove(propertyId)
        }
    }

    @Synchronized
    override fun clear() {
        following?.cancel()
        following = null
        current = null
        state.value = PropertyDownloads(restored = true)
    }

    private fun show(propertyId: String, s: SyncState) {
        val d = when (s) {
            is SyncState.Downloading -> {
                val fraction = ((s.progressPercent ?: 0) / 100f).coerceIn(0f, 1f)
                PropertyDownload.Running(fraction, s.downloadedMb ?: 0.0, s.totalMb ?: 0.0)
            }
            SyncState.Unpacking -> PropertyDownload.Unpacking
            else -> return
        }
        set(propertyId, d)
    }

    /** Keeps [propertyId] as the recent property ("" forgets it). Not kept, the app just opens on the property list. */
    private fun save(propertyId: String) {
        runCatching { cache.write(RECENT, propertyId) }
    }

    private fun set(propertyId: String, d: PropertyDownload) = state.update { it.copy(active = it.active + (propertyId to d)) }

    private fun remove(propertyId: String) = state.update { it.copy(active = it.active - propertyId) }

    private companion object {
        const val RECENT = "recent_property"
        const val FINISHED_SHOWN_MS = 1_400L
        const val START_TIMEOUT_MS = 30_000L
    }
}
