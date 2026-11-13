package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.mapper.PropertyRecord
import com.sclera.sclerago.data.mapper.settingsBody
import com.sclera.sclerago.data.mapper.toBody
import com.sclera.sclerago.data.mapper.toRecord
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's properties and their settings. They come from the offline database when it has a `properties`
 * table, otherwise from the API ([AdcApi]); the last API list is kept encrypted ([EncryptedCache], it holds
 * contact details and access codes) so it still shows offline. Writes are online only.
 * Loaded when someone signs in; forgotten on sign-out (the cache file is removed by the cache's own cleaner).
 */
@Singleton
class PropertyStore @Inject constructor(
    private val api: AdcApi,
    private val session: OnlineSession,
    private val cache: EncryptedCache,
    private val offline: PropertyOfflineSource,
    private val db: AdcDatabase,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : UserDataCleaner {
    private val _properties = MutableStateFlow(emptyList<Property>())
    private val _disabled = MutableStateFlow(emptyMap<String, Set<OnboardingChannel>>())
    private var dtos = emptyList<PropertyDto>()
    private var offlineJob: Job? = null

    val properties: StateFlow<List<Property>> = _properties

    /** Property id → ways of adding assets switched off there. */
    val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = _disabled

    init {
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

    /** Loads the properties again. Throws [OnlineException]; the list shown before stays. */
    suspend fun refresh() {
        offlineJob?.cancel()
        if (offlineTableReady()) {
            offlineJob = scope.launch { offline.propertiesFlow().collect { list -> publish(list.map { PropertyRecord(it, emptySet()) }) } }
            return
        }
        val user = session.user.value
        val loaded = api.properties(session.organisation())
        if (session.user.value != user) return // signed out (or in as someone else) meanwhile: drop it
        setDtos(loaded)
    }

    /** Creates a property on the server. Throws [OnlineException]. */
    suspend fun create(draft: Property): Property {
        val user = session.user.value
        val created = api.createProperty(session.organisation(), draft.toBody())
        if (session.user.value == user) setDtos((dtos + created).sortedBy { it.name.lowercase() })
        return created.toRecord().property
    }

    /** Switches one way of adding assets on or off for [propertyId]. Throws [OnlineException]. */
    suspend fun setOnboarding(propertyId: String, channel: OnboardingChannel, enabled: Boolean) {
        val settings = api.updateSettings(propertyId, settingsBody(channel, enabled))
        setDtos(dtos.map { if (it.id == propertyId) it.copy(settings = settings) else it })
    }

    override fun clear() {
        offlineJob?.cancel()
        dtos = emptyList()
        publish(emptyList())
    }

    private suspend fun offlineTableReady(): Boolean =
        withContext(io) { db.isOpen && runCatching { db.hasTable(TABLE) }.getOrDefault(false) }

    private suspend fun readCache() {
        val text = withContext(io) { cache.read(CACHE) } ?: return
        runCatching { AdcJson.decodeFromString(LIST, text) }.getOrNull()?.let {
            dtos = it
            publish(it.map(PropertyDto::toRecord))
        }
    }

    private suspend fun setDtos(list: List<PropertyDto>) {
        dtos = list
        publish(list.map(PropertyDto::toRecord))
        withContext(io) { runCatching { cache.write(CACHE, AdcJson.encodeToString(LIST, list)) } }
    }

    private fun publish(records: List<PropertyRecord>) {
        _properties.value = records.map { it.property }
        _disabled.value = records.associate { it.property.id to it.disabled }
    }

    private companion object {
        const val TABLE = "properties"
        const val CACHE = "properties"
        val LIST = ListSerializer(PropertyDto.serializer())
    }
}
