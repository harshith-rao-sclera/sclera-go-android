package com.sclera.sclerago.domain.usecase.sync

import com.sclera.sclerago.domain.sync.OfflineSync
import com.sclera.sclerago.domain.sync.SyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Starts downloading and syncing the signed-in user's data (safe to call again). */
class StartOfflineSyncUseCase @Inject constructor(private val sync: OfflineSync) {
    operator fun invoke() = sync.start()
}

/** Tries the download again after it failed. */
class RetryOfflineSyncUseCase @Inject constructor(private val sync: OfflineSync) {
    operator fun invoke() = sync.retry()
}

/** Where the offline data stands: downloading, ready or failed. */
class ObserveSyncStateUseCase @Inject constructor(private val sync: OfflineSync) {
    operator fun invoke(): StateFlow<SyncState> = sync.state
}
