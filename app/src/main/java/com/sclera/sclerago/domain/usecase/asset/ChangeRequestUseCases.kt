package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.isLockedFor
import com.sclera.sclerago.domain.model.isSubmittedOrSignedOff
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.ChangeRequestRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/** The longest note a change request takes (the server's `ChangeRequestIn.note`). */
const val CHANGE_NOTE_MAX = 500

/**
 * Asks an admin to reopen a submitted asset, as the server's `POST …/change-request`: anyone who can collect, only on a
 * submitted (or signed-off) asset; the note is trimmed and capped. Asking again replaces the note.
 */
class RequestChangeUseCase @Inject constructor(
    private val repo: ChangeRequestRepository,
    private val assets: AssetRepository,
    private val guard: PermissionGuard,
) {
    suspend operator fun invoke(assetId: String, note: String) {
        guard.requireOffline(Permission.COLLECT)
        requireNotManaged(assets, listOf(assetId))
        val status = assets.catalog.value.assets.firstOrNull { it.id == assetId }?.st
        // Not submitted: it can be edited as it is, so there is nothing to ask for.
        if (status != null && !isLockedFor(status, isAdmin = false)) throw DataException(DataError.FAILED)
        repo.request(assetId, note.trim().take(CHANGE_NOTE_MAX).ifEmpty { null })
    }
}

/**
 * Closes a change request without reopening the asset, as the server's `DELETE …/change-request`: an admin declines
 * it, or whoever asked withdraws it; nobody else may ([DataException] FORBIDDEN).
 */
class CloseChangeRequestUseCase @Inject constructor(private val repo: ChangeRequestRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, request: ChangeRequest) {
        val mine = isMine(guard, request)
        val admin = guard.isAdmin()
        if (!admin && !mine) throw DataException(DataError.FORBIDDEN)
        repo.close(assetId, declined = admin && !mine)
    }
}

private fun isMine(guard: PermissionGuard, request: ChangeRequest?): Boolean =
    request?.requestedBy != null && request.requestedBy == guard.userId()

/**
 * Moves assets back to draft, as the server's PATCH `status: drafted`, which also approves a change request on them (each
 * goes to whoever asked, and the request closes). Needs Collect. An admin moves any of them. A reviewer (Validate) moves a
 * submitted or signed-off one whoever it is assigned to (the server's "reopening", as Recollect does in a validation run);
 * anything else, an exception say, only as anyone may change it: assigned to them or to nobody ([DataException]
 * NOT_ASSIGNED, FORBIDDEN or MANAGED otherwise; nothing moves).
 */
class ReopenAssetsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>) {
        guard.requireOffline(Permission.COLLECT)
        requireNotManaged(repo, ids)
        val reviewer = guard.allows(Permission.VALIDATE)
        val wanted = ids.toSet()
        val reopening: Set<String> = if (!reviewer) emptySet() else {
            repo.catalog.value.assets.filter { it.id in wanted && isSubmittedOrSignedOff(it.st) }.mapTo(HashSet()) { it.id }
        }
        requireUnlocked(repo, guard, wanted - reopening)
        repo.setStatus(ids, Status.DRAFT)
    }
}

/**
 * A status action on several assets at once (the web's selection bar). APPROVE: an admin's or a reviewer's yes to a change
 * request (back to draft, assigned to whoever asked). MARK_VALIDATED: a reviewer or an admin signs submitted assets off
 * without a validation run.
 */
enum class ChangeAction { MARK_VALIDATED, APPROVE, MOVE_TO_DRAFT, DECLINE, REQUEST, UPDATE_REQUEST, WITHDRAW }

/** Why an action is offered but can't be used for these assets (the web's disabledReason). */
enum class ChangeActionBlock {
    NOT_ALL_SUBMITTED, NOT_ALL_REQUESTED, NOT_ALL_EXCEPTIONS, ASSIGNED_ELSEWHERE, SOME_ARE_DRAFTS, SIGN_OFF_NOT_SUBMITTED,
}

/** One action offered for the selection; [blocked] says why it can't be used (null: it can). */
data class ChangeActionOption(val action: ChangeAction, val blocked: ChangeActionBlock? = null)

/**
 * The change-request actions for [assets], as the web's changeRequestActions:
 * - only exceptions (nothing submitted): anyone who may work on them moves them back to draft, no request needed;
 * - an admin or a reviewer (Validate), when every one has a request: approve them (back to draft, to whoever asked); an
 *   admin may decline them instead, a reviewer may withdraw them when every one is their own. Otherwise they move
 *   submitted, signed-off and exception assets back to draft (a reviewer only the exceptions assigned to them or to
 *   nobody), and an admin declines the requests there are;
 * - anyone else: ask for a change (or update their requests), and withdraw when every one is their own request.
 */
class ChangeRequestActionsUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(assets: List<Asset>): List<ChangeActionOption> {
        if (assets.isEmpty()) return emptyList()
        // Asking for a change and moving back to draft both need Collect (the server's rule): a group without it gets
        // none, admins included (the web's mayCollect).
        if (!guard.allows(Permission.COLLECT)) return emptyList()
        val locked = { a: Asset -> isLockedFor(a.st, isAdmin = false) }
        val exception = { a: Asset -> a.st == Status.EXCEPTION }
        if (assets.none(locked)) {
            if (assets.none(exception)) return emptyList()
            val blocked = when {
                !assets.all(exception) -> ChangeActionBlock.NOT_ALL_EXCEPTIONS
                !guard.isAdmin() && assets.any { guard.isAssignedElsewhere(it.assignedTo) } -> ChangeActionBlock.ASSIGNED_ELSEWHERE
                else -> null
            }
            return listOf(ChangeActionOption(ChangeAction.MOVE_TO_DRAFT, blocked))
        }
        val notSubmitted = ChangeActionBlock.NOT_ALL_SUBMITTED.takeUnless { assets.all(locked) }
        val asked = assets.filter { it.changeRequest != null }
        val allAsked = asked.size == assets.size
        val allMine = allAsked && assets.all { isMine(guard, it.changeRequest) }
        val withdraw = ChangeActionOption(ChangeAction.WITHDRAW)
        val admin = guard.isAdmin()
        // A reviewer (Validate) moves submitted assets back to draft as an admin does (the server's reopening).
        if (admin || guard.allows(Permission.VALIDATE)) {
            if (allAsked) {
                val approve = ChangeActionOption(ChangeAction.APPROVE)
                // Declining is an admin's; a reviewer who asked themselves can still take the requests back.
                if (!admin) return if (allMine) listOf(approve, withdraw) else listOf(approve)
                return listOf(approve, ChangeActionOption(ChangeAction.DECLINE))
            }
            // An exception is still open, so a reviewer moves only the ones assigned to them or to nobody, like anyone else.
            val blocked = when {
                !assets.all { locked(it) || exception(it) } -> ChangeActionBlock.SOME_ARE_DRAFTS
                !admin && assets.any { exception(it) && guard.isAssignedElsewhere(it.assignedTo) } -> ChangeActionBlock.ASSIGNED_ELSEWHERE
                else -> null
            }
            val draft = ChangeActionOption(ChangeAction.MOVE_TO_DRAFT, blocked)
            if (!admin || asked.isEmpty()) return listOf(draft)
            return listOf(draft, ChangeActionOption(ChangeAction.DECLINE, ChangeActionBlock.NOT_ALL_REQUESTED))
        }
        val request = ChangeActionOption(if (allAsked) ChangeAction.UPDATE_REQUEST else ChangeAction.REQUEST, notSubmitted)
        return if (allMine) listOf(request, withdraw) else listOf(request)
    }
}

/**
 * Applies [ChangeAction] to each asset in turn, as the web's dialog does, and says how many it changed and how many were
 * refused or failed: asking (or updating) with [note], declining or withdrawing the request, or moving back to draft.
 */
class ApplyChangeActionUseCase @Inject constructor(
    private val request: RequestChangeUseCase,
    private val close: CloseChangeRequestUseCase,
    private val reopen: ReopenAssetsUseCase,
    private val markValidated: MarkAssetsValidatedUseCase,
) {
    data class Result(val done: Int, val failed: Int)

    suspend operator fun invoke(action: ChangeAction, assets: List<Asset>, note: String = ""): Result {
        // One sign-off for the lot, as the web's bulk validate: those not signed off (not submitted) count as failed.
        if (action == ChangeAction.MARK_VALIDATED) {
            val done = markValidated(assets.map { it.id }).validated
            return Result(done, assets.size - done)
        }
        var done = 0
        assets.forEach { asset ->
            try {
                when (action) {
                    ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> request(asset.id, note)
                    ChangeAction.DECLINE, ChangeAction.WITHDRAW -> close(asset.id, asset.changeRequest ?: throw DataException(DataError.FAILED))
                    // Back in draft approves an open request: the writer gives the asset to whoever asked.
                    ChangeAction.APPROVE, ChangeAction.MOVE_TO_DRAFT -> reopen(listOf(asset.id))
                    ChangeAction.MARK_VALIDATED -> Unit // signed off above, all at once
                }
                done++
            } catch (_: DataException) {
                // Counted below: the others still go ahead, as on the web.
            }
        }
        return Result(done, assets.size - done)
    }
}

/** The change request use cases, for injecting as one, with who the signed-in user is to a request. */
class ChangeRequestUseCases @Inject constructor(
    val request: RequestChangeUseCase,
    val close: CloseChangeRequestUseCase,
    val actions: ChangeRequestActionsUseCase,
    val signOff: SignOffActionUseCase,
    val markValidated: MarkAssetsValidatedUseCase,
    val apply: ApplyChangeActionUseCase,
    /** Back to draft (Approve, Move to draft) for one asset's own bar, by the same rules as the selection's. */
    val reopen: ReopenAssetsUseCase,
    private val guard: PermissionGuard,
    private val access: AccessRepository,
) {
    /** Who made [request]: their name, else their email, from the people kept on this device; null when they aren't there. */
    fun requesterName(request: ChangeRequest?): String? =
        request?.requestedBy?.let { id -> access.people.value.firstOrNull { it.id == id }?.name }

    /** The signed-in user made [request]. */
    fun isMine(request: ChangeRequest?): Boolean = isMine(guard, request)

    fun isAdmin(): Boolean = guard.isAdmin()

    /** The signed-in user may validate (a reviewer, or an admin): they move a submitted asset back to draft themselves. */
    fun isReviewer(): Boolean = guard.allows(Permission.VALIDATE)
}
