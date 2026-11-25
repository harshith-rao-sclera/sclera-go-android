package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.usecase.asset.AssetButtonBlocks
import com.sclera.sclerago.domain.usecase.asset.ButtonBlock
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
    /** Assign (a reviewer picks who has the asset); anyone else sees it greyed out, saying only a reviewer assigns. */
    val assign: UiText? = null,
    /** Assign to me (someone who collects and isn't a reviewer takes the asset). */
    val take: UiText? = null,
    val moveToManaged: UiText? = null,
    val comment: UiText? = null,
    val removeOthersComment: UiText? = null,
)

/**
 * [AssetButtonBlocks] as what a tap on each greyed-out button says. Buttons that only some users use still show for
 * everyone ([reviewer]: the signed-in user is a reviewer; [mine]: they made the asset's change request), greyed out with
 * why: Assign for anyone but a reviewer, Request change for a reviewer (who changes the asset instead), Decline on one's
 * own request (Withdraw takes it back).
 */
fun AssetButtonBlocks.toUi(reviewer: Boolean, mine: Boolean): AssetButtons = AssetButtons(
    change = change.text(), collect = collect.text(), validate = validate.text(), signOff = signOff.text(),
    moveToDraft = moveToDraft.text(),
    requestChange = requestChange.text() ?: UiText.Res(R.string.access_request_change_reviewer).takeIf { reviewer },
    approve = approve.text(),
    decline = decline.text() ?: UiText.Res(R.string.access_decline_own_request).takeIf { mine },
    withdraw = withdraw.text(),
    assign = assign.text() ?: UiText.Res(R.string.assign_admin_only).takeUnless { reviewer },
    take = assign.text(),
    moveToManaged = moveToManaged.text(), comment = comment.text(),
    removeOthersComment = removeOthersComment.text(),
)

private fun ButtonBlock?.text(): UiText? = when (this) {
    null -> null
    is ButtonBlock.Missing -> block.reason()
    is ButtonBlock.Locked -> lock.reason()
    ButtonBlock.NotYourRequest -> UiText.Res(R.string.access_withdraw_not_yours)
}
