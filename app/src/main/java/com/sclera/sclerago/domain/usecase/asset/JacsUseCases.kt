package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.JacsType
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.ClassificationRepository
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.isServerId
import javax.inject.Inject

/** Searches the JACS catalog for the asset's JACS code (`GET /jacs-codes`), as anyone who can collect may. */
class SearchJacsCodesUseCase @Inject constructor(private val repo: JacsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(query: String, cursor: String? = null): JacsPage {
        guard.requireOnline(Permission.COLLECT)
        return repo.search(query.trim(), cursor)
    }
}

/**
 * The type a picked JACS code sets, as the web does: the property's master format for the code
 * (`GET /properties/{id}/jacs-codes/{code}/master-format`); null when it has none. Nothing is saved.
 */
class JacsTypeUseCase @Inject constructor(private val repo: JacsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(code: String): JacsType? {
        guard.requireOnline(Permission.COLLECT)
        return repo.typeFor(code.trim())
    }
}

/**
 * Up to three JACS codes for a saved asset, best first, as the web's AI button beside an empty JACS code
 * (`POST /properties/{id}/classification/suggest` with `assetId`): ranked from the asset's saved answers and photos, for
 * the type [masterFormatId] (the one in the form), or without it for the type the AI thinks the asset is. Nothing is
 * saved. Needs collect, as the server. Throws [OnlineException]: Forbidden without collect, NotFound for an asset the
 * server doesn't have (one made on the phone and not synced yet), Invalid when there is nothing to go on.
 */
class SuggestJacsCodesUseCase @Inject constructor(private val repo: ClassificationRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(assetId: String, masterFormatId: String?): List<JacsSuggestion> {
        guard.requireOnline(Permission.COLLECT)
        if (!isServerId(assetId)) throw OnlineException(OnlineError.NotFound)
        return repo.suggest(ClassifyDetails(), emptyList(), masterFormatId, assetId).jacsCodes
    }
}

/** The JACS use cases, for injecting as one. */
class JacsUseCases @Inject constructor(
    val search: SearchJacsCodesUseCase,
    val typeFor: JacsTypeUseCase,
    val suggest: SuggestJacsCodesUseCase,
)
