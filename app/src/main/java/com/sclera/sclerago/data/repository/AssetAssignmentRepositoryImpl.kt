package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.repository.NetworkMonitor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.sclera.sclerago.data.local.PropertyAssigneeStore
import com.sclera.sclerago.data.mapper.assignBody
import com.sclera.sclerago.data.mapper.toAssignResult
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PropertyAssignee
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.system.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assigns assets through `POST /properties/{p}/assets/bulk/assign`, as the web's new API does, and keeps each property's
 * assignees (`GET /properties/{p}/assets/assignees`, which anyone who can open the property may read) in
 * [PropertyAssigneeStore], so their names and emails show offline for every user. Wiped on sign-out.
 */
@Singleton
class AssetAssignmentRepositoryImpl @Inject constructor(
    private val api: AdcApi,
    private val store: PropertyAssigneeStore,
    private val time: TimeProvider,
    downloads: PropertyDownloadRepository,
    network: NetworkMonitor,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AssetAssignmentRepository, UserDataCleaner {
    private val kept = ConcurrentHashMap<String, Map<String, PropertyAssignee>>()

    init {
        // Each property downloaded (when the app starts, and each one downloaded since, while the phone is online):
        // its kept assignees are read, then fetched again from the server.
        scope.launch {
            var seen = emptySet<String>()
            downloads.downloads.map { it.downloadedIds }.distinctUntilChanged().collect { ids ->
                (ids - seen).forEach { id -> launch { openAssignees(id) } }
                seen = ids
            }
        }
        // Back online (the app may have started offline): every downloaded property's assignees are fetched again, so
        // someone given assets on the web meanwhile is known here too.
        scope.launch {
            network.observeOnline().drop(1).filter { it }.collect {
                downloads.downloads.value.downloadedIds.forEach { id -> launch { openAssignees(id) } }
            }
        }
    }

    override suspend fun assign(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult {
        val result = api.assignAssets(propertyId, assignBody(assetIds, email)).toAssignResult()
        // Someone may have assets for the first time now: the property's assignees are fetched again.
        try {
            fetch(propertyId)
        } catch (_: OnlineException) {
            // The assigning went through; the list catches up at the next refresh.
        }
        return result
    }

    override suspend fun assigneeNames(propertyId: String): Map<String, String> = fetch(propertyId).mapValues { it.value.label }

    override fun keptAssignees(): Map<String, PropertyAssignee> = kept.values.fold(emptyMap()) { all, one -> all + one }

    override fun keptAssigneesOf(propertyId: String): Map<String, PropertyAssignee> = kept[propertyId].orEmpty()

    override suspend fun openAssignees(propertyId: String) {
        if (kept[propertyId] == null) {
            kept[propertyId] = withContext(io) { runCatching { store.of(propertyId) }.getOrDefault(emptyList()) }.associateBy { it.userId }
        }
        try {
            fetch(propertyId)
        } catch (_: OnlineException) {
            // Offline (or refused): what is kept stays.
        }
    }

    override fun clear() {
        kept.clear()
        runCatching { store.clear() }
    }

    /** Fetches [propertyId]'s assignees and keeps them, in memory and on the device. */
    private suspend fun fetch(propertyId: String): Map<String, PropertyAssignee> {
        val fresh = api.assetAssignees(propertyId).map { PropertyAssignee(it.userId, it.name?.trim()?.takeIf(String::isNotEmpty), it.email) }
        withContext(io) { runCatching { store.replace(propertyId, fresh, time.nowMillis()) } }
        return fresh.associateBy { it.userId }.also { kept[propertyId] = it }
    }
}
