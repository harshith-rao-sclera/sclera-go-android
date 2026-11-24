package com.sclera.sclerago.domain.usecase.integration

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ManagedCounts
import com.sclera.sclerago.domain.model.MoveOutcome
import com.sclera.sclerago.domain.model.MovePreview
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.VdmsAssignee
import com.sclera.sclerago.domain.repository.ManagedAssetsRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.config.ObserveConfigureBlockUseCase
import javax.inject.Inject

// Move to Managed Assets (asset list selection): validated assets go to the property's linked VDMS, as on the web.

/**
 * "Move to Managed assets" is offered when at least one of [assets] is validated (the server moves only those; once moved,
 * an asset is managed in VDMS and read-only here). Only someone with Configure can use it (the server's POST
 * /integration/move): for anyone else it stays greyed out, with the reason from [ObserveConfigureBlockUseCase].
 */
class MoveOfferedUseCase @Inject constructor() {
    operator fun invoke(assets: Collection<Asset>): Boolean = assets.any { it.st == Status.VALIDATED }
}

/** The linked VDMS's network names; a moved asset goes on one of them. */
class GetVdmsNetworksUseCase @Inject constructor(private val repo: ManagedAssetsRepository) {
    suspend operator fun invoke(propertyId: String): List<String> = repo.networks(propertyId)
}

/** People the linked VDMS can assign moved assets to, matching [key]. */
class SearchAssigneesUseCase @Inject constructor(private val repo: ManagedAssetsRepository) {
    suspend operator fun invoke(propertyId: String, key: String): List<VdmsAssignee> = repo.assignees(propertyId, key)
}

/**
 * Moves [assetIds] to Managed Assets on [networkName] (required), assigned to [assignee] (optional email).
 * Returns how many will move (0: none of them could be; null: the server doesn't say), and the buildings, floors and
 * locations VDMS lacked, sent there first. Needs Configure, as the server: anyone else gets [OnlineException]
 * ([OnlineError.Forbidden]) and nothing is sent.
 */
class MoveToManagedAssetsUseCase @Inject constructor(private val repo: ManagedAssetsRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, assetIds: Collection<String>, networkName: String, assignee: String?): MoveOutcome {
        guard.requireOnline(Permission.CONFIGURE)
        val ids = assetIds.filter { it.isNotBlank() }
        if (ids.isEmpty() || networkName.isBlank()) throw OnlineException(OnlineError.Invalid)
        return repo.move(propertyId, ids, networkName, assignee?.takeIf { it.isNotBlank() })
    }
}

/** What moving [assetIds] would take: the buildings, floors and locations VDMS lacks are sent there first. Changes nothing. */
class PreviewMoveUseCase @Inject constructor(private val repo: ManagedAssetsRepository) {
    suspend operator fun invoke(propertyId: String, assetIds: Collection<String>): MovePreview =
        repo.preview(propertyId, assetIds.filter { it.isNotBlank() })
}

/** The Managed Assets Summary for the places in [scope] (the whole property when it names none). */
class GetManagedCountsUseCase @Inject constructor(private val repo: ManagedAssetsRepository) {
    suspend operator fun invoke(propertyId: String, scope: PlaceScope): ManagedCounts = repo.counts(propertyId, scope)
}

/** The Move to Managed Assets use cases, for injecting as one. */
class ManagedAssetsUseCases @Inject constructor(
    val integration: GetIntegrationUseCase,
    val offered: MoveOfferedUseCase,
    val configureBlock: ObserveConfigureBlockUseCase,
    val networks: GetVdmsNetworksUseCase,
    val assignees: SearchAssigneesUseCase,
    val move: MoveToManagedAssetsUseCase,
    val preview: PreviewMoveUseCase,
)
