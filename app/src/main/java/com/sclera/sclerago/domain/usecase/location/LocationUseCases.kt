package com.sclera.sclerago.domain.usecase.location

import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.PlaceRow
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Configuration → Building info: the building → floor → location tree of the downloaded property.

class ObserveLocationTreeUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(): StateFlow<List<LocNode>> = repo.tree
}

private fun BuildingInfo.trimmed() = copy(
    address1 = address1.trim(), address2 = address2.trim(), state = state.trim(), city = city.trim(),
    contactName = contactName.trim(), email = email.trim(), phone = phone.trim(), notes = notes.trim(), accessCode = accessCode.trim(),
)

/** Adds a building, with its details when given; returns its id. */
class AddBuildingUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(name: String, info: BuildingInfo? = null): String = repo.addBuilding(name.trim(), info?.trimmed())
}

/** Adds a floor to a building; returns its id. */
class AddFloorUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(buildingId: String, name: String): String = repo.addFloor(buildingId, name.trim())
}

/** Edit building sheet. */
class SaveBuildingUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(id: String, name: String, info: BuildingInfo) = repo.saveBuilding(id, name.trim(), info.trimmed())
}

/** Edit floors sheet: the building's floors in their new order, with names, types and codes; missing ones are removed. */
class SaveFloorsUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(buildingId: String, floors: List<LocNode>) =
        repo.saveFloors(buildingId, floors.map { it.copy(name = it.name.trim(), type = it.type.trim(), code = it.code.trim()) })
}

class RenameLocationNodeUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(id: String, name: String) = repo.renameNode(id, name.trim())
}

/** Deletes buildings, floors or locations; false when there was nothing to delete. */
class DeleteLocationNodesUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        repo.deleteNodes(ids)
        return true
    }
}

/** Adds one location, or several at once (one name per line; codes get -1, -2, … when given). Returns how many. */
class AddLocationsUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(floorId: String, names: List<String>, type: String, code: String): Int {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return 0
        val made = clean.mapIndexed { i, n ->
            val c = code.trim().let { if (it.isNotEmpty() && clean.size > 1) "$it-${i + 1}" else it }
            NewLocation(n, type.trim(), c)
        }
        return repo.addLocations(floorId, made)
    }
}

class UpdateLocationUseCase @Inject constructor(private val repo: LocationRepository) {
    suspend operator fun invoke(id: String, name: String, type: String, code: String) =
        repo.updateLocation(id, name.trim(), type.trim(), code.trim())
}

/**
 * Import buildings: rows of Building, Floor, Location, Type, Code (a header row is skipped) from a
 * CSV or Excel file. Buildings and floors are matched by name; locations already there are left alone.
 */
class ImportBuildingsUseCase @Inject constructor(
    private val repo: LocationRepository,
    private val files: FileRepository,
) {
    sealed interface Result {
        data object Unreadable : Result
        data object NothingFound : Result
        /** [rows] read from the file; [added] buildings, floors and locations that were new. */
        data class Imported(val rows: Int, val added: Int) : Result
    }

    suspend operator fun invoke(uri: String): Result {
        val rows = files.readSheet(uri) ?: return Result.Unreadable
        val data = rows.map { r -> List(5) { i -> r.getOrNull(i)?.trim().orEmpty() } }
            .filter { it[0].isNotEmpty() }
            .let { l -> if (l.firstOrNull()?.get(0)?.equals("building", ignoreCase = true) == true) l.drop(1) else l }
        if (data.isEmpty()) return Result.NothingFound
        val added = repo.importRows(data.map { (building, floor, location, type, code) -> PlaceRow(building, floor, location, type, code) })
        return Result.Imported(data.size, added)
    }
}

/** The location use cases, for injecting as one. */
class LocationUseCases @Inject constructor(
    val observeTree: ObserveLocationTreeUseCase,
    val addBuilding: AddBuildingUseCase,
    val addFloor: AddFloorUseCase,
    val saveBuilding: SaveBuildingUseCase,
    val saveFloors: SaveFloorsUseCase,
    val rename: RenameLocationNodeUseCase,
    val delete: DeleteLocationNodesUseCase,
    val addLocations: AddLocationsUseCase,
    val updateLocation: UpdateLocationUseCase,
    val importBuildings: ImportBuildingsUseCase,
)
