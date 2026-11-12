package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.DEFAULT_FIELDS
import com.sclera.sclerago.data.local.DEFAULT_PHOTO_REQS
import com.sclera.sclerago.data.local.PROPERTIES
import com.sclera.sclerago.data.local.locationTree
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// In-memory property data, starting from the samples, until it comes from the Sclera API.

@ActivityRetainedScoped
class PropertyRepositoryImpl @Inject constructor() : PropertyRepository, UserDataCleaner {
    private val state = MutableStateFlow(PROPERTIES)
    override val properties: StateFlow<List<Property>> = state.asStateFlow()

    override fun add(property: Property) {
        state.value = state.value + property
    }

    override fun clear() {
        state.value = PROPERTIES
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
