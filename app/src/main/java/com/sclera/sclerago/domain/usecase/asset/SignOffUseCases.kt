package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.SignOffOutcome
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AssetSignOffRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * An admin marks submitted assets as validated without a validation run, as the server's `POST …/assets/bulk/validate`:
 * only admins ([DataException] FORBIDDEN otherwise); drafts and exceptions are left as they are (submit them first).
 */
class MarkAssetsValidatedUseCase @Inject constructor(private val repo: AssetSignOffRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(ids: Collection<String>): SignOffOutcome {
        if (!guard.isAdmin()) throw DataException(DataError.FORBIDDEN)
        return repo.markValidated(ids)
    }
}

/**
 * The admin's sign-off for [assets], as the web's signOffActions: offered when any of them is submitted, and only usable
 * when all of them are ([ChangeActionBlock.SIGN_OFF_NOT_SUBMITTED] otherwise). Nothing for anyone else.
 */
class SignOffActionUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(assets: List<Asset>): List<ChangeActionOption> {
        if (!guard.isAdmin() || assets.none { it.st == Status.SUBMITTED }) return emptyList()
        val blocked = ChangeActionBlock.SIGN_OFF_NOT_SUBMITTED.takeUnless { assets.all { it.st == Status.SUBMITTED } }
        return listOf(ChangeActionOption(ChangeAction.MARK_VALIDATED, blocked))
    }
}
