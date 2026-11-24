package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.assetLock
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * Editing several assets at once (the web's bulk Edit): the [values] given a value and the [place] when one is chosen
 * go on every asset in [ids]; fields left empty stay as they are on each asset. As the server, a submitted or
 * validated asset, or one assigned to someone else, can only be changed by an admin, so those are left out and counted
 * in [Result.skipped].
 */
class BulkUpdateAssetsUseCase @Inject constructor(private val repo: AssetRepository, private val guard: PermissionGuard) {

    data class Result(val updated: Int, val skipped: Int)

    suspend operator fun invoke(ids: Collection<String>, values: Map<String, String>, place: LocValue?): Result {
        guard.requireOffline(Permission.COLLECT)
        val given = values.filterValues { it.isNotBlank() }
        if (given.isEmpty() && place == null) return Result(0, 0)
        val reviewer = guard.isReviewer()
        val byId = repo.catalog.value.assets.associateBy { it.id }
        // As the server's check_editable: someone who isn't a reviewer changes only unlocked assets assigned to them or to nobody.
        val editable = ids.filter { id -> byId[id]?.let { assetLock(it.st, reviewer, guard.isAssignedElsewhere(it.assignedTo)) == null } == true }
        // Values and place together, so each asset gets one history line for the edit, as the server's PATCH.
        editable.forEach { repo.saveDetails(it, given, place = place) }
        return Result(editable.size, ids.size - editable.size)
    }
}
