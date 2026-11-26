package com.sclera.sclerago.presentation.assets

import androidx.annotation.StringRes
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.TakeRefusal
import com.sclera.sclerago.domain.usecase.asset.AssetButtonBlocks
import com.sclera.sclerago.domain.usecase.asset.ButtonBlock
import com.sclera.sclerago.domain.usecase.asset.lacksPermission
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.model.reason

/**
 * Why each of the open asset's buttons is greyed out, as what a tap on it says (null: it can be used). See
 * [AssetButtonBlocks] for which button each one is.
 */
data class AssetButtons(
    val change: UiText? = null,
    val collect: UiText? = null,
    val validate: UiText? = null,
    val signOff: UiText? = null,
    val moveToDraft: UiText? = null,
    val requestChange: UiText? = null,
    val approve: UiText? = null,
    val decline: UiText? = null,
    val withdraw: UiText? = null,
    /** Assign (a reviewer picks who has the asset); left out for anyone else ([hidden]). */
    val assign: UiText? = null,
    /** Assign to me (someone who collects and isn't a reviewer takes the asset). */
    val take: UiText? = null,
    val moveToManaged: UiText? = null,
    val comment: UiText? = null,
    val removeOthersComment: UiText? = null,
    /** The asset bar's actions left out because the user's group doesn't allow them ([lacksPermission]). */
    val hidden: Set<BarAction> = emptySet(),
)

/** An action of the asset bar that can be left out for the signed-in user (see [AssetButtons.hidden]). */
enum class BarAction { VALIDATE, SIGN_OFF, ASSIGN, TAKE, SUBMIT, MOVE_TO_MANAGED, MOVE_TO_DRAFT, REQUEST_CHANGE, DELETE, APPROVE, DECLINE }

/**
 * [AssetButtonBlocks] as what a tap on each greyed-out button says ([reviewer]: the signed-in user is a reviewer). On the
 * asset bar and the change request banner, an action the user's group doesn't allow is left out ([AssetButtons.hidden]):
 * Assign for anyone but a reviewer, Request change for a reviewer (who changes the asset instead), and any whose
 * permission is missing (Approve and Decline included). One blocked by the asset's state (submitted, someone else's) or a
 * passing state (access still loading, off site) stays, greyed out with why.
 */
fun AssetButtonBlocks.toUi(reviewer: Boolean): AssetButtons = AssetButtons(
    change = change.text(), collect = collect.text(), validate = validate.text(), signOff = signOff.text(),
    moveToDraft = moveToDraft.text(),
    requestChange = requestChange.text(),
    approve = approve.text(),
    decline = decline.text(),
    withdraw = withdraw.text(),
    assign = assign.text(),
    take = take.text(),
    moveToManaged = moveToManaged.text(), comment = comment.text(),
    removeOthersComment = removeOthersComment.text(),
    hidden = buildSet {
        if (validate.lacksPermission) add(BarAction.VALIDATE)
        if (signOff.lacksPermission) add(BarAction.SIGN_OFF)
        // Not a reviewer: no Assign, unless the access is still being fetched (then it is greyed out as still checking).
        if (assign.lacksPermission || (!reviewer && !assign.stillChecking)) add(BarAction.ASSIGN)
        if (take.lacksPermission) add(BarAction.TAKE)
        if (change.lacksPermission) addAll(listOf(BarAction.SUBMIT, BarAction.DELETE))
        if (moveToManaged.lacksPermission) add(BarAction.MOVE_TO_MANAGED)
        if (moveToDraft.lacksPermission) add(BarAction.MOVE_TO_DRAFT)
        if (requestChange.lacksPermission || reviewer) add(BarAction.REQUEST_CHANGE)
        if (approve.lacksPermission) add(BarAction.APPROVE)
        if (decline.lacksPermission) add(BarAction.DECLINE)
    },
)

/** The access is still being fetched, so whether the user's group allows the action isn't known yet. */
private val ButtonBlock?.stillChecking: Boolean get() = (this as? ButtonBlock.Missing)?.block == AccessBlock.CHECKING

private fun ButtonBlock?.text(): UiText? = when (this) {
    null -> null
    is ButtonBlock.Missing -> block.reason()
    is ButtonBlock.Locked -> lock.reason()
    ButtonBlock.NotYourRequest -> UiText.Res(R.string.access_withdraw_not_yours)
    is ButtonBlock.TakeRefused -> UiText.Res(refusal.message(many = false))
    ButtonBlock.NotYourException -> UiText.Res(R.string.change_block_not_your_exception)
}

/** What a tap on a greyed-out "Assign to me" says for [TakeRefusal] (the web's takeReason), for one asset or [many]. */
@StringRes
fun TakeRefusal.message(many: Boolean): Int = when (this) {
    TakeRefusal.ALREADY_ASSIGNED -> if (many) R.string.assign_take_some_assigned else R.string.assign_take_assigned
    TakeRefusal.ALREADY_YOURS -> if (many) R.string.assign_take_some_yours else R.string.assign_take_yours
    TakeRefusal.SUBMITTED -> if (many) R.string.assign_take_some_submitted else R.string.assign_take_submitted
}
