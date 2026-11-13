package com.sclera.sclerago.testutil

import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.data.local.PropertyStore
import com.sclera.sclerago.data.remote.PropertyRemoteSource
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PropertyStore] in memory, with the same contract as the SQLite one (sorted by name, upsert by id). */
class FakePropertyStore(initial: List<PropertyRecord> = emptyList()) : PropertyStore {
    val rows = LinkedHashMap<String, PropertyRecord>().apply { initial.forEach { put(it.id, it) } }

    override fun all(): List<PropertyRecord> = rows.values.sortedBy { it.name.lowercase() }

    override fun upsert(records: List<PropertyRecord>) = records.forEach { rows[it.id] = it }

    override fun replaceSynced(records: List<PropertyRecord>) {
        rows.values.removeAll { it.isSynced }
        records.forEach { rows[it.id] = it.copy(isSynced = true) }
    }

    override fun clear() = rows.clear()
}

/** A [PropertyRemoteSource] that returns [records], or throws [failure] when it is set. */
class FakePropertyRemote(var records: List<PropertyRecord> = emptyList()) : PropertyRemoteSource {
    var failure: AuthError? = null
    val fetchedFor = mutableListOf<String>()

    override suspend fun fetchAll(organisationId: String): List<PropertyRecord> {
        fetchedFor += organisationId
        failure?.let { throw it }
        return records
    }
}

/** A [PropertyRepository] that records refreshes; [failure] makes them throw. */
class FakePropertyRepository : PropertyRepository {
    private val flow = MutableStateFlow(emptyList<Property>())
    override val properties: StateFlow<List<Property>> = flow
    val refreshed = mutableListOf<String>()
    var failure: AuthError? = null

    override fun add(property: Property) {
        flow.value = flow.value + property
    }

    override suspend fun refresh(organisationId: String) {
        refreshed += organisationId
        failure?.let { throw it }
    }
}

fun record(id: String, name: String, synced: Boolean = true, org: String? = "org-1") =
    PropertyRecord(id = id, organisationId = org, name = name, isSynced = synced)
