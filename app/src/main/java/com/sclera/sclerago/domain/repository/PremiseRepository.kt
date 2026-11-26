package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.GeoPoint
import com.sclera.sclerago.domain.model.PremiseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the phone is inside the current property's geofence, for someone whose group is geo restricted. It is worked
 * out again whenever the current property changes and on [refresh] (the app came back to the foreground, or the location
 * permission was granted).
 */
interface PremiseRepository {
    val status: StateFlow<PremiseStatus>

    /** Works the status out again now (reads the location, and the fences when none are known). */
    fun refresh()
}

/** No geofencing: nobody is held back. The starting point where no premise check is wired. */
object NoPremiseRestriction : PremiseRepository {
    override val status: StateFlow<PremiseStatus> = MutableStateFlow(PremiseStatus.NOT_APPLICABLE)
    override fun refresh() = Unit
}

/**
 * Where the phone is. Read only for someone whose group is geo restricted: compared with the fences, and sent to the
 * server with the calls inside that property (its geo restriction); never stored or logged.
 */
interface DeviceLocationSource {
    /** The location permission is already granted (this never asks for it). */
    fun permissionGranted(): Boolean

    /** The phone's position now, or null when the permission is missing or no fix could be had. */
    suspend fun current(): GeoPoint?
}
