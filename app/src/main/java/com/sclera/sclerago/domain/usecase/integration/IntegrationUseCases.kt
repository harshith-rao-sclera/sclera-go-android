package com.sclera.sclerago.domain.usecase.integration

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.VdmsChoice
import com.sclera.sclerago.domain.model.VdmsLink
import com.sclera.sclerago.domain.repository.IntegrationRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// The Integration page (side menu → Integration): the property's link to a VDMS (Managed Assets).

/** Whether the signed-in user may see and change the integration (the server's configure permission). */
class IntegrationAccessUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Boolean = guard.allows(Permission.CONFIGURE)
}

class GetIntegrationUseCase @Inject constructor(private val repo: IntegrationRepository) {
    suspend operator fun invoke(propertyId: String): VdmsLink? = repo.link(propertyId)
}

/** The VDMS that can be linked, matching [key] (searched by VDMS id at the server). Needs [Permission.CONFIGURE]. */
class SearchVdmsUseCase @Inject constructor(private val repo: IntegrationRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, key: String): List<VdmsChoice> {
        guard.requireOnline(Permission.CONFIGURE)
        return repo.available(propertyId, key)
    }
}

/** Links the property to a VDMS picked from the available ones; it can't be changed afterwards. Needs [Permission.CONFIGURE]. */
class LinkVdmsUseCase @Inject constructor(private val repo: IntegrationRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, vdmsId: String): VdmsLink {
        guard.requireOnline(Permission.CONFIGURE)
        if (vdmsId.isBlank()) throw OnlineException(OnlineError.Invalid)
        return repo.linkVdms(propertyId, vdmsId)
    }
}

/**
 * "+ Integration" is offered only to someone who may configure the property, as the server needs for searching and
 * linking (the web tests the plain "user" role, which gives the same answer on its Configure-only page).
 */
class CanAddIntegrationUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Boolean = guard.allows(Permission.CONFIGURE)
}

/** The Integration use cases, for injecting as one. */
class IntegrationUseCases @Inject constructor(
    val canAdd: CanAddIntegrationUseCase,
    val get: GetIntegrationUseCase,
    val search: SearchVdmsUseCase,
    val link: LinkVdmsUseCase,
)
