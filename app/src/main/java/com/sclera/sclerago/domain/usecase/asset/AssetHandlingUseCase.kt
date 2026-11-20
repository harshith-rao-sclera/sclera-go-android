package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * A user an asset names: their [name] and [email] from the people kept on this device (null when they aren't there),
 * and whether it is the signed-in user.
 */
data class AssetPerson(val name: String?, val me: Boolean, val email: String? = null)

/** Who has an asset and who submitted it, and when (null: nobody / not submitted). */
data class AssetHandling(val assignedTo: AssetPerson?, val submittedBy: AssetPerson?, val submittedAt: Long?)

/** The history actions the server records when an asset is submitted (`activity.SUBMITTED`, `activity.EXCEPTION`). */
private val SUBMIT_ACTIONS = setOf("submitted", "exception")

/** The statuses of a submitted asset: submitted, submitted with exception, and saved / signed off. */
private val SUBMITTED = setOf(Status.SUBMITTED, Status.EXCEPTION, Status.VALIDATED)

/**
 * Who an asset is assigned to (`assets.assigned_to`), and, while it is submitted, who submitted it and when: the latest
 * submit line of its history ([activity], newest first), as the web's "Submitted by" and "Submitted on".
 */
class AssetHandlingUseCase @Inject constructor(
    private val access: AccessRepository,
    private val guard: PermissionGuard,
    private val auth: AuthRepository,
    private val assignees: AssetAssignmentRepository,
) {
    operator fun invoke(asset: Asset, activity: List<AssetActivity>): AssetHandling {
        val submit = if (asset.st in SUBMITTED) {
            activity.firstOrNull { it.kind == ActivityKind.HISTORY && it.action in SUBMIT_ACTIONS }
        } else null
        return AssetHandling(person(asset.assignedTo), person(submit?.createdBy), submit?.createdAt)
    }

    /**
     * Found by the asset's user id: the organisation's users (an admin's, held in memory while the app runs), else the
     * assignees kept on the device for every user (`property_assignees`, as the web names them, so this shows offline
     * too), and, for the signed-in user, their own sign-in email.
     */
    private fun person(userId: String?): AssetPerson? = userId?.takeIf { it.isNotBlank() }?.let { id ->
        val known = access.people.value.firstOrNull { it.id == id }
        val assignee = if (known == null) assignees.keptAssignees()[id] else null
        val me = id == guard.userId()
        val myEmail = if (me) (auth.state.value as? AuthState.SignedIn)?.session?.email else null
        AssetPerson(known?.name ?: assignee?.name, me, (known?.email ?: assignee?.email ?: myEmail)?.takeIf { it.isNotBlank() })
    }
}
