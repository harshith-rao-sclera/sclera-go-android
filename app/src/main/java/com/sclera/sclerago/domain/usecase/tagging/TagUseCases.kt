package com.sclera.sclerago.domain.usecase.tagging

import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagPhoto
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagUse
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.isSubmittedOrSignedOff
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.QrDirectionSource
import com.sclera.sclerago.domain.repository.TagServerInfo
import com.sclera.sclerago.domain.repository.TagHistory
import com.sclera.sclerago.domain.repository.TaggingRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.asset.requireUnlocked
import javax.inject.Inject

/**
 * Whether the signed-in user may tag an asset. A tag sets the asset's QR / barcode / NFC flags, which the server changes
 * only for someone who can collect and may change the asset (its check_editable): an admin any asset, anyone else one
 * assigned to them or to nobody that isn't submitted or signed off; nobody one in Managed Assets.
 */
class CanTagAssetUseCase @Inject constructor(private val assets: AssetRepository, private val guard: PermissionGuard) {
    /** True when [asset] may be tagged by the signed-in user. */
    operator fun invoke(asset: String): Boolean = try {
        requireTaggable(asset)
        true
    } catch (e: DataException) {
        false
    }

    /**
     * Refuses tagging [asset]: [DataException] FORBIDDEN without the collect permission (or while the access isn't
     * known), NOT_ASSIGNED for someone else's, FORBIDDEN for a submitted one (not an admin), MANAGED in Managed Assets.
     * An asset this device doesn't know yet (a new one) is left to the server.
     */
    fun requireTaggable(asset: String) {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(assets, guard, listOf(asset))
    }
}

/**
 * The rules for attaching a tag to an asset: ask the server about the tag, accept it, refuse it with a reason, or ask the
 * user to move it from another draft, then write it.
 */
class AttachTagUseCase @Inject constructor(
    private val repo: TaggingRepository,
    private val history: TagHistory,
    private val canTag: CanTagAssetUseCase,
) {
    /**
     * Attaches [tag] to [asset]; first refuses ([DataException], see [CanTagAssetUseCase.requireTaggable]) an asset the
     * signed-in user may not change, so nothing is written offline that the server would refuse. With [isMoveConfirmed] a
     * tag held by another draft is taken from it; without, that case throws
     * [TaggingError.NEEDS_MOVE] so the user can be asked first. [isSubmitted] says whether an asset was submitted (an asset the
     * phone doesn't know counts as not submitted; the server has the last word when the tag is written).
     */
    suspend operator fun invoke(
        tag: TagValue,
        asset: String,
        property: String,
        placement: TagPlacement = TagPlacement(),
        photo: TagPhoto? = null,
        isMoveConfirmed: Boolean = false,
        isSubmitted: suspend (String) -> Boolean = { false },
    ): TagUpdate {
        canTag.requireTaggable(asset)
        val check = repo.check(tag, asset, property)
        val previous = if (isMoveConfirmed) {
            check.heldBy?.takeIf { it != asset }
        } else {
            register(tag, asset, property, check)
            holderToMoveFrom(check, tag, asset, isSubmitted)
        }
        return repo.write(TagWrite(tag, asset, property, previous ?: check.heldBy, placement, photo))
    }

    /** A tag the server has never seen is refused when Sclera issued it, and registered when it is the customer's. */
    private suspend fun register(tag: TagValue, asset: String, property: String, check: TagCheck) {
        when {
            check.use == TagUse.UNKNOWN -> {
                if (tag.isSystem) throw TaggingException(TaggingError.UNKNOWN_ID)
                repo.register(tag, asset, property)
            }
            check.use == TagUse.IN_USE && !check.isAdc -> throw TaggingException(TaggingError.TAKEN_BY_VDMS)
        }
    }

    /**
     * Refuses a tag that can't move, and returns the asset to move it from when the user must be asked. The order is the old app's
     * (its TaggingService): a QR code on another property is refused first, whoever holds it; a barcode or NFC tag only once it is held
     * by another asset. A submitted holder is refused before an unsubmitted one is offered for a move.
     */
    private suspend fun holderToMoveFrom(check: TagCheck, tag: TagValue, asset: String, isSubmitted: suspend (String) -> Boolean): String? {
        if (tag.kind == TagKind.QR && check.isOnOtherProperty) throw TaggingException(TaggingError.TAKEN_BY_OTHER_PROPERTY)
        val holder = check.heldBy
        if (check.use != TagUse.IN_USE || !check.isAdc || check.holderTagCount < 1 || holder == null || holder == asset) return null
        if (tag.kind == TagKind.QR) {
            if (isSubmitted(holder)) throw TaggingException(TaggingError.TAKEN_BY_SUBMITTED, holder)
        } else {
            if (isSubmitted(holder)) throw TaggingException(TaggingError.TAKEN_BY_SUBMITTED, holder)
            if (check.isOnOtherProperty) throw TaggingException(TaggingError.TAKEN_BY_OTHER_PROPERTY)
        }
        if (check.confirmsMove) {
            // The old app wrote this line when it asked, before the user answered.
            history.retagRequested(asset, tag.kind)
            throw TaggingException(TaggingError.NEEDS_MOVE, holder)
        }
        return holder
    }
}

