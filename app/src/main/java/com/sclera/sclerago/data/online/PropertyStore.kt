package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.local.PropertyListCache
import com.sclera.sclerago.data.mapper.PropertyRecord
import com.sclera.sclerago.data.mapper.channelsOff
import com.sclera.sclerago.data.mapper.settingsBody
import com.sclera.sclerago.data.mapper.toBody
import com.sclera.sclerago.data.mapper.toRecord
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.GeofenceDto
import com.sclera.sclerago.data.remote.adc.PhotoRulesDto
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.SettingsDto
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's properties and their settings. They come from the offline database when it has a `properties`
 * table, otherwise from the API ([AdcApi]); the API's list is kept in `global_config.db`'s `properties` table
 * ([PropertyListCache], global_config.db's properties table: synced rows, access codes and contacts encrypted) and read from there, so it shows offline.
 * Writes are online only. Loaded when someone signs in; forgotten on sign-out (the table's rows are deleted).
 */
@Singleton
class PropertyStore @Inject constructor(
    private val api: AdcApi,
    private val session: OnlineSession,
    private val table: PropertyListCache,
    private val downloads: PropertyDownloadRepository,
    private val offline: PropertyOfflineSource,
    private val db: AdcDatabase,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UserDataCleaner {
    private val _properties = MutableStateFlow(emptyList<Property>())
    private val _disabled = MutableStateFlow(emptyMap<String, Set<OnboardingChannel>>())
    private var dtos = emptyList<PropertyDto>()
    private var offlineJob: Job? = null

    /**
     * Property id → the onboarding channels its server settings switch off, as last read from or saved to the server.
     * The offline properties table doesn't carry settings, so the switches use this whichever list is shown.
     */
    private var settingsOff = emptyMap<String, Set<OnboardingChannel>>()

    val properties: StateFlow<List<Property>> = _properties

    private val _listed = MutableSharedFlow<Set<String>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The ids in each property list fresh from the server (not the kept copy), latest first to a new collector. */
    val listed: SharedFlow<Set<String>> = _listed.asSharedFlow()

    /** Property id → ways of adding assets switched off there. */
    val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = _disabled

    private val _photoRules = MutableStateFlow(emptyMap<String, PhotoRulesDto>())

    /** Property id → its photo rules (its settings' `photoRules`), as last loaded from the server. */
    val photoRules: StateFlow<Map<String, PhotoRulesDto>> = _photoRules

    init {
        // is_synced follows the downloads: 1 once a property's offline data is on this device, 0 when it isn't.
        scope.launch {
            downloads.downloads.map { it.downloadedIds }.distinctUntilChanged().collect { ids ->
                withContext(io) { runCatching { table.markSynced(ids) } }
            }
        }
        scope.launch {
            session.user.collectLatest { user ->
                if (user == null) {
                    clear()
                } else {
                    readCache()
                    try {
                        refresh()
                    } catch (_: OnlineException) {
                        // the cached list stays
                    }
                }
            }
        }
    }

    /** Loads the properties again; returns once they are published. Throws [OnlineException]; the list shown before stays. */
    suspend fun refresh() {
        offlineJob?.cancel()
        if (offlineTableReady()) {
            val firstPublished = CompletableDeferred<Unit>()
            val job = scope.launch {
                offline.propertiesFlow().collect { list ->
                    publish(list.map { PropertyRecord(it, emptySet()) })
                    firstPublished.complete(Unit)
                }
            }
            // Also released when reading stops early (failed or replaced), so a caller never waits forever.
            job.invokeOnCompletion { firstPublished.complete(Unit) }
            offlineJob = job
            firstPublished.await()
            // The offline list has no settings: read the server's in the background for the Bluetooth / Wi-Fi switches.
            scope.launch { refreshSettings() }
            return
        }
        val user = session.user.value
        val loaded = api.properties(session.organisation())
        if (session.user.value != user) return // signed out (or in as someone else) meanwhile: drop it
        setDtos(loaded)
        _listed.tryEmit(loaded.mapTo(HashSet()) { it.id })
    }

    /** Creates a property on the server. Throws [OnlineException]. */
    suspend fun create(draft: Property): Property {
        val user = session.user.value
        val created = api.createProperty(session.organisation(), draft.toBody())
        if (session.user.value == user) setDtos((dtos + created).sortedBy { it.name.lowercase() })
        return created.toRecord().property
    }

    /** The property as the server has it now (Property Info's Edit reads it fresh, as the web does). Throws [OnlineException]. */
    suspend fun fetch(id: String): Property = api.property(id).toRecord().property

    /** Saves [property]'s details on the server and keeps what it saved. Throws [OnlineException]. */
    suspend fun update(property: Property): Property {
        val user = session.user.value
        val saved = api.updateProperty(property.id, property.toBody())
        if (session.user.value == user) setDtos(dtos.map { if (it.id == saved.id) saved else it })
        return saved.toRecord().property
    }

    /** Deletes [id] on the server and drops it from the list. Throws [OnlineException]. */
    suspend fun delete(id: String) {
        val user = session.user.value
        api.deleteProperty(id)
        if (session.user.value == user) setDtos(dtos.filterNot { it.id == id })
    }

    /** Switches one way of adding assets on or off for [propertyId]. Throws [OnlineException]. */
    suspend fun setOnboarding(propertyId: String, channel: OnboardingChannel, enabled: Boolean) {
        // Only the settings change: the property list on show (possibly the offline one) stays as it is.
        applySettings(propertyId, api.updateSettings(propertyId, settingsBody(channel, enabled)))
    }

    /**
     * Keeps [settings] as [propertyId]'s settings just read from or saved to the server (Configuration), so the asset
     * screens use its photo rules and onboarding switches at once. The kept property list is updated too when it holds the property.
     */
    suspend fun applySettings(propertyId: String, settings: SettingsDto) {
        _photoRules.value = _photoRules.value + (propertyId to settings.photoRules)
        val off = settings.channelsOff()
        settingsOff = settingsOff + (propertyId to off)
        _disabled.value = _disabled.value + (propertyId to off)
        if (dtos.none { it.id == propertyId }) return
        dtos = dtos.map { if (it.id == propertyId) it.copy(settings = settings) else it }
        keep(dtos)
    }

    /**
     * Keeps [geofence] as [propertyId]'s geofence just saved in Configuration, so the geofence check uses it at once
     * (and offline) without waiting for the next property list. Nothing changes when the kept list doesn't hold it.
     */
    suspend fun applyGeofence(propertyId: String, geofence: GeofenceDto) {
        if (dtos.none { it.id == propertyId }) return
        setDtos(dtos.map { if (it.id == propertyId) it.copy(geofenceEnforced = geofence.enforced, geofence = geofence.fences) else it })
    }

    /**
     * Reads the property list from the server only for its settings (onboarding switches and photo rules), while the
     * offline list stays on show. Nothing is said when it can't: the last known settings stay.
     */
    private suspend fun refreshSettings() {
        val user = session.user.value
        val loaded = try {
            api.properties(session.organisation())
        } catch (_: OnlineException) {
            return
        }
        if (session.user.value != user) return
        _listed.tryEmit(loaded.mapTo(HashSet()) { it.id })
        rememberSettings(loaded)
        _disabled.value = _disabled.value.mapValues { (id, off) -> settingsOff[id] ?: off }
        dtos = loaded
        keep(loaded)
    }

    private fun rememberSettings(list: List<PropertyDto>) {
        settingsOff = settingsOff + list.associate { it.id to it.settings.channelsOff() }
        _photoRules.value = list.associate { it.id to it.settings.photoRules }
    }

    override fun clear() {
        offlineJob?.cancel()
        _listed.resetReplayCache()
        scope.launch(io) { runCatching { table.clear() } }
        settingsOff = emptyMap()
        dtos = emptyList()
        publish(emptyList())
        _photoRules.value = emptyMap()
    }

    private suspend fun offlineTableReady(): Boolean =
        withContext(io) { db.isOpen && runCatching { db.hasTable(TABLE) }.getOrDefault(false) }

    private suspend fun readCache() {
        val kept = withContext(io) { runCatching { table.read() }.getOrNull() }?.takeIf { it.isNotEmpty() } ?: return
        dtos = kept
        rememberSettings(kept)
        publish(kept.map(PropertyDto::toRecord))
    }

    private suspend fun setDtos(list: List<PropertyDto>) {
        dtos = list
        rememberSettings(list)
        publish(list.map(PropertyDto::toRecord))
        keep(list)
    }

    /** Keeps [list] in `global_config.db`'s `properties` table (downloaded ones marked synced), for the next start and offline. */
    private suspend fun keep(list: List<PropertyDto>) {
        val organisation = runCatching { session.organisation() }.getOrNull()
        val synced = downloads.downloads.value.downloadedIds
        withContext(io) { runCatching { table.replace(list, organisation, synced) } }
    }

    /** Shows [records]; each one's onboarding switches come from its last known server settings when there are any. */
    private fun publish(records: List<PropertyRecord>) {
        _properties.value = records.map { it.property }
        _disabled.value = records.associate { it.property.id to (settingsOff[it.property.id] ?: it.disabled) }
    }

    private companion object {
        const val TABLE = "properties"
    }
}
