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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Property downloads through the offline sync: [start] hands a property to [OfflineSync] (which calls the engine's
 * `initAssetDataSync()` with its id) and follows its state until the database is on the device or it fails.
 * Several properties can be downloaded, at the same time too, and every downloaded property keeps syncing (one
 * database and engine each, as inspection has per vdms). [open] only changes which one the app works on.
 *
 * The current property's id is kept ([EncryptedCache], wiped on sign-out) so the app opens on it next time, and
 * [resume] restarts the sync of every downloaded property then. Which properties are downloaded is read from the
 * sync (what is on the device).
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

    /** The sync being followed for each property. */
    private val follows = mutableMapOf<String, Job>()

    /** The current property waiting to be written to [cache]; written in order on the IO dispatcher, never on the caller's thread. */
    private val toSave = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            val saved = cache.read(RECENT)?.takeIf { it.isNotBlank() }
            val onDevice = onDevice()
            state.update { it.copy(currentId = it.currentId ?: saved, downloadedIds = it.downloadedIds + onDevice, restored = true) }
        }
        scope.launch {
            toSave.filterNotNull().collect { id ->
                // Skipped when signed out meanwhile, so the wiped cache doesn't get it back.
                if (toSave.value == id) runCatching { cache.write(RECENT, id) }
            }
        }
    }

    @Synchronized
    override fun start(propertyId: String) {
        if (state.value.active[propertyId].inProgress) return
        set(propertyId, PropertyDownload.Running(0f, 0.0, 0.0))
        sync.download(propertyId)
        follow(propertyId, showProgress = true, showFailure = true)
    }

    @Synchronized
    override fun open(propertyId: String) {
        if (propertyId !in state.value.downloadedIds) return
        makeCurrent(propertyId)
        // It already syncs in the background; this only starts it again if it stopped or failed.
        if (follows[propertyId]?.isActive != true) {
            sync.download(propertyId)
            follow(propertyId, showProgress = false, showFailure = true)
        }
    }

    override fun resume() {
        scope.launch {
            val s = state.first { it.restored }
            s.currentId?.let { sync.select(it) }
            reopen(s.downloadedIds + setOfNotNull(s.currentId))
        }
    }

    /** Starts syncing every one of [propertyIds] again; the engine reuses each database already on the device. */
    @Synchronized
    private fun reopen(propertyIds: Set<String>) {
        for (id in propertyIds) {
            if (follows[id]?.isActive == true) continue
            sync.download(id)
            follow(id, showProgress = false, showFailure = false)
        }
    }

    /** Makes [propertyId] the property the app works on (and reads), and keeps it for the next start. */
    @Synchronized
    private fun makeCurrent(propertyId: String) {
        if (state.value.currentId == propertyId) return
        state.update { it.copy(currentId = propertyId) }
        save(propertyId)
        sync.select(propertyId)
    }

    @Synchronized
    private fun follow(propertyId: String, showProgress: Boolean, showFailure: Boolean) {
        follows[propertyId]?.cancel()
        follows[propertyId] = scope.launch { followSync(propertyId, showProgress, showFailure) }
    }

    /** Follows the sync of [propertyId]; [showProgress] shows the download's progress and tick, [showFailure] a failure. */
    private suspend fun followSync(propertyId: String, showProgress: Boolean, showFailure: Boolean) {
        val mine = sync.states.map { it[propertyId] }
        // The engine never got going (no account yet, or it failed before reporting a size).
        val started = withTimeoutOrNull(START_TIMEOUT_MS) {
            mine.first { it != null && (it !is SyncState.Downloading || it.totalMb != null) }
        }
        if (started == null) {
            if (showFailure) failed(propertyId)
            return
        }
        val end = mine.first { s ->
            val busy = s is SyncState.Downloading || s is SyncState.Unpacking
            if (busy && showProgress) show(propertyId, s)
            !busy
        }
        when (end) {
            SyncState.Ready -> {
                state.update { it.copy(downloadedIds = it.downloadedIds + propertyId) }
                // The first property downloaded is opened; later ones wait until the user opens them.
                if (state.value.currentId == null) makeCurrent(propertyId)
                if (!showProgress) return
                set(propertyId, PropertyDownload.Finished)
                delay(FINISHED_SHOWN_MS)
                remove(propertyId)
            }
            is SyncState.Failed -> {
                refreshOnDevice(propertyId)
                if (showFailure) set(propertyId, PropertyDownload.Failed) else remove(propertyId)
            }
            // Stopped (signed out): nothing to show any more.
            else -> remove(propertyId)
        }
    }

    private fun failed(propertyId: String) {
        refreshOnDevice(propertyId)
        set(propertyId, PropertyDownload.Failed)
    }

    /** Re-reads which properties are on the device; [propertyId] stops being current when its data is gone. */
    private fun refreshOnDevice(propertyId: String) {
        val onDevice = onDevice()
        val lost = state.value.currentId == propertyId && propertyId !in onDevice
        state.update { it.copy(downloadedIds = onDevice, currentId = it.currentId.takeIf { !lost }) }
        if (lost) sync.select(null)
    }

    @Synchronized
    override fun clear() {
        follows.values.forEach { it.cancel() }
        follows.clear()
        toSave.value = null
        state.value = PropertyDownloads(restored = true)
    }

    private fun show(propertyId: String, s: SyncState?) {
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

    private fun onDevice(): Set<String> = runCatching { sync.downloadedProperties() }.getOrDefault(emptySet())

    /** Keeps [propertyId] as the current property (written in the background). Not kept, the app just opens on the property list. */
    private fun save(propertyId: String) {
        toSave.value = propertyId
    }

    private fun set(propertyId: String, d: PropertyDownload) = state.update { it.copy(active = it.active + (propertyId to d)) }

    private fun remove(propertyId: String) = state.update { it.copy(active = it.active - propertyId) }

    private companion object {
        const val RECENT = "recent_property"
        const val FINISHED_SHOWN_MS = 1_400L
        const val START_TIMEOUT_MS = 30_000L
    }
}
