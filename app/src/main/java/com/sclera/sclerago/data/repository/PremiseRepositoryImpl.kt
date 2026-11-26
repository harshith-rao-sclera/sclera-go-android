package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.adc.PositionForCalls
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.PremiseStatus
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
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
 * Works out whether the phone is inside the current property's geofence, for the server's geo restriction: only for
 * someone whose group is geo restricted ([com.sclera.sclerago.domain.model.CurrentAccess.geoRestricted]) and only for a
 * property with a fence (whether or not its enforce switch is on); nobody else's location is read. It looks again whenever
 * the current property, its fences or the access change, each time a property list comes fresh from the server, and on
 * [refresh]. Failing safe: no location permission or no fix is never "on premise". The fences come with the property list
 * (kept encrypted on the device, wiped on sign-out), so this works offline. The position found is handed to
 * [PositionForCalls] (memory only) so the calls inside the property carry it, as the server asks; it is never stored or
 * logged.
 */
@Singleton
class PremiseRepositoryImpl @Inject constructor(
    downloads: PropertyDownloadRepository,
    properties: PropertyRepository,
    private val location: DeviceLocationSource,
    private val isOnPremise: IsOnPremiseUseCase,
    access: CurrentAccessRepository,
    private val forCalls: PositionForCalls,
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
            combine(downloads.downloads, properties.properties, access.state, ticks) { d, list, state, tick ->
                val id = d.currentId
                val property = list.firstOrNull { it.id == id }
                val restricted = (state as? AccessState.Loaded)?.access?.geoRestricted == true
                Target(id, restricted, property?.fences.orEmpty(), tick)
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
        forCalls.set(null)
        _status.value = PremiseStatus.NOT_APPLICABLE
    }

    private data class Target(val id: String?, val restricted: Boolean, val fences: List<Fence>, val tick: Int)

    private suspend fun evaluate(target: Target) {
        val id = target.id
        // Not geo restricted, or a property without a fence (nowhere to be inside): nobody is held, nothing is read or sent.
        if (id == null || !target.restricted || target.fences.isEmpty()) {
            evaluatedFor = null
            forCalls.set(null)
            _status.value = PremiseStatus.NOT_APPLICABLE
            return
        }
        // A property just opened is held to view only until it is known; looking again keeps what was known meanwhile.
        if (evaluatedFor != id) {
            _status.value = PremiseStatus.UNKNOWN
            forCalls.set(null)
        }
        evaluatedFor = id
        if (!location.permissionGranted()) {
            forCalls.set(null)
            _status.value = PremiseStatus.LOCATION_PERMISSION_NEEDED
            return
        }
        val point = location.current()
        // The calls inside the property say where the phone is (or nothing, without a fix: the server then lets them look only).
        forCalls.set(point)
        _status.value = when {
            // No fix: where the phone is can't be told, so view only.
            point == null -> PremiseStatus.UNKNOWN
            isOnPremise(point, target.fences) -> PremiseStatus.ON_PREMISE
            else -> PremiseStatus.OFF_PREMISE
        }
    }
}
