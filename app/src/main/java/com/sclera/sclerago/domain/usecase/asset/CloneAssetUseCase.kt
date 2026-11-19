package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ClonePlace
import com.sclera.sclerago.domain.model.CloneRequest
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.MAX_CLONE_COPIES
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/**
 * Clone, as the web's: makes [CloneRequest.copies] new draft assets from [source], each taking only what was picked (its
 * place down to the level chosen, the form values, its type) and the original's own fields by name. Photos are never
 * copied (as the web's clone): each copy starts without any. Each copy gets no name of its own, as on the web. They are
 * written to the offline database and synced as new assets. The server keeps no "cloned" mark, so the copies are
 * ordinary drafts.
 */
class CloneAssetUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val ids: IdGenerator,
    private val guard: PermissionGuard,
) {
    /** Makes the copies; returns how many. Throws [DataException] (FORBIDDEN, FAILED). */
    suspend operator fun invoke(source: Asset, request: CloneRequest): Int {
        guard.requireOffline(Permission.COLLECT)
        if (request.copies !in 1..MAX_CLONE_COPIES) throw DataException(DataError.FAILED)
        val place = request.place
        val keepsPlace = place != ClonePlace.NONE
        val copies = List(request.copies) {
            val asset = Asset(
                id = ids.next(), name = "", detail = source.detail,
                loc = source.loc.takeIf { keepsPlace }, where = if (keepsPlace) source.where else "",
                src = Source.MANUAL, st = Status.DRAFT, type = source.type, propertyId = source.propertyId, media = emptyList(),
                buildingId = source.buildingId.takeIf { keepsPlace },
                floorId = source.floorId.takeIf { place == ClonePlace.FLOOR || place == ClonePlace.LOCATION },
                locationId = source.locationId.takeIf { place == ClonePlace.LOCATION },
                masterFormatId = source.masterFormatId.takeIf { request.keepType },
            )
            NewAsset(asset, request.values, copyOf = source.id)
        }
        repo.add(copies)
        return copies.size
    }
}
