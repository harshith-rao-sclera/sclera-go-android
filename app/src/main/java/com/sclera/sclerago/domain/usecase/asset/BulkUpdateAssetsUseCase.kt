package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.assetLock
import com.sclera.sclerago.domain.model.missingRequiredFields
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * Of the ticked assets, how many ([count] of [total]) would still be missing a mandatory field after a bulk update, and
 * which of the sheet's fields those are ([fieldNames]), for the web's "Update assets?".
 */
data class BulkIncomplete(val count: Int, val total: Int, val fieldNames: List<String>)

/**
 * As the web's bulk update asks first: a bulk update only fills fields (one left empty stays as it is on each asset), so
 * an asset is still incomplete when one of its empty mandatory [fields] (the sheet's) isn't given a value in [values], or
 * it is missing one the sheet doesn't show (one of its own fields). Null when every asset would be complete.
 */
class BulkIncompleteUseCase @Inject constructor(private val repo: AssetRepository) {
    suspend operator fun invoke(ids: Collection<String>, values: Map<String, String>, fields: List<FieldCfg>): BulkIncomplete? {
        val filled = values.filterValues { it.isNotBlank() }.keys
        val details = repo.detailsFor(ids)
        val byId = repo.catalog.value.assets.associateBy { it.id }
        val names = LinkedHashSet<String>()
        var count = 0
        ids.forEach { id ->
            val empty = missingRequiredFields(fields, details[id]?.fields.orEmpty())
            val left = empty.filter { it.key !in filled }
            // Missing one the sheet doesn't show: the asset says a mandatory field is empty, but none of the sheet's is.
            val elsewhere = byId[id]?.requiredMissing == true && empty.isEmpty()
            if (left.isNotEmpty() || elsewhere) {
                count++
                left.forEach { names += it.label }
            }
        }
        return if (count == 0) null else BulkIncomplete(count, ids.size, names.toList())
    }
}

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
