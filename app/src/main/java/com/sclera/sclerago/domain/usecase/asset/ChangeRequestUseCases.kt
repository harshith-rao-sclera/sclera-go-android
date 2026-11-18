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

/** A change-request action on several assets at once (the web's selection bar). */
enum class ChangeAction { MOVE_TO_DRAFT, DECLINE, REQUEST, UPDATE_REQUEST, WITHDRAW }

/** Why an action is offered but can't be used for these assets (the web's disabledReason). */
enum class ChangeActionBlock { NOT_ALL_SUBMITTED, NOT_ALL_REQUESTED }

/** One action offered for the selection; [blocked] says why it can't be used (null: it can). */
data class ChangeActionOption(val action: ChangeAction, val blocked: ChangeActionBlock? = null)

/**
 * The change-request actions for [assets], as the web's changeRequestActions: none unless one of them is submitted or
 * signed off. An admin moves them back to draft (closing their requests) or declines the requests; anyone else asks for
 * a change (or updates their request), and withdraws when every one of them is their own request.
 */
class ChangeRequestActionsUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(assets: List<Asset>): List<ChangeActionOption> {
        if (assets.isEmpty() || assets.none { isLockedFor(it.st, isAdmin = false) }) return emptyList()
        val notSubmitted = ChangeActionBlock.NOT_ALL_SUBMITTED.takeUnless { assets.all { isLockedFor(it.st, isAdmin = false) } }
        val asked = assets.filter { it.changeRequest != null }
        if (guard.isAdmin()) {
            val draft = ChangeActionOption(ChangeAction.MOVE_TO_DRAFT, notSubmitted)
            if (asked.isEmpty()) return listOf(draft)
            val notAll = ChangeActionBlock.NOT_ALL_REQUESTED.takeUnless { asked.size == assets.size }
            return listOf(draft, ChangeActionOption(ChangeAction.DECLINE, notAll))
        }
        val allAsked = asked.size == assets.size
        val request = ChangeActionOption(if (allAsked) ChangeAction.UPDATE_REQUEST else ChangeAction.REQUEST, notSubmitted)
        val allMine = allAsked && assets.all { isMine(guard, it.changeRequest) }
        return if (allMine) listOf(request, ChangeActionOption(ChangeAction.WITHDRAW)) else listOf(request)
    }
}

/**
 * Applies [ChangeAction] to each asset in turn, as the web's dialog does, and says how many it changed and how many were
 * refused or failed: asking (or updating) with [note], declining or withdrawing the request, or moving back to draft.
 */
class ApplyChangeActionUseCase @Inject constructor(
    private val request: RequestChangeUseCase,
    private val close: CloseChangeRequestUseCase,
    private val setStatus: SetAssetStatusUseCase,
) {
    data class Result(val done: Int, val failed: Int)

    suspend operator fun invoke(action: ChangeAction, assets: List<Asset>, note: String = ""): Result {
        var done = 0
        assets.forEach { asset ->
            try {
                when (action) {
                    ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> request(asset.id, note)
                    ChangeAction.DECLINE, ChangeAction.WITHDRAW -> close(asset.id, asset.changeRequest ?: throw DataException(DataError.FAILED))
                    ChangeAction.MOVE_TO_DRAFT -> setStatus(listOf(asset.id), Status.DRAFT)
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
    val apply: ApplyChangeActionUseCase,
    private val guard: PermissionGuard,
    private val access: AccessRepository,
) {
    /** Who made [request]: their name, else their email, from the people kept on this device; null when they aren't there. */
    fun requesterName(request: ChangeRequest?): String? =
        request?.requestedBy?.let { id -> access.people.value.firstOrNull { it.id == id }?.name }

    /** The signed-in user made [request]. */
    fun isMine(request: ChangeRequest?): Boolean = isMine(guard, request)

    fun isAdmin(): Boolean = guard.isAdmin()
}
