package com.sclera.sclerago.data.sync

import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.sync.OfflineSync
import javax.inject.Inject
import javax.inject.Singleton

/** On sign-out: stops the sync and deletes the downloaded database, its config rows and the engine's credentials. */
@Singleton
class SyncDataCleaner @Inject constructor(
    private val sync: OfflineSync,
    private val bridge: SyncManagerBridge,
) : UserDataCleaner {
    override fun clear() {
        sync.stop()
        if (bridge.hasLocalData()) bridge.wipe()
    }
}
