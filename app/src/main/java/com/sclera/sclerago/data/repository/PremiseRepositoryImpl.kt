package com.sclera.sclerago.data.repository

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PremiseStatus
import com.sclera.sclerago.domain.repository.DeviceLocationSource
import com.sclera.sclerago.domain.repository.GeofenceRepository
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out whether the phone is on premise of the current property when that property has geofencing on
 * ([com.sclera.sclerago.domain.model.Property.gps]). It looks again whenever the current property or its geofencing
 * switch changes, and on [refresh]. Failing safe: with geofencing on, no location permission, no fix or no fences known
 * is never "on premise". The last fences read are kept in memory only (for working offline) and dropped on sign-out;
 * the phone's position is compared once and not kept, logged or sent.
 */
@Singleton
class PremiseRepositoryImpl @Inject constructor(
    downloads: PropertyDownloadRepository,
    properties: PropertyRepository,
    private val geofences: GeofenceRepository,
    private val location: DeviceLocationSource,
    private val isOnPremise: IsOnPremiseUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) : PremiseRepository, UserDataCleaner {
    private val _status = MutableStateFlow(PremiseStatus.NOT_APPLICABLE)
    override val status: StateFlow<PremiseStatus> = _status

    private val ticks = MutableStateFlow(0)

    /** Property id → the fences last read for it. */
    private val fences = ConcurrentHashMap<String, List<Fence>>()

    /** The property the current status was worked out for, so a new property starts from the safe answer. */
    @Volatile
    private var evaluatedFor: String? = null

    init {
        scope.launch {
            combine(downloads.downloads, properties.properties, ticks) { d, list, tick ->
                val id = d.currentId
                Target(id, list.firstOrNull { it.id == id }?.gps == true, tick)
            }.distinctUntilChanged().collectLatest { evaluate(it) }
        }
    }

    override fun refresh() {
        ticks.value++
    }

    override fun clear() {
        fences.clear()
        evaluatedFor = null
        _status.value = PremiseStatus.NOT_APPLICABLE
    }

    private data class Target(val id: String?, val gpsOn: Boolean, val tick: Int)

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
        val known = loadFences(id)
        val point = location.current()
        _status.value = when {
            point == null || known == null -> PremiseStatus.UNKNOWN
            isOnPremise(point, known) -> PremiseStatus.ON_PREMISE
            else -> PremiseStatus.OFF_PREMISE
        }
    }

    /** The property's fences from the server, else the last ones read, else null. */
    private suspend fun loadFences(id: String): List<Fence>? = try {
        geofences.load(id).fences.also { fences[id] = it }
    } catch (_: OnlineException) {
        fences[id]
    }
}
