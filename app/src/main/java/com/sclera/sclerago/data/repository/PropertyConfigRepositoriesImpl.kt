package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.data.sync.SyncManagerBridge
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.data.SheetUploads
import com.sclera.sclerago.data.mapper.aiValidationBody
import com.sclera.sclerago.data.mapper.unitSystemBody
import com.sclera.sclerago.data.mapper.fieldPatchJson
import com.sclera.sclerago.data.mapper.fieldsBulkJson
import com.sclera.sclerago.data.mapper.placeCreateJson
import com.sclera.sclerago.data.mapper.placePatchJson
import com.sclera.sclerago.data.mapper.placesTree
import com.sclera.sclerago.data.mapper.toFieldCfg
import com.sclera.sclerago.data.mapper.toFieldsRead
import com.sclera.sclerago.data.mapper.toPlacesImported
import com.sclera.sclerago.data.mapper.geofenceBody
import com.sclera.sclerago.data.mapper.photoRulesBody
import com.sclera.sclerago.data.mapper.toGeofence
import com.sclera.sclerago.data.mapper.toSettings
import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcConfigApi
import com.sclera.sclerago.data.remote.adc.CfgFieldDto
import com.sclera.sclerago.data.remote.adc.CfgOptionDto
import com.sclera.sclerago.data.remote.adc.SettingsDto
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldsRead
import com.sclera.sclerago.domain.model.Geofence
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.PlaceChange
import com.sclera.sclerago.domain.model.PlacesImported
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PropertySettings
import com.sclera.sclerago.domain.repository.ConfigFieldsRepository
import com.sclera.sclerago.domain.repository.ConfigPlacesRepository
import com.sclera.sclerago.domain.repository.GeofenceRepository
import com.sclera.sclerago.domain.repository.PropertySettingsRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A property's settings, read and changed at the server (`/properties/{id}/settings`). What the server returns is also
 * handed to the [PropertyStore], so the asset screens use the new photo rules at once.
 */
@Singleton
class PropertySettingsRepositoryImpl @Inject constructor(
    private val api: AdcApi,
    private val store: PropertyStore,
) : PropertySettingsRepository {
    private val _aiSwitches = MutableStateFlow(emptyMap<String, Boolean>())
    override val aiSwitches: StateFlow<Map<String, Boolean>> = _aiSwitches

    /** Property id → when its AI switch was last read or saved (monotonic), for [refreshAiSwitch]. */
    private val readAt = mutableMapOf<String, TimeMark>()

    /** One read at a time, so screens opening together ask the server once. */
    private val reading = Mutex()

    override suspend fun load(propertyId: String): PropertySettings = kept(propertyId, api.settings(propertyId))

    override suspend fun refreshAiSwitch(propertyId: String, maxAgeMs: Long) {
        reading.withLock {
            val at = synchronized(readAt) { readAt[propertyId] }
            if (at == null || propertyId !in _aiSwitches.value || at.elapsedNow() >= maxAgeMs.milliseconds) load(propertyId)
        }
    }

    override suspend fun savePhotoRules(propertyId: String, rules: List<PhotoRule>): PropertySettings =
        kept(propertyId, api.updateSettings(propertyId, photoRulesBody(rules)))

    override suspend fun setAiValidation(propertyId: String, on: Boolean): PropertySettings =
        kept(propertyId, api.updateSettings(propertyId, aiValidationBody(on)))

    override suspend fun setMetricUnits(propertyId: String, metric: Boolean): PropertySettings =
        kept(propertyId, api.updateSettings(propertyId, unitSystemBody(metric)))

    private suspend fun kept(propertyId: String, dto: SettingsDto): PropertySettings {
        store.applySettings(propertyId, dto)
        // The asset screens' AI buttons follow at once (the web's setAiOn).
        synchronized(readAt) { readAt[propertyId] = TimeSource.Monotonic.markNow() }
        _aiSwitches.update { it + (propertyId to dto.aiValidation) }
        return dto.toSettings()
    }
}

/** A property's geofence at the server (`/properties/{id}/geofence`); nothing is kept on the device. */
@Singleton
class GeofenceRepositoryImpl @Inject constructor(private val api: AdcApi) : GeofenceRepository {
    override suspend fun load(propertyId: String): Geofence = api.geofence(propertyId).toGeofence()

    override suspend fun save(propertyId: String, geofence: Geofence): Geofence =
        api.putGeofence(propertyId, geofenceBody(geofence)).toGeofence()
}

/**
 * The property's standard fields at the server. The last list read is kept so a change sends only what differs and
 * dropdown options keep their colours. Import from Excel sends the picked sheet to the server, which reads it.
 */
