package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.isSubmittedOrSignedOff
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import javax.inject.Inject

/** A selection action only a reviewer may use, shown greyed out to someone else with why ([block]). */
data class ReviewerOnlyAction(val action: ChangeAction, val block: AccessBlock)

/**
 * The selection's reviewer-only actions (the User Access Guide: Mark as validated and Move to draft on submitted assets,
 * Approve and Decline on change requests) for someone whose group lacks Validate, so they stay on the Change menu greyed
 * out. Empty for a reviewer (they get the real actions); listed for anyone else, whether or not they can collect.
 */
class ReviewerOnlyActionsUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(assets: List<Asset>): List<ReviewerOnlyAction> {
        val block = guard.block(Permission.VALIDATE) ?: return emptyList()
        if (assets.isEmpty()) return emptyList()
        return buildList {
            if (assets.any { it.st == Status.SUBMITTED }) add(ChangeAction.MARK_VALIDATED)
            // Exceptions alone go back to draft without a reviewer (the selection already offers that).
            if (assets.any { isSubmittedOrSignedOff(it.st) }) add(ChangeAction.MOVE_TO_DRAFT)
            if (assets.any { it.changeRequest != null }) {
                add(ChangeAction.APPROVE)
                add(ChangeAction.DECLINE)
            }
        }.map { ReviewerOnlyAction(it, block) }
    }
}
