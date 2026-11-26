package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.AssetTagFlagStore
import com.sclera.sclerago.data.offline.QrTagStore
import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.TagHistory
import com.sclera.sclerago.domain.repository.TaggingRepository
import javax.inject.Inject

/**
 * Tags through the server when the phone is online, and in its own tables (QR codes only) when it is not. Either way, what the
 * old app did after a tag is done here: the assets' tagged / untagged flags follow the counts the write answered with, and a QR
 * tag adds a history line. A QR code the server took also gets its placement (movable or fixed) in the phone's own row, as an
 * offline tag does.
 */
class ConnectionAwareTaggingRepository @Inject constructor(
    private val network: NetworkMonitor,
    private val online: TaggingRepositoryImpl,
    private val offline: OfflineTaggingRepository,
    private val flags: AssetTagFlagStore,
    private val history: TagHistory,
    private val qrTags: QrTagStore,
) : TaggingRepository {
    private val current: TaggingRepository get() = if (network.isOnline()) online else offline

    override suspend fun check(tag: TagValue, asset: String, property: String): TagCheck = current.check(tag, asset, property)
    override suspend fun register(tag: TagValue, asset: String, property: String) = current.register(tag, asset, property)

    override suspend fun write(write: TagWrite): TagUpdate {
        val isOnline = network.isOnline()
        val update = (if (isOnline) online else offline).write(write)
        val kind = write.tag.kind
        // Offline the row was written with its placement already; online the server has it, and the phone's copy follows now
        // (the next sync would bring it anyway, so a missing database does not fail the tag).
        if (isOnline && kind == TagKind.QR) runCatching { qrTags.setMovable(write.tag, write.placement.isMovable) }
        // The asset is tagged while it holds one or more of this kind; the asset the tag came from keeps its flag while it still holds others.
        flags.mark(write.asset, kind, update.newAssetCount != 0)
        write.previousAsset?.takeIf { it.isNotEmpty() && it != write.asset }?.let { flags.mark(it, kind, update.previousAssetCount != 0) }
        if (kind == TagKind.QR) history.qrTagged(write.asset)
        return update
    }
}
