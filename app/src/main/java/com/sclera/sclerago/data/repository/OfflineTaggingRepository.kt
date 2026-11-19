package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.directionJson
import com.sclera.sclerago.data.offline.QrTagStore
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagUse
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.repository.TaggingRepository
import javax.inject.Inject

/**
 * Tags a QR code without a connection: the phone's own tables answer, and the change waits for the sync. Barcodes and NFC tags
 * have no local copy, so they need a connection.
 */
class OfflineTaggingRepository @Inject constructor(
    private val tags: QrTagStore,
    private val credentials: SyncCredentialsProvider,
) : TaggingRepository {
    override suspend fun check(tag: TagValue, asset: String, property: String): TagCheck {
        if (tag.kind != TagKind.QR) throw TaggingException(TaggingError.NEEDS_CONNECTION)
        // A code the phone has never seen is free: it is added to the tables when it is tagged.
        val row = tags.row(tag) ?: return TagCheck(TagUse.FREE, isAdc = true, heldBy = null, isOnOtherProperty = false, holderTagCount = 0, confirmsMove = true)
        val holder = row.assetId?.takeIf { it.isNotEmpty() }
        return TagCheck(
            use = if (holder == null && row.isAdc) TagUse.FREE else TagUse.IN_USE,
            isAdc = row.isAdc,
            heldBy = holder,
            isOnOtherProperty = !row.propertyId.isNullOrEmpty() && row.propertyId != property,
            holderTagCount = if (holder == null) 0 else 1,
            confirmsMove = true,
        )
    }

    /** Offline a new code is added when it is tagged, so there is nothing to register. */
    override suspend fun register(tag: TagValue, asset: String, property: String) = Unit

    override suspend fun write(write: TagWrite): TagUpdate {
        if (write.tag.kind != TagKind.QR) throw TaggingException(TaggingError.NEEDS_CONNECTION)
        val photo = write.photo ?: throw TaggingException(TaggingError.PHOTO_REQUIRED)
        tags.save(write, photo.data, write.placement.direction?.let(::directionJson).orEmpty(), credentials.credentials.value?.email.orEmpty())
        // The asset the code came from is left with whatever other codes it still holds.
        val previous = write.previousAsset?.takeIf { it != write.asset }
        return TagUpdate(newAssetCount = 1, previousAssetCount = previous?.let(tags::tagCount) ?: 0)
    }
}
