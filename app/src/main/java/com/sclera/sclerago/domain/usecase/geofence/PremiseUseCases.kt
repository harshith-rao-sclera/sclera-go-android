package com.sclera.sclerago.domain.usecase.geofence

import com.sclera.sclerago.domain.model.PremiseNotice
import com.sclera.sclerago.domain.repository.PremiseRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Why a collector is view only because of the property's geofence (outside the fences, no location permission, or no
 * location), or [PremiseNotice.NONE]; admins and other users are never held to it. Kept up to date.
 */
class ObservePremiseNoticeUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Flow<PremiseNotice> = guard.observePremiseNotice()
}

/**
 * The geofence is checked again: the app came back to the foreground, or the location permission was just granted.
 * (A change of the current property is picked up on its own.)
 */
class RefreshPremiseUseCase @Inject constructor(private val repo: PremiseRepository) {
    operator fun invoke() = repo.refresh()
}
