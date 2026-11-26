package com.sclera.sclerago.domain.model

/** The kinds of physical tag an asset can carry. */
enum class TagKind { QR, BARCODE, NFC }

/**
 * The kind of tag a field such as "QR Tagged", "Barcode Tagged" or "NFC Tagged" records (some properties have these
 * fields; any capitalisation or spacing), or null for any other field. The form doesn't show them: the Tags bar shows
 * the asset's tag columns instead.
 */
fun tagKindOfField(name: String): TagKind? = when (name.filter(Char::isLetterOrDigit).lowercase()) {
    "qrtagged" -> TagKind.QR
    "barcodetagged" -> TagKind.BARCODE
    "nfctagged" -> TagKind.NFC
    else -> null
}

/** The tag read off a sticker or chip. */
class TagValue private constructor(
    val kind: TagKind,
    val id: String,
    /** A QR code issued by Sclera, which must already exist on the server; everything else is the customer's own sticker. */
    val isSystem: Boolean,
) {
    override fun equals(other: Any?) = other is TagValue && kind == other.kind && id == other.id && isSystem == other.isSystem
    override fun hashCode() = 31 * (31 * kind.hashCode() + id.hashCode()) + isSystem.hashCode()

    /** The id is a tag's content; keep it out of logs. */
    override fun toString() = "TagValue($kind)"

    companion object {
        /** Marks a Sclera-issued QR code: the text is a link and the id is what follows `?id=`. */
        private const val SYSTEM_MARKER = "?id="

        /** A QR code read from its text. A Sclera link keeps only the id; any other text is the customer's own code. */
        fun qr(text: String): TagValue {
            val trimmed = text.trim().takeIf { it.isNotEmpty() } ?: throw TaggingException(TaggingError.EMPTY_TAG)
            val at = trimmed.indexOf(SYSTEM_MARKER)
            if (at < 0) return TagValue(TagKind.QR, trimmed, isSystem = false)
            val id = trimmed.substring(at + SYSTEM_MARKER.length).takeIf { it.isNotEmpty() } ?: throw TaggingException(TaggingError.EMPTY_TAG)
            return TagValue(TagKind.QR, id, isSystem = true)
        }

        fun barcode(text: String): TagValue {
            val trimmed = text.trim().takeIf { it.isNotEmpty() } ?: throw TaggingException(TaggingError.EMPTY_TAG)
            return TagValue(TagKind.BARCODE, trimmed, isSystem = false)
        }

        /** An NFC tag read from its content. A blank chip can't be tagged, and one that holds a Sclera link belongs to the main VDMS system. */
        fun nfc(text: String, isBlank: Boolean, vdmsPrefix: String): TagValue {
            if (isBlank) throw TaggingException(TaggingError.BLANK_NFC)
            val trimmed = text.trim().takeIf { it.isNotEmpty() } ?: throw TaggingException(TaggingError.EMPTY_TAG)
            if (trimmed.startsWith(vdmsPrefix)) throw TaggingException(TaggingError.TAKEN_BY_VDMS)
            return TagValue(TagKind.NFC, trimmed, isSystem = false)
        }
    }
}

/** Whether the server already knows a tag, and whether someone holds it. */
enum class TagUse { FREE, IN_USE, UNKNOWN }

/** What the server answers when asked about a tag. */
data class TagCheck(
    val use: TagUse,
    /** The tag is held by an Asset Data Collection record, not by the main VDMS system. */
    val isAdc: Boolean,
    /** The asset that holds the tag now. */
    val heldBy: String?,
    /** The tag belongs to a different property. */
    val isOnOtherProperty: Boolean,
    /** How many tags the holder has (at least one when another asset holds the tag). */
    val holderTagCount: Int,
    /** Whether the user is asked before the tag is taken from a draft that holds it (online and offline). */
    val confirmsMove: Boolean,
)

/** How many tags each asset is left with after a tag is written. */
data class TagUpdate(val newAssetCount: Int, val previousAssetCount: Int)

/** Which way a QR code faces: yaw, pitch and roll in degrees, with how far the measurement can be trusted. */
data class QrMeasurement(
    /** 0-360 degrees, compass-locked. */
    val yaw: Double,
    /** -90 to +90 degrees. */
    val pitch: Double,
    /** 0-360 degrees, in-plane rotation. */
    val roll: Double,
    val confidence: Double,
    val method: String,
)

/** What a QR code records about how it was photographed: the way it faces (none for a barcode or NFC tag) and whether it can be moved. */
data class TagPlacement(val direction: QrMeasurement? = null, val isMovable: Boolean = false)

/** The photo taken while scanning a QR code, as JPEG. */
class TagPhoto(val data: ByteArray)

/** Everything the server needs to write a tag. */
class TagWrite(
    val tag: TagValue,
    val asset: String,
    val property: String,
    /** The asset the tag moves from, when it is being moved. */
    val previousAsset: String?,
    val placement: TagPlacement,
    val photo: TagPhoto?,
)

/** Why a tag was not attached. */
enum class TaggingError {
    EMPTY_TAG,
    /** A blank NFC chip has nothing to identify it. */
    BLANK_NFC,
    /** A Sclera QR code the server doesn't know. */
    UNKNOWN_ID,
    TAKEN_BY_VDMS,
    TAKEN_BY_OTHER_PROPERTY,
    /** The asset holding the tag has been submitted, so the tag can't leave it. */
    TAKEN_BY_SUBMITTED,
    /** Another draft asset holds the tag; it can be moved after the user agrees (see [TaggingException.heldBy]). */
    NEEDS_MOVE,
    REGISTRATION_FAILED,
    /** Another device claimed the tag in the meantime. */
    CLAIMED_ELSEWHERE,
    /** The server's answer could not be read. */
    INVALID_RESPONSE,
    /** A QR code tagged offline needs its photo, which is sent to the server later. */
    PHOTO_REQUIRED,

    /** Barcodes and NFC tags can only be tagged with a connection. */
    NEEDS_CONNECTION,
}

/** A tag was not attached; [error] says why. [heldBy] is the asset that holds the tag for [TaggingError.NEEDS_MOVE]. */
class TaggingException(val error: TaggingError, val heldBy: String? = null) : Exception(error.name)

/** Why the camera could not start. */
enum class TagScannerFailure { CAMERA_DENIED, UNAVAILABLE }

/** Why an NFC tag could not be read. */
enum class NfcFailure { UNAVAILABLE, DISABLED, TIMED_OUT, FAILED }