/** What a scan gives: the text read, and (for a QR code) the photo of it and the direction measured while it was scanned (null when the device cannot measure one). */
class TagScan(val text: String, val photo: ByteArray?, val direction: QrMeasurement? = null)

/**
 * Turns what the scanner or the NFC reader read into a [TagValue] with its placement: a QR code also gets its direction, the one the
 * scanner measured, or [direction]'s for a device that cannot measure.
 */
class BuildScannedTagUseCase @Inject constructor(private val direction: QrDirectionSource, private val server: TagServerInfo) {
    /** The tag and its placement for a [kind] scan. Throws [TaggingException] for text that holds nothing. */
    suspend operator fun invoke(kind: TagKind, scan: TagScan, isMovable: Boolean): Pair<TagValue, TagPlacement> = when (kind) {
        TagKind.QR -> TagValue.qr(scan.text) to TagPlacement(scan.direction ?: direction.measure(), isMovable)
        TagKind.BARCODE -> TagValue.barcode(scan.text) to TagPlacement()
        TagKind.NFC -> throw IllegalArgumentException("NFC tags are built from the chip's content: use nfc()")
    }

    /** The tag for an NFC chip's content (no placement). A chip already holding a link to the Sclera server belongs to the main VDMS. */
    fun nfc(text: String, isBlank: Boolean): TagValue = TagValue.nfc(text, isBlank, server.url)
}

/** Whether an asset was submitted, which a tag on it can't leave. An asset this device doesn't know counts as not submitted. */
class IsAssetSubmittedUseCase @Inject constructor(private val assets: AssetRepository) {
    operator fun invoke(asset: String): Boolean =
        assets.catalog.value.assets.firstOrNull { it.id == asset }?.st?.let(::isSubmittedOrSignedOff) ?: false
}

/** The name of the asset that holds a tag, for telling the user whose it is; null when this device doesn't know the asset. */
class TagHolderNameUseCase @Inject constructor(private val assets: AssetRepository) {
    operator fun invoke(asset: String): String? = assets.catalog.value.assets.firstOrNull { it.id == asset }?.name?.takeIf { it.isNotBlank() }
}

/**
 * The property a tag is checked and written for: the asset's own, or (assets read from the open property's database carry none) the
 * property being worked on, as the old app did. The server decides "tagged to another property" from it, so it must not be empty.
 */
class TagPropertyUseCase @Inject constructor(private val downloads: PropertyDownloadRepository) {
    operator fun invoke(assetProperty: String): String = assetProperty.ifBlank { downloads.downloads.value.currentId.orEmpty() }
}

/** The tagging use cases, for injecting as one. */
class TagUseCases @Inject constructor(
    val attach: AttachTagUseCase,
    val build: BuildScannedTagUseCase,
    val isSubmitted: IsAssetSubmittedUseCase,
    val holderName: TagHolderNameUseCase,
    val property: TagPropertyUseCase,
    val canTag: CanTagAssetUseCase,
)
