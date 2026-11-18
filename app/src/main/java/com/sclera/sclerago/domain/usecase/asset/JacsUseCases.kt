package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsType
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.JacsRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
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

/** The JACS use cases, for injecting as one. */
class JacsUseCases @Inject constructor(val search: SearchJacsCodesUseCase, val typeFor: JacsTypeUseCase)
