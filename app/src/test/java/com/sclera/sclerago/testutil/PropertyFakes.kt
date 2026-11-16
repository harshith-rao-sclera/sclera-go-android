package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.usecase.property.IsDeviceOnlineUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertyDownloadsUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.RefreshPropertyDownloadsUseCase
import com.sclera.sclerago.domain.usecase.property.StartPropertyDownloadUseCase
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
class FakePropertyRepository(initial: List<Property> = emptyList()) : PropertyRepository {
    private val flow = MutableStateFlow(initial)
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

/** The properties the screen tests were written against (ids p1 to p7; "p1" has the sample assets). */
val SAMPLE_PROPERTIES = listOf(
    Property("p1", "Test 861", true),
    Property("p2", "Dhanush 16/10 2.1.0", false),
    Property("p3", "Post production 12/10", false),
    Property("p4", "Sample Property 5", false),
    Property("p5", "Harbour Point Office Tower", false, "Office"),
    Property("p6", "Northgate Logistics Hub", false, "Warehouse"),
    Property("p7", "Riverside Medical Centre", false, "Hospital"),
)

/** A [PropertyDownloadRepository] the test drives: starts are recorded, the state is set directly. */
class FakePropertyDownloads(initial: PropertyDownloads = PropertyDownloads()) : PropertyDownloadRepository {
    val flow = MutableStateFlow(initial)
    override val downloads: StateFlow<PropertyDownloads> = flow
    val started = mutableListOf<String>()
    var refreshes = 0

    override fun start(propertyId: String) {
        started += propertyId
        flow.value = flow.value.copy(active = flow.value.active + (propertyId to PropertyDownload.Running(0f, 0.0, 0.0)))
    }

    override fun refresh() {
        refreshes++
    }
}

/** A [NetworkMonitor] whose answer the test sets. */
class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}

fun propertyDownloadUseCases(repo: PropertyDownloadRepository, network: NetworkMonitor) = PropertyDownloadUseCases(
    ObservePropertyDownloadsUseCase(repo), StartPropertyDownloadUseCase(repo, network),
    RefreshPropertyDownloadsUseCase(repo), IsDeviceOnlineUseCase(network),
)
