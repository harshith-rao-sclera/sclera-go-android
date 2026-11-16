package com.sclera.sclerago.data.sync

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.sync.OfflineSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On sign-out: stops the sync and deletes every downloaded database, its config rows and the engine's credentials,
 * and tells the screens' data it is gone. Called on the main thread, so the engine and disk work runs on [io]; a
 * new sign-in's download waits for it (the bridge serialises the wipe and the start of a sync).
 */
@Singleton
class SyncDataCleaner @Inject constructor(
    private val sync: OfflineSync,
    private val bridge: SyncManagerBridge,
    private val invalidations: TableInvalidations,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UserDataCleaner {
    override fun clear() {
        sync.stop()
        scope.launch(io) {
            if (bridge.hasLocalData()) bridge.wipe()
            invalidations.emitAll()
        }
    }
}
