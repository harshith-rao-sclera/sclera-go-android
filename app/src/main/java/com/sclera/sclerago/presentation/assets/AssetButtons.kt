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
    val assign: UiText? = null,
    val moveToManaged: UiText? = null,
    val comment: UiText? = null,
    val removeOthersComment: UiText? = null,
)

/** [AssetButtonBlocks] as what a tap on each greyed-out button says. */
fun AssetButtonBlocks.toUi(): AssetButtons = AssetButtons(
    change = change.text(), collect = collect.text(), validate = validate.text(), signOff = signOff.text(),
    moveToDraft = moveToDraft.text(), requestChange = requestChange.text(), approve = approve.text(), decline = decline.text(),
    withdraw = withdraw.text(), assign = assign.text(), moveToManaged = moveToManaged.text(), comment = comment.text(),
    removeOthersComment = removeOthersComment.text(),
)

private fun ButtonBlock?.text(): UiText? = when (this) {
    null -> null
    is ButtonBlock.Missing -> block.reason()
    is ButtonBlock.Locked -> lock.reason()
    ButtonBlock.NotYourRequest -> UiText.Res(R.string.access_withdraw_not_yours)
}
