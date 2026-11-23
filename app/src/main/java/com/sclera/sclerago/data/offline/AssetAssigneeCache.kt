package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncManagerBridge
import com.sclera.sclerago.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Sets `assigned_to` where it differs; no version bump or history line, as the server already made that change. */
private const val ASSET_MIRROR_ASSIGNEE =
    "UPDATE assets SET assigned_to = ? WHERE id = ? AND COALESCE(assigned_to, '') <> COALESCE(?, '')"

/**
 * The phone's copy of who has each asset (`assets.assigned_to`), set straight after the server assigned them, as the tag
 * flags are after a tag is written: the list, the people filter and who may work on an asset follow at once instead of
 * waiting for the sync to pull the change. Only a cache of what the server will send: a failure leaves it to the sync.
 */
class AssetAssigneeCache @Inject constructor(
    private val db: AdcDatabase,
    private val bridge: SyncManagerBridge,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** [assetIds] of [propertyId] are now [userId]'s (null: nobody's), when that property's database is the open one. */
    suspend fun mark(propertyId: String, assetIds: Collection<String>, userId: String?) = withContext(io) {
        runCatching {
            if (assetIds.isEmpty() || !bridge.isSelected(propertyId) || !db.isOpen || !db.hasColumn("assets", "assigned_to")) return@runCatching
            db.inTransaction {
                assetIds.forEach { id -> execute(ASSET_MIRROR_ASSIGNEE, listOf(userId, id, userId), setOf("assets")) }
            }
        }
        Unit
    }
}
