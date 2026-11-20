package com.sclera.sclerago.domain.usecase.integration

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.PlacesPush
import com.sclera.sclerago.domain.model.VdmsPlaces
import com.sclera.sclerago.domain.repository.VdmsPlacesRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// Buildings, floors and locations on the Integration page: they don't sync with the linked VDMS, so they are sent there
// or imported from there by hand, as on the web. Sending, importing and refreshing need the configure permission.

/** "Send to VDMS" and "Import from VDMS" are turned off for the plain "user" role, as on the web. */
class CanImportPlacesUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Boolean = !guard.isPlainUser()
}

/** Both sides' buildings, floors and locations, and the latest send to VDMS. */
class GetVdmsPlacesUseCase @Inject constructor(private val repo: VdmsPlacesRepository) {
    suspend operator fun invoke(propertyId: String): VdmsPlaces = repo.places(propertyId)
}

/** Asks VDMS to send its list again; returns when it was asked (its list is newer than that once it has answered). */
class RefreshVdmsPlacesUseCase @Inject constructor(private val repo: VdmsPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String): Long? {
        guard.requireOnline(Permission.CONFIGURE)
        return repo.refresh(propertyId)
    }
}

/** Sends the [chosen] places of the property (and the parents they need) to VDMS, which adds them in the background. */
class SendPlacesToVdmsUseCase @Inject constructor(private val repo: VdmsPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, chosen: Set<PlaceKey>): PlacesPush {
        guard.requireOnline(Permission.CONFIGURE)
        if (chosen.isEmpty()) throw OnlineException(OnlineError.Invalid)
        return repo.push(propertyId, chosen)
    }
}

/** Imports the [chosen] VDMS places (and the parents they need) into the property, from VDMS's last list. */
class ImportPlacesFromVdmsUseCase @Inject constructor(private val repo: VdmsPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, chosen: Set<PlaceKey>): PlacesImportResult {
        guard.requireOnline(Permission.CONFIGURE)
        if (chosen.isEmpty()) throw OnlineException(OnlineError.Invalid)
        return repo.pull(propertyId, chosen)
    }
}

/** The places import use cases, for injecting as one. */
class VdmsPlacesUseCases @Inject constructor(
    val canImport: CanImportPlacesUseCase,
    val get: GetVdmsPlacesUseCase,
    val refresh: RefreshVdmsPlacesUseCase,
    val send: SendPlacesToVdmsUseCase,
    val importPlaces: ImportPlacesFromVdmsUseCase,
)
