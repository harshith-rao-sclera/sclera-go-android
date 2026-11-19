package com.sclera.sclerago.data.remote.tagging

import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagUse
import kotlinx.serialization.Serializable

/** The server wraps every answer in `data`. */
@Serializable
data class TagCheckEnvelopeDto(val data: TagCheckDto? = null)

@Serializable
data class TagCheckDto(
    /** 0 free, 1 already in use, 2 unknown to the server. */
    val isTagged: Int? = null,
    val isAdc: Boolean? = null,
    val collectionId: String? = null,
    /** 1 when the tag belongs to a different property. */
    val isTaggedToProperty: Int? = null,
    val existingCollectionIdCount: Int? = null,
) {
    /** The answer as the app understands it, or null when the server left out what the rules need. */
    fun toDomain(): TagCheck? {
        val use = when (isTagged) {
            STATE_FREE -> TagUse.FREE
            STATE_IN_USE -> TagUse.IN_USE
            STATE_UNKNOWN -> TagUse.UNKNOWN
            else -> return null
        }
        // An unknown tag has no holder, so only a known one needs to say whose it is.
        if (use != TagUse.UNKNOWN && isAdc == null) return null
        val holder = collectionId?.takeIf { it.isNotEmpty() }
        return TagCheck(
            use = use,
            isAdc = isAdc ?: false,
            heldBy = holder,
            isOnOtherProperty = isTaggedToProperty == STATE_IN_USE,
            // A holder named without a count still holds the tag.
            holderTagCount = existingCollectionIdCount ?: if (holder != null) 1 else 0,
            confirmsMove = true,
        )
    }

    private companion object {
        const val STATE_FREE = 0
        const val STATE_IN_USE = 1
        const val STATE_UNKNOWN = 2
    }
}

/** The server can answer HTTP 200 with its own status in the body; 826 means someone else got the tag. */
data class TagUpdateEnvelopeDto(val status: Int? = null, val data: TagUpdateDto? = null)

@Serializable
data class TagUpdateDto(val newCollectionIdCount: Int? = null, val existingCollectionIdCount: Int? = null)

/** The key names each tag kind uses to register a customer's own tag: its id, and who added it. */
internal fun registrationKeys(kind: TagKind): Pair<String, String> = when (kind) {
    TagKind.QR -> "clientQrCodeId" to "addedBy"
    TagKind.BARCODE -> "clientBarCodeId" to "addedBy"
    TagKind.NFC -> "nfc_id" to "createdBy"
}
