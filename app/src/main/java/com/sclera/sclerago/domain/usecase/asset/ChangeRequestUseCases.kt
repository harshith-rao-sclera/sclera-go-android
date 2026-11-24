package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.isLockedFor
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
        if (status != null && !isLockedFor(status, reviewer = false)) throw DataException(DataError.FAILED)
        repo.request(assetId, note.trim().take(CHANGE_NOTE_MAX).ifEmpty { null })
    }
}

/**
 * Closes a change request without reopening the asset, as the server's `DELETE …/change-request`: a reviewer
 * (Validate) declines it, or whoever asked withdraws it; nobody else may ([DataException] FORBIDDEN).
 */
class CloseChangeRequestUseCase @Inject constructor(private val repo: ChangeRequestRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, request: ChangeRequest) {
        val mine = isMine(guard, request)
        val reviewer = guard.isReviewer()
        if (!reviewer && !mine) throw DataException(DataError.FORBIDDEN)
        repo.close(assetId, declined = reviewer && !mine)
    }
}

private fun isMine(guard: PermissionGuard, request: ChangeRequest?): Boolean =
    request?.requestedBy != null && request.requestedBy == guard.userId()

/**
 * Moves assets back to draft, as the server's PATCH `status: drafted`, which also approves a change request on them (each
 * goes to whoever asked, and the request closes). Needs Collect. A reviewer (Validate) moves any of them; anyone else
 * only what they may change: assigned to them or to nobody, and not submitted ([DataException] NOT_ASSIGNED, FORBIDDEN
 * or MANAGED otherwise; nothing moves).
 */
class ReopenAssetsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>) {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(repo, guard, ids)
        repo.setStatus(ids, Status.DRAFT)
    }
}

/**
 * A status action on several assets at once (the web's selection bar). APPROVE: a reviewer's yes to a change request
 * (back to draft, assigned to whoever asked). MARK_VALIDATED: a reviewer signs submitted assets off without a validation
 * run.
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
 * - a reviewer (Validate), when every one has a request: approve them (back to draft, to whoever asked) or decline them.
 *   Otherwise they move submitted, signed-off and exception assets back to draft, and decline the requests there are;
 * - anyone else: ask for a change (or update their requests), and withdraw when every one is their own request.
 */
class ChangeRequestActionsUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(assets: List<Asset>): List<ChangeActionOption> {
        if (assets.isEmpty()) return emptyList()
        // Asking for a change and moving back to draft both need Collect (the server's rule): a group without it gets
        // none, admins included (the web's mayCollect).
        if (!guard.allows(Permission.COLLECT)) return emptyList()
        val locked = { a: Asset -> isLockedFor(a.st, reviewer = false) }
        val exception = { a: Asset -> a.st == Status.EXCEPTION }
        if (assets.none(locked)) {
            if (assets.none(exception)) return emptyList()
            val blocked = when {
                !assets.all(exception) -> ChangeActionBlock.NOT_ALL_EXCEPTIONS
                !guard.isReviewer() && assets.any { guard.isAssignedElsewhere(it.assignedTo) } -> ChangeActionBlock.ASSIGNED_ELSEWHERE
                else -> null
            }
            return listOf(ChangeActionOption(ChangeAction.MOVE_TO_DRAFT, blocked))
        }
        val notSubmitted = ChangeActionBlock.NOT_ALL_SUBMITTED.takeUnless { assets.all(locked) }
        val asked = assets.filter { it.changeRequest != null }
        val allAsked = asked.size == assets.size
        val allMine = allAsked && assets.all { isMine(guard, it.changeRequest) }
        val withdraw = ChangeActionOption(ChangeAction.WITHDRAW)
        // A reviewer (Validate) changes any asset, so moves submitted ones back to draft and answers requests.
        if (guard.isReviewer()) {
            if (allAsked) return listOf(ChangeActionOption(ChangeAction.APPROVE), ChangeActionOption(ChangeAction.DECLINE))
            // Submitted, signed-off and exception assets go back together.
            val blocked = ChangeActionBlock.SOME_ARE_DRAFTS.takeUnless { assets.all { locked(it) || exception(it) } }
            val draft = ChangeActionOption(ChangeAction.MOVE_TO_DRAFT, blocked)
            if (asked.isEmpty()) return listOf(draft)
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

    /**
     * The signed-in user is a reviewer (Validate): they change a submitted asset, move it back to draft and decline
     * change requests.
     */
    fun isReviewer(): Boolean = guard.isReviewer()
}
