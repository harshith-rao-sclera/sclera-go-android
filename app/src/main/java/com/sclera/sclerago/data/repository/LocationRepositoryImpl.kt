package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.data.offline.PlacesWriter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.PlaceRow
import com.sclera.sclerago.domain.repository.LocationRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The building, floor and location tree of the downloaded property, from the offline database. The database has no
 * place for the details of a building (type, address, contact, access code), so they are kept in memory for this
 * session only and cleared on sign-out.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    source: PlacesOfflineSource,
    private val writer: PlacesWriter,
    @ApplicationScope scope: CoroutineScope,
) : LocationRepository, UserDataCleaner {
    private val infos = MutableStateFlow(emptyMap<String, BuildingInfo>())

    override val tree: StateFlow<List<LocNode>> = combine(source.treeFlow(), infos) { nodes, details ->
        nodes.map { b -> details[b.id]?.let { b.copy(info = it) } ?: b }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun addBuilding(name: String, info: BuildingInfo?): String {
        val id = writer.addBuilding(name)
        info?.let { details -> infos.update { it + (id to details) } }
        return id
    }

    override suspend fun addFloor(buildingId: String, name: String): String = writer.addFloor(buildingId, name)

    override suspend fun saveBuilding(id: String, name: String, info: BuildingInfo) {
        writer.rename(id, name)
        infos.update { it + (id to info) }
    }

    override suspend fun saveFloors(buildingId: String, floors: List<LocNode>) = writer.saveFloors(buildingId, floors)

    override suspend fun renameNode(id: String, name: String) = writer.rename(id, name)

    override suspend fun updateLocation(id: String, name: String, type: String, code: String) =
        writer.updateLocation(id, name, type, code)

    override suspend fun deleteNodes(ids: Set<String>) {
        writer.delete(ids)
        infos.update { it - ids }
    }

    override suspend fun addLocations(floorId: String, locations: List<NewLocation>): Int = writer.addLocations(floorId, locations)

    override suspend fun importRows(rows: List<PlaceRow>): Int = writer.import(rows)

    override fun clear() {
        infos.value = emptyMap()
    }
}
