package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.TakeRefusal
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.assign.TakeAssetsUseCase
import javax.inject.Inject

/**
 * Why the signed-in user can't use one of an asset's buttons (the User Access Guide): a permission their group lacks
 * ([Missing]), the asset's own lock ([Locked]: someone else's, submitted, in Managed Assets, view only), or a change request
 * that isn't theirs to take back ([NotYourRequest]). The button stays where it is, greyed out, and a tap says why.
 */
sealed interface ButtonBlock {
    data class Missing(val block: AccessBlock) : ButtonBlock
    data class Locked(val lock: AssetLock) : ButtonBlock

    /** Withdraw: only whoever asked takes their change request back. */
    data object NotYourRequest : ButtonBlock

    /** Assign to me: the asset already has an assignee, or is submitted or signed off (the web's takeReason). */
    data class TakeRefused(val refusal: TakeRefusal) : ButtonBlock
}

/**
 * The user's group doesn't allow the action ([AccessBlock.lacksPermission]), rather than this asset's state (submitted,
 * someone else's, in Managed Assets) or a passing one: the asset bar leaves such an action out instead of greying it out.
 */
val ButtonBlock?.lacksPermission: Boolean
    get() = when (this) {
        is ButtonBlock.Missing -> block.lacksPermission
        is ButtonBlock.Locked -> lock == AssetLock.NO_PERMISSION
        else -> false
    }

/**
 * Why each of one asset's buttons can't be used now (null: it can). Which buttons show at all is decided by the asset's
 * status (Mark as validated only on a submitted asset, Delete not on a signed-off one); this only says who may use them.
 */
data class AssetButtonBlocks(
    /** Changing the asset: Save, Submit, Delete, its photos, files, documents, tags, AI and voice fill, own fields. */
    val change: ButtonBlock? = null,
    /** Anything that needs Collect alone, whatever the asset (Clone, removing a file that may be removed). */
    val collect: ButtonBlock? = null,
    /** Running its checks (Validate, the Validation tab's run button) and opening its run on the Validation page (Check). */
    val validate: ButtonBlock? = null,
    /** Mark as validated (Validate). */
    val signOff: ButtonBlock? = null,
    /** Move to draft: an exception by whoever may work on it; a submitted or signed-off asset by a reviewer. */
    val moveToDraft: ButtonBlock? = null,
    /** Request change (or update the request) on a submitted asset (Collect). */
    val requestChange: ButtonBlock? = null,
    /** Approve a change request: back to draft for whoever asked (Validate, and Collect here). */
    val approve: ButtonBlock? = null,
    /** Decline a change request (Validate). */
    val decline: ButtonBlock? = null,
    /** Withdraw a change request: only whoever asked. */
    val withdraw: ButtonBlock? = null,
    /** Assign (a reviewer) or Assign to me (anyone else who collects). */
    val assign: ButtonBlock? = null,
    /** Assign to me: [assign], and greyed out on an asset that already has an assignee or is submitted or signed off. */
    val take: ButtonBlock? = null,
    /** Move to Managed assets (Configure). */
    val moveToManaged: ButtonBlock? = null,
    /** Adding a comment (Collect or Validate). */
    val comment: ButtonBlock? = null,
    /** Removing someone else's comment (Validate). */
    val removeOthersComment: ButtonBlock? = null,
)

/**
 * The [AssetButtonBlocks] of [Asset] for the signed-in user, by the User Access Guide: nothing changes an asset in Managed
 * Assets; a submitted or signed-off asset, or one assigned to someone else, is a reviewer's only ([CanEditAssetUseCase]);
 * each other button needs its permission. A new asset isn't saved yet, so creating it needs Collect only. The use cases
 * behind the buttons check again, and the server decides.
 */
class AssetButtonBlocksUseCase @Inject constructor(
    private val guard: PermissionGuard,
    private val canEdit: CanEditAssetUseCase,
    private val takeAssets: TakeAssetsUseCase,
) {
    operator fun invoke(asset: Asset, isNew: Boolean): AssetButtonBlocks {
        val collect = missing(Permission.COLLECT)
        if (isNew) return AssetButtonBlocks(change = collect, collect = collect)
        // A lock that only says Collect is missing gives Collect's own reason (still checking, no group, off premise).
        val lock = canEdit.lock(asset)?.let {
            if (it == AssetLock.NO_PERMISSION || it == AssetLock.OFF_PREMISE) collect ?: ButtonBlock.Locked(it) else ButtonBlock.Locked(it)
        }
        val managed = ButtonBlock.Locked(AssetLock.MANAGED).takeIf { asset.managed }
        val validate = missing(Permission.VALIDATE)
        // A reviewer moves it back to draft (approving any request) unless it can't be changed here at all.
        val reopen = validate ?: lock
        return AssetButtonBlocks(
            change = lock,
            collect = collect,
            validate = managed ?: missing(Permission.CHECK),
            signOff = managed ?: validate,
            moveToDraft = managed ?: if (asset.st == Status.EXCEPTION) lock else reopen,
            requestChange = managed ?: collect,
            approve = managed ?: reopen,
            decline = managed ?: validate,
            withdraw = managed ?: ButtonBlock.NotYourRequest.takeUnless { mine(asset) },
            assign = managed ?: collect,
            take = managed ?: collect ?: takeAssets.refusal(listOf(asset))?.let { ButtonBlock.TakeRefused(it) },
            moveToManaged = managed ?: missing(Permission.CONFIGURE),
            // Collect or Validate comments, as the server.
            comment = managed ?: collect?.takeIf { validate != null },
            removeOthersComment = managed ?: validate,
        )
    }

    private fun missing(permission: Permission): ButtonBlock? = guard.block(permission)?.let { ButtonBlock.Missing(it) }

    private fun mine(asset: Asset): Boolean = asset.changeRequest?.requestedBy?.let { it == guard.userId() } == true
}
