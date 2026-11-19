package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AddFieldOutcome
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.OWN_FIELD_KINDS
import com.sclera.sclerago.domain.model.OWN_FIELD_NAME_MAX
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.AssetOwnFieldsRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * Adds a field to one asset only, as the web's "Add field" (For this asset only) and the server's
 * `POST /assets/{id}/fields`: anyone who can collect may, on an asset they can edit; a name the asset's form already
 * has is refused. The field then shows at the end of the asset's fields, empty, to fill in like the rest.
 */
class AddAssetFieldUseCase @Inject constructor(
    private val repo: AssetOwnFieldsRepository,
    private val assets: AssetRepository,
    private val guard: PermissionGuard,
) {
    /** The signed-in user may add fields to assets. */
    fun allowed(): Boolean = guard.allows(Permission.COLLECT)

    suspend operator fun invoke(assetId: String, name: String, kind: FieldKind): AddFieldOutcome {
        val trimmed = name.trim().take(OWN_FIELD_NAME_MAX)
        if (trimmed.isEmpty()) return AddFieldOutcome.NoName
        require(kind in OWN_FIELD_KINDS) { "Not a type an asset's own field can have" }
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(assets, guard, listOf(assetId))
        return repo.add(assetId, trimmed, kind)?.let(AddFieldOutcome::Added) ?: AddFieldOutcome.NameTaken
    }
}

/**
 * Removes a field that belongs to one asset alone, with its answer, as the server's `DELETE /assets/{id}/fields/{id}`:
 * anyone who can collect may, on an asset they can edit. Fields every asset has are never removed here.
 */
class RemoveAssetFieldUseCase @Inject constructor(
    private val repo: AssetOwnFieldsRepository,
    private val assets: AssetRepository,
    private val guard: PermissionGuard,
) {
    /** Removes asset [assetId]'s own field [key]; false when it isn't one of the asset's own fields. */
    suspend operator fun invoke(assetId: String, key: String): Boolean {
        guard.requireOffline(Permission.COLLECT)
        requireUnlocked(assets, guard, listOf(assetId))
        return repo.remove(assetId, key)
    }
}
