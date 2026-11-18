package com.sclera.sclerago.data.sync

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.sync.OfflineSync
import com.sclera.sclerago.domain.sync.SyncFailure
import com.sclera.sclerago.domain.sync.SyncState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OfflineSync] on top of the Java sync engine: starts an engine per property with the signed-in user's credentials
 * (they all keep syncing, like inspection's per vdms), turns each one's progress fields into [SyncState] by polling
 * while it downloads, and keeps their token fresh.
 */
@Singleton
class AdcSyncController @Inject constructor(
    private val bridge: SyncManagerBridge,
    private val credentialsProvider: SyncCredentialsProvider,
    private val network: NetworkMonitor,
    private val invalidations: TableInvalidations,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : OfflineSync {
    private val _states = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    override val states: StateFlow<Map<String, SyncState>> = _states.asStateFlow()

    private val runJobs = mutableMapOf<String, Job>()
    private var tokenJob: Job? = null

    init {
        bridge.setTablesChangedListener(invalidations::emit)
    }

    @Synchronized
    override fun download(propertyId: String) {
        if (runJobs[propertyId]?.isActive == true) return
        val restart = _states.value[propertyId] is SyncState.Failed
        launchRun(propertyId, restart)
    }

    /** Safe on the main thread: the engine and disk work runs on [io], once the signed-in user's credentials are known. */
    override fun reopenAll() {
        scope.launch(io) {
            if (bridge.usesTestDatabase()) return@launch
            // No credentials (signed out meanwhile, or the session never loaded): nothing is started.
            val credentials = withTimeoutOrNull(CREDENTIALS_WAIT_MS) { credentialsProvider.credentials.filterNotNull().first() } ?: return@launch
            runCatching { bridge.reopenAll(credentials) }
        }
    }

    /** Safe on the main thread: only records the choice (no engine or disk work). */
    override fun select(propertyId: String?) {
        bridge.select(propertyId)
        // The screens now read another database.
        invalidations.emitAll()
    }

    override fun downloadedProperties(): Set<String> = bridge.downloadedProperties()

    @Synchronized
    override fun stop() {
        runJobs.values.forEach { it.cancel() }
        runJobs.clear()
        tokenJob?.cancel()
        tokenJob = null
        _states.value = emptyMap()
    }

    /** Only updates state and launches here: callers may be on the main thread, so all engine and disk work runs on [io]. */
    private fun launchRun(propertyId: String, restart: Boolean) {
        setState(propertyId, SyncState.Downloading())
        if (tokenJob?.isActive != true) tokenJob = scope.launch(io) { pushRefreshedCredentials() }
        runJobs[propertyId] = scope.launch(io) {
            // Checking for the debug test database may open it.
            if (bridge.usesTestDatabase()) {
                setState(propertyId, SyncState.Ready)
                invalidations.emitAll()
                return@launch
            }
            // Right after an app start the session may still be loading.
            val credentials = withTimeoutOrNull(CREDENTIALS_WAIT_MS) { credentialsProvider.credentials.filterNotNull().first() }
            if (credentials == null) {
                setState(propertyId, SyncState.Failed(SyncFailure.SESSION_EXPIRED))
                return@launch
            }
            if (restart) bridge.restart(credentials, propertyId) else bridge.begin(credentials, propertyId)
            watchEngine(propertyId)
        }
    }

    /** Forwards every new token, session or organisation to the engines (the first value is what they were started with). */
    private suspend fun pushRefreshedCredentials() {
        var first = true
        credentialsProvider.credentials.filterNotNull().distinctUntilChanged().collect {
            if (first) first = false else bridge.updateCredentials(it)
        }
    }

    private suspend fun watchEngine(propertyId: String) {
        while (currentCoroutineContext().isActive) {
            val next = classify(propertyId, bridge.snapshot(propertyId))
            setState(propertyId, next)
            when (next) {
                SyncState.Ready -> {
                    if (bridge.isSelected(propertyId)) invalidations.emitAll()
                    return
                }
                is SyncState.Failed -> return
                else -> delay(POLL_MS)
            }
        }
    }

    private fun setState(propertyId: String, state: SyncState) = _states.update { it + (propertyId to state) }

    private fun classify(propertyId: String, s: EngineSnapshot): SyncState = when {
        !s.hasInstance -> SyncState.Failed(failureReason(propertyId))
        s.readable -> SyncState.Ready
        s.status == ENGINE_FAILED -> SyncState.Failed(failureReason(propertyId))
        // Unzipping, then (synced but not readable yet) pulling the table configuration.
        s.status == ENGINE_UNZIPPING || s.status == ENGINE_SYNCED -> SyncState.Unpacking
        s.totalMb > 0.0 -> SyncState.Downloading(s.progressPercent.takeIf { it > 0 }, s.downloadedMb, s.totalMb)
        else -> SyncState.Downloading()
    }

    private fun failureReason(propertyId: String): SyncFailure = when {
        credentialsProvider.credentials.value == null -> SyncFailure.SESSION_EXPIRED
        !network.isOnline() -> SyncFailure.NO_NETWORK
        bridge.storageLow(propertyId) -> SyncFailure.STORAGE_FULL
        else -> SyncFailure.SERVER_ERROR
    }

    private companion object {
        const val POLL_MS = 500L
        const val CREDENTIALS_WAIT_MS = 10_000L
        const val ENGINE_SYNCED = 2
        const val ENGINE_FAILED = 3
        const val ENGINE_UNZIPPING = 4
    }
}
