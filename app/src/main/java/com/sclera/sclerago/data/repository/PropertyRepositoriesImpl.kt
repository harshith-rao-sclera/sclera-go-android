package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.DEFAULT_FIELDS
import com.sclera.sclerago.data.local.DEFAULT_PHOTO_REQS
import com.sclera.sclerago.data.local.PropertyStore
import com.sclera.sclerago.data.local.locationTree
import com.sclera.sclerago.data.mapper.toDomain
import com.sclera.sclerago.data.mapper.toLocalRecord
import com.sclera.sclerago.data.remote.PropertyRemoteSource
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The properties in the `properties` table: the organisation's from the ADC API (`is_synced = 1`) and any
 * added on this device (`is_synced = 0`). Loaded from the table at start, so the last list shows offline.
 * Every table read and write runs on [io], one at a time.
 */
@ActivityRetainedScoped
class PropertyRepositoryImpl @Inject constructor(
    private val store: PropertyStore,
    private val remote: PropertyRemoteSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PropertyRepository, UserDataCleaner {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()
    private val state = MutableStateFlow<List<Property>>(emptyList())
    override val properties: StateFlow<List<Property>> = state.asStateFlow()

    /** The organisation of the last refresh; properties added on the device are saved under it. */
    @Volatile private var organisationId: String? = null

    /** Bumped by [clear], so a refresh still running when the user signs out doesn't save anything. */
    @Volatile private var generation = 0

    init {
        scope.launch { lock.withLock { reloadLocked() } }
    }

    override fun add(property: Property) {
        state.value = state.value + property // on screen straight away
        val record = property.toLocalRecord(organisationId)
        scope.launch {
            lock.withLock {
                store.upsert(listOf(record))
                reloadLocked()
            }
        }
    }

    override suspend fun refresh(organisationId: String) {
        val started = generation
        this.organisationId = organisationId
        val records = remote.fetchAll(organisationId)
        withContext(io) {
            lock.withLock {
                if (generation != started) return@withLock // signed out meanwhile
                store.replaceSynced(records)
                reloadLocked()
            }
        }
    }

    override fun clear() {
        generation++
        organisationId = null
        state.value = emptyList()
        scope.launch { lock.withLock { store.clear() } }
    }

    private fun reloadLocked() {
        state.value = store.all().map { it.toDomain() }
    }
}

@ActivityRetainedScoped
class LocationRepositoryImpl @Inject constructor() : LocationRepository, UserDataCleaner {
    private val state = MutableStateFlow(emptyMap<String, List<LocNode>>())
    override val trees: StateFlow<Map<String, List<LocNode>>> = state.asStateFlow()
    private var nodeSeq = 1

    override fun tree(prop: String): List<LocNode> = state.value[prop] ?: locationTree(prop)

    override fun edit(prop: String, f: (List<LocNode>) -> List<LocNode>) {
        state.value = state.value + (prop to f(tree(prop)))
    }

    override fun newNodeId(prefix: String): String = "$prefix-n${nodeSeq++}"

    override fun clear() {
        state.value = emptyMap()
        nodeSeq = 1
    }
}

@ActivityRetainedScoped
class AssetConfigRepositoryImpl @Inject constructor() : AssetConfigRepository, UserDataCleaner {
    private val state = MutableStateFlow(AssetConfig(DEFAULT_FIELDS, DEFAULT_PHOTO_REQS))
    override val config: StateFlow<AssetConfig> = state.asStateFlow()

    override fun update(f: (AssetConfig) -> AssetConfig) {
        state.value = f(state.value)
    }

    override fun clear() {
        state.value = AssetConfig(DEFAULT_FIELDS, DEFAULT_PHOTO_REQS)
    }
}

@ActivityRetainedScoped
class OnboardingRepositoryImpl @Inject constructor() : OnboardingRepository, UserDataCleaner {
    private val state = MutableStateFlow(emptyMap<String, Set<OnboardingChannel>>())
    override val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = state.asStateFlow()

    override fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean) {
        val cur = state.value[prop].orEmpty()
        state.value = state.value + (prop to if (enabled) cur - channel else cur + channel)
    }

    override fun clear() {
        state.value = emptyMap()
    }
}
