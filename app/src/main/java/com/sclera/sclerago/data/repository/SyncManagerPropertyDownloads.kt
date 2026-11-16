package com.sclera.sclerago.data.repository

import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.services.syncManager.SyncManager
import com.sclera.sclerago.services.syncManager.SyncManagerService
import com.sclera.sclerago.services.syncManager.utils.SyncUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** The asset sync's state of one property, as [SyncManager] reports it (it has no callbacks, so it is polled). */
data class SyncSnapshot(val propertyId: String?, val status: Int, val progress: Int, val downloadedMb: Double, val totalMb: Double)

/** The sync manager calls, behind an interface so the polling can be tested without the real sync. */
interface AssetSync {
    /** The running sync's state; null before asset sync has been started. */
    fun snapshot(): SyncSnapshot?

    /** Downloads [propertyId]'s offline data through `initAssetDataSync()`. */
    fun download(propertyId: String)
}

/** [AssetSync] over the legacy [SyncManagerService] / [SyncManager] (one property's database at a time). */
class LegacyAssetSync @Inject constructor() : AssetSync {
    override fun snapshot(): SyncSnapshot? = SyncUtils.dbInstance?.let {
        SyncSnapshot(it.propertyId, it.isSynced, it.progress, it.totalMBDownloaded, it.totalFileSizeMB)
    }

    override fun download(propertyId: String) {
        // Downloads the property's database and keeps it in sync (switching from another property if needed).
        SyncManagerService.initAssetDataSync(JSONObject().put("propertyId", propertyId))
    }
}

/**
 * Property downloads through the asset sync: [start] hands the property to `initAssetDataSync()` and then
 * follows the sync's progress until it has the database (done) or gives up (failed).
 */
@Singleton
class SyncManagerPropertyDownloads internal constructor(
    private val sync: AssetSync,
    private val time: TimeProvider,
    io: CoroutineDispatcher,
) : PropertyDownloadRepository {

    @Inject constructor(sync: LegacyAssetSync, time: TimeProvider, @IoDispatcher io: CoroutineDispatcher) :
        this(sync as AssetSync, time, io)

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val state = MutableStateFlow(PropertyDownloads())
    override val downloads: StateFlow<PropertyDownloads> = state.asStateFlow()

    override fun refresh() {
        scope.launch { state.update { it.copy(downloadedId = downloadedNow() ?: it.downloadedId) } }
    }

    override fun start(propertyId: String) {
        if (state.value.active[propertyId] is PropertyDownload.Running) return
        set(propertyId, PropertyDownload.Running(0f, 0.0, 0.0))
        scope.launch {
            try {
                sync.download(propertyId)
                follow(propertyId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                set(propertyId, PropertyDownload.Failed)
            }
        }
    }

    private suspend fun follow(propertyId: String) {
        val startedAt = time.nowMillis()
        while (true) {
            delay(POLL_MS)
            val s = sync.snapshot()?.takeIf { it.propertyId == propertyId }
            when {
                s?.status == SYNCED -> {
                    state.update { it.copy(downloadedId = propertyId) }
                    set(propertyId, PropertyDownload.Finished)
                    delay(FINISHED_SHOWN_MS)
                    state.update { it.copy(active = it.active - propertyId) }
                    return
                }
                s?.status == FAILED -> {
                    set(propertyId, PropertyDownload.Failed)
                    return
                }
                // The sync never got going (no account yet, or it failed before reporting anything).
                (s == null || s.status == NOT_STARTED) && time.nowMillis() - startedAt > START_TIMEOUT_MS -> {
                    set(propertyId, PropertyDownload.Failed)
                    return
                }
                s != null -> set(propertyId, PropertyDownload.Running((s.progress / 100f).coerceIn(0f, 1f), s.downloadedMb, s.totalMb))
            }
        }
    }

    private fun downloadedNow(): String? = sync.snapshot()?.takeIf { it.status == SYNCED }?.propertyId

    private fun set(propertyId: String, d: PropertyDownload) = state.update { it.copy(active = it.active + (propertyId to d)) }

    internal companion object {
        // SyncManager.isSynced: 0 not started, 1 downloading, 2 has the database, 3 failed, 4 unzipping.
        const val NOT_STARTED = 0
        const val SYNCED = 2
        const val FAILED = 3

        const val POLL_MS = 250L
        const val FINISHED_SHOWN_MS = 1_400L
        const val START_TIMEOUT_MS = 30_000L
    }
}
