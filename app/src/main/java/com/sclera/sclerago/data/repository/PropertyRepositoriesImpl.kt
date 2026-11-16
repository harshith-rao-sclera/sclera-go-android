package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.OnboardingRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The properties, read and created through the [PropertyStore]. */
@Singleton
class PropertyRepositoryImpl @Inject constructor(private val store: PropertyStore) : PropertyRepository {
    override val properties: StateFlow<List<Property>> = store.properties

    override suspend fun refresh() = store.refresh()

    override suspend fun add(property: Property): Property = store.create(property)
}

/** Property Settings, read and changed through the [PropertyStore] (the server holds them per property). */
@Singleton
class OnboardingRepositoryImpl @Inject constructor(private val store: PropertyStore) : OnboardingRepository {
    override val disabled: StateFlow<Map<String, Set<OnboardingChannel>>> = store.disabled

    override suspend fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean) =
        store.setOnboarding(prop, channel, enabled)
}
