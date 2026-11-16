package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import com.sclera.sclerago.domain.usecase.property.IsDeviceOnlineUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertyDownloadsUseCase
import com.sclera.sclerago.domain.usecase.property.OpenDownloadedPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.ResumePropertyDownloadUseCase
import com.sclera.sclerago.domain.usecase.property.StartPropertyDownloadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A [PropertyRepository] in memory: [refresh] counts and throws [failure] when set; [add] gives a new property the next id. */
class FakePropertyRepository(initial: List<Property> = emptyList()) : PropertyRepository {
    private val flow = MutableStateFlow(initial)
    override val properties: StateFlow<List<Property>> = flow
    var refreshes = 0
        private set
    var failure: Throwable? = null
    private var next = 0

    override suspend fun refresh() {
        refreshes++
        failure?.let { throw it }
    }

    override suspend fun add(property: Property): Property {
        failure?.let { throw it }
        val saved = if (property.id.isEmpty()) property.copy(id = "new-${++next}") else property
        flow.value = (flow.value + saved).sortedBy { it.name.lowercase() }
        return saved
    }
}

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

/** A [PropertyDownloadRepository] the test drives: starts, opens and resumes are recorded, the state is set directly. */
class FakePropertyDownloads(initial: PropertyDownloads = PropertyDownloads(restored = true)) : PropertyDownloadRepository {
    val flow = MutableStateFlow(initial)
    override val downloads: StateFlow<PropertyDownloads> = flow
    val started = mutableListOf<String>()
    val opened = mutableListOf<String>()
    var resumes = 0
        private set

    override fun start(propertyId: String) {
        started += propertyId
        flow.value = flow.value.copy(active = flow.value.active + (propertyId to PropertyDownload.Running(0f, 0.0, 0.0)))
    }

    override fun open(propertyId: String) {
        if (propertyId !in flow.value.downloadedIds) return
        opened += propertyId
        flow.value = flow.value.copy(currentId = propertyId)
    }

    override fun resume() {
        resumes++
    }
}

/** A [NetworkMonitor] whose answer the test sets. */
class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}

fun propertyDownloadUseCases(repo: PropertyDownloadRepository, network: NetworkMonitor) = PropertyDownloadUseCases(
    ObservePropertyDownloadsUseCase(repo), StartPropertyDownloadUseCase(repo, network),
    OpenDownloadedPropertyUseCase(repo), ResumePropertyDownloadUseCase(repo), IsDeviceOnlineUseCase(network),
)
