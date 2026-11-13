package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.PROPERTIES
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// In-memory property data, starting from the samples, until the properties come from the Sclera API.

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
