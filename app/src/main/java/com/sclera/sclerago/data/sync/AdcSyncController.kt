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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OfflineSync] on top of the Java sync engine: starts it for a property with the signed-in user's credentials,
 * turns its progress fields into [SyncState] by polling while it downloads, and keeps its token fresh.
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
    private val _state = MutableStateFlow<SyncState>(SyncState.NotStarted)
    override val state: StateFlow<SyncState> = _state.asStateFlow()

    private var runJob: Job? = null
    private var tokenJob: Job? = null
    private var propertyId: String? = null

    init {
        bridge.setTablesChangedListener(invalidations::emit)
    }

    @Synchronized
    override fun download(propertyId: String) {
        if (propertyId == this.propertyId && runJob?.isActive == true) return
        val restart = _state.value is SyncState.Failed
        runJob?.cancel()
        this.propertyId = propertyId
        launchRun(propertyId, restart)
    }

    @Synchronized
    override fun stop() {
        runJob?.cancel()
        tokenJob?.cancel()
        runJob = null
        tokenJob = null
        propertyId = null
        _state.value = SyncState.NotStarted
    }

    private fun launchRun(propertyId: String, restart: Boolean) {
        if (bridge.usesTestDatabase()) {
            _state.value = SyncState.Ready
            invalidations.emitAll()
            return
        }
        _state.value = SyncState.Downloading()
        if (tokenJob?.isActive != true) tokenJob = scope.launch(io) { pushRefreshedCredentials() }
        runJob = scope.launch(io) {
            // Right after an app start the session may still be loading.
            val credentials = withTimeoutOrNull(CREDENTIALS_WAIT_MS) { credentialsProvider.credentials.filterNotNull().first() }
            if (credentials == null) {
                _state.value = SyncState.Failed(SyncFailure.SESSION_EXPIRED)
                return@launch
            }
            // The engine drops the previous property's database itself when the property changes.
            if (restart) bridge.restart(credentials, propertyId) else bridge.begin(credentials, propertyId)
            watchEngine()
        }
    }

    /** Forwards every new token, session or organisation to the engine (the first value is what it was started with). */
    private suspend fun pushRefreshedCredentials() {
        var first = true
        credentialsProvider.credentials.filterNotNull().distinctUntilChanged().collect {
            if (first) first = false else bridge.updateCredentials(it)
        }
    }

    private suspend fun watchEngine() {
        while (scope.isActive) {
            val next = classify(bridge.snapshot())
            _state.value = next
            when (next) {
                SyncState.Ready -> { invalidations.emitAll(); return }
                is SyncState.Failed -> return
                else -> delay(POLL_MS)
            }
        }
    }

    private fun classify(s: EngineSnapshot): SyncState = when {
        !s.hasInstance -> SyncState.Failed(failureReason())
        s.readable -> SyncState.Ready
        s.status == ENGINE_FAILED -> SyncState.Failed(failureReason())
        // Unzipping, then (synced but not readable yet) pulling the table configuration.
        s.status == ENGINE_UNZIPPING || s.status == ENGINE_SYNCED -> SyncState.Unpacking
        s.totalMb > 0.0 -> SyncState.Downloading(s.progressPercent.takeIf { it > 0 }, s.downloadedMb, s.totalMb)
        else -> SyncState.Downloading()
    }

    private fun failureReason(): SyncFailure = when {
        credentialsProvider.credentials.value == null -> SyncFailure.SESSION_EXPIRED
        !network.isOnline() -> SyncFailure.NO_NETWORK
        bridge.storageLow() -> SyncFailure.STORAGE_FULL
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
