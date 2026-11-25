package com.sclera.sclerago.data.repository

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.PremiseStatus
import com.sclera.sclerago.domain.repository.DeviceLocationSource
import com.sclera.sclerago.domain.repository.PremiseRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.usecase.geofence.IsOnPremiseUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out whether the phone is on premise of the current property when that property has geofencing on
 * ([com.sclera.sclerago.domain.model.Property.gps]). It looks again whenever the current property or its geofencing
 * switch or fences change, each time a property list comes fresh from the server, and on [refresh]. Failing safe: with geofencing on, no location permission, no fix or no
 * fences is never "on premise". The fences come with the property list (the server sends them with each property), which
 * keeps them on the device (encrypted, wiped on sign-out), so no other call is made and this works offline. The phone's
 * position is compared once and not kept, logged or sent.
 */
@Singleton
class PremiseRepositoryImpl @Inject constructor(
    downloads: PropertyDownloadRepository,
    properties: PropertyRepository,
    private val location: DeviceLocationSource,
    private val isOnPremise: IsOnPremiseUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : PremiseRepository, UserDataCleaner {
    private val _status = MutableStateFlow(PremiseStatus.NOT_APPLICABLE)
    override val status: StateFlow<PremiseStatus> = _status

    private val ticks = MutableStateFlow(0)

    /** The property the current status was worked out for, so a new property starts from the safe answer. */
    @Volatile
    private var evaluatedFor: String? = null

    init {
        scope.launch {
            combine(downloads.downloads, properties.properties, ticks) { d, list, tick ->
                val id = d.currentId
                val property = list.firstOrNull { it.id == id }
                Target(id, property?.gps == true, property?.fences.orEmpty(), tick)
            }.distinctUntilChanged().collectLatest { evaluate(it) }
        }
        // Each property list fresh from the server (sign-in, the Property list, the re-check every 30 seconds) looks
        // again where the phone is, even when the fences didn't change: the phone may have moved on or off the property.
        scope.launch { properties.listed.collect { refresh() } }
    }

    override fun refresh() {
        ticks.value++
    }

    override fun clear() {
        evaluatedFor = null
        _status.value = PremiseStatus.NOT_APPLICABLE
    }

    private data class Target(val id: String?, val gpsOn: Boolean, val fences: List<Fence>, val tick: Int)

    private suspend fun evaluate(target: Target) {
        val id = target.id
        if (id == null || !target.gpsOn) {
            evaluatedFor = null
            _status.value = PremiseStatus.NOT_APPLICABLE
            return
        }
        // A property just opened is held to view only until it is known; looking again keeps what was known meanwhile.
        if (evaluatedFor != id) _status.value = PremiseStatus.UNKNOWN
        evaluatedFor = id
        if (!location.permissionGranted()) {
            _status.value = PremiseStatus.LOCATION_PERMISSION_NEEDED
            return
        }
        val point = location.current()
        _status.value = when {
            // No fix, or geofencing on with no fence drawn: where the phone is can't be told, so view only.
            point == null || target.fences.isEmpty() -> PremiseStatus.UNKNOWN
            isOnPremise(point, target.fences) -> PremiseStatus.ON_PREMISE
            else -> PremiseStatus.OFF_PREMISE
        }
    }
}