@Singleton
class ConfigFieldsRepositoryImpl @Inject constructor(
    private val api: AdcConfigApi,
    private val sheets: SheetUploads,
) : ConfigFieldsRepository, UserDataCleaner {
    private val options = java.util.concurrent.ConcurrentHashMap<String, List<CfgOptionDto>>()

    override fun clear() = options.clear()

    private fun keep(dto: CfgFieldDto): CfgFieldDto = dto.also { options[it.id] = it.options.orEmpty() }

    override suspend fun load(propertyId: String): List<FieldCfg> =
        api.fields(propertyId).sortedBy { it.position }.map { keep(it) }.mapNotNull { it.toFieldCfg() }

    override suspend fun create(propertyId: String, fields: List<FieldCfg>): List<FieldCfg> =
        api.createFields(propertyId, fieldsBulkJson(fields)).map { keep(it) }.mapNotNull { it.toFieldCfg() }

    override suspend fun update(propertyId: String, before: FieldCfg, after: FieldCfg): FieldCfg {
        val body = fieldPatchJson(before, after, options[after.id])
        if (body.isEmpty()) return after
        return keep(api.updateField(propertyId, after.id, body)).toFieldCfg() ?: after
    }

    override suspend fun delete(propertyId: String, id: String) {
        api.deleteField(propertyId, id)
        options.remove(id)
    }

    override suspend fun readSheet(propertyId: String, file: String): FieldsRead {
        val sheet = sheets.read(file)
        return api.readFieldImport(propertyId, sheet.name, sheet.bytes).toFieldsRead()
    }

    override suspend fun importSample(propertyId: String): ByteArray = api.fieldImportTemplate(propertyId)
}

/**
 * The property's buildings, floors and locations at the server; offline, the copy downloaded with the property (to look
 * at: adding and changing places need the server). A building's access code is its code there (as the web saves it);
 * its other details (type, address, contact) have no place there, so they are kept in memory on this phone for the
 * session and wiped on sign-out. Import buildings sends the picked sheet to the server, which reads it.
 */
@Singleton
class ConfigPlacesRepositoryImpl @Inject constructor(
    private val api: AdcConfigApi,
    private val sheets: SheetUploads,
    private val offline: PlacesOfflineSource,
    private val bridge: SyncManagerBridge,
) : ConfigPlacesRepository, UserDataCleaner {
    private val infos = java.util.concurrent.ConcurrentHashMap<String, BuildingInfo>()

    override suspend fun load(propertyId: String): List<LocNode> {
        val tree = try {
            placesTree(api.buildings(propertyId), api.floors(propertyId), api.locations(propertyId))
        } catch (e: OnlineException) {
            // No connection: the downloaded copy, when this property's is the one open (else the tab says it's offline).
            if (e.error != OnlineError.Offline || !bridge.isSelected(propertyId)) throw e
            offline.everyPlace().ifEmpty { throw e }
        }
        return tree.map { b -> b.copy(info = (infos[b.id] ?: BuildingInfo()).copy(accessCode = b.code)) }
    }

    override suspend fun addBuilding(propertyId: String, name: String, info: BuildingInfo?): String {
        val id = api.createBuilding(propertyId, placeCreateJson(name, code = info?.accessCode.orEmpty())).id
        info?.let { infos[id] = it }
        return id
    }

    override suspend fun addFloor(propertyId: String, buildingId: String, name: String, type: String, code: String): String =
        api.createFloor(propertyId, buildingId, placeCreateJson(name, code, type)).id

    override suspend fun addLocation(propertyId: String, floorId: String, location: NewLocation): String =
        api.createLocation(propertyId, floorId, placeCreateJson(location.name, location.code, location.type)).id

    override suspend fun update(propertyId: String, kind: NodeKind, id: String, change: PlaceChange) {
        val body = placePatchJson(kind, change)
        if (body.isNotEmpty()) api.updatePlace(propertyId, kind.collection(), id, body)
    }

    override fun saveBuildingInfo(buildingId: String, info: BuildingInfo) {
        infos[buildingId] = info
    }

    override suspend fun delete(propertyId: String, kind: NodeKind, id: String) {
        api.deletePlace(propertyId, kind.collection(), id)
        infos.remove(id)
    }

    override suspend fun importSheet(propertyId: String, file: String): PlacesImported {
        val sheet = sheets.read(file)
        return api.importPlaces(propertyId, sheet.name, sheet.bytes).toPlacesImported()
    }

    override suspend fun importSample(propertyId: String): ByteArray = api.placeImportTemplate(propertyId)

    override fun clear() = infos.clear()

    private fun NodeKind.collection() = when (this) {
        NodeKind.BUILDING -> "buildings"
        NodeKind.FLOOR -> "floors"
        NodeKind.LOCATION -> "locations"
    }
}
