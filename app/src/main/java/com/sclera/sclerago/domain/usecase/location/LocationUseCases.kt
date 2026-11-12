package com.sclera.sclerago.domain.usecase.location

import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.mapNode
import com.sclera.sclerago.domain.model.removeNodes
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Configuration → Building info: each property's building → floor → location tree.

class ObserveLocationTreesUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(): StateFlow<Map<String, List<LocNode>>> = repo.trees
}

class GetLocationTreeUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String): List<LocNode> = repo.tree(prop)
}

private fun BuildingInfo.trimmed() = copy(
    address1 = address1.trim(), address2 = address2.trim(), state = state.trim(), city = city.trim(),
    contactName = contactName.trim(), email = email.trim(), phone = phone.trim(), notes = notes.trim(), accessCode = accessCode.trim(),
)

/** Adds a building, with its details when given; returns its id. */
class AddBuildingUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, name: String, info: BuildingInfo? = null): String {
        val id = repo.newNodeId("b")
        repo.edit(prop) { it + LocNode(id, name.trim(), NodeKind.BUILDING, emptyList(), info = info?.trimmed()) }
        return id
    }
}

/** Adds a floor to a building; returns its id. */
class AddFloorUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, buildingId: String, name: String): String {
        val id = repo.newNodeId("f")
        repo.edit(prop) { t -> t.mapNode(buildingId) { b -> b.copy(kids = b.kids.orEmpty() + LocNode(id, name.trim(), NodeKind.FLOOR, emptyList())) } }
        return id
    }
}

/** Edit building sheet. */
class SaveBuildingUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, id: String, name: String, info: BuildingInfo) =
        repo.edit(prop) { t -> t.mapNode(id) { it.copy(name = name.trim(), info = info.trimmed()) } }
}

/** Edit floors sheet: the building's floors in their new order, with names, types and codes; missing ones are removed. */
class SaveFloorsUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, buildingId: String, floors: List<LocNode>) = repo.edit(prop) { t ->
        t.mapNode(buildingId) { b ->
            val old = b.kids.orEmpty().associateBy { it.id }
            b.copy(kids = floors.mapNotNull { f -> old[f.id]?.copy(name = f.name.trim(), type = f.type.trim(), code = f.code.trim()) })
        }
    }
}

class RenameLocationNodeUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, id: String, name: String) = repo.edit(prop) { t -> t.mapNode(id) { it.copy(name = name.trim()) } }
}

/** Deletes buildings, floors or locations; false when there was nothing to delete. */
class DeleteLocationNodesUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        repo.edit(prop) { it.removeNodes(ids) }
        return true
    }
}

/** Adds one location, or several at once (one name per line; codes get -1, -2, … when given). Returns how many. */
class AddLocationsUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, floorId: String, names: List<String>, type: String, code: String): Int {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return 0
        val made = clean.mapIndexed { i, n ->
            val c = code.trim().let { if (it.isNotEmpty() && clean.size > 1) "$it-${i + 1}" else it }
            LocNode(repo.newNodeId("l"), n, NodeKind.LOCATION, null, 0, type.trim(), c)
        }
        repo.edit(prop) { t -> t.mapNode(floorId) { f -> f.copy(kids = f.kids.orEmpty() + made) } }
        return made.size
    }
}

class UpdateLocationUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(prop: String, id: String, name: String, type: String, code: String) =
        repo.edit(prop) { t -> t.mapNode(id) { it.copy(name = name.trim(), type = type.trim(), code = code.trim()) } }
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

    suspend operator fun invoke(prop: String, uri: String): Result {
        val rows = files.readSheet(uri) ?: return Result.Unreadable
        val data = rows.map { r -> List(5) { i -> r.getOrNull(i)?.trim().orEmpty() } }
            .filter { it[0].isNotEmpty() }
            .let { l -> if (l.firstOrNull()?.get(0)?.equals("building", ignoreCase = true) == true) l.drop(1) else l }
        if (data.isEmpty()) return Result.NothingFound
        var added = 0
        var t = repo.tree(prop)
        data.forEach { (bName, fName, lName, type, code) ->
            var b = t.firstOrNull { it.name.equals(bName, ignoreCase = true) }
            if (b == null) {
                b = LocNode(repo.newNodeId("b"), bName, NodeKind.BUILDING, emptyList())
                t = t + b
                added++
            }
            if (fName.isEmpty()) return@forEach
            var f = b.kids.orEmpty().firstOrNull { it.name.equals(fName, ignoreCase = true) }
            if (f == null) {
                val nf = LocNode(repo.newNodeId("f"), fName, NodeKind.FLOOR, emptyList())
                t = t.mapNode(b.id) { it.copy(kids = it.kids.orEmpty() + nf) }
                f = nf
                added++
            }
            if (lName.isEmpty() || f.kids.orEmpty().any { it.name.equals(lName, ignoreCase = true) }) return@forEach
            val l = LocNode(repo.newNodeId("l"), lName, NodeKind.LOCATION, null, 0, type, code)
            t = t.mapNode(f.id) { it.copy(kids = it.kids.orEmpty() + l) }
            added++
        }
        val tree = t
        repo.edit(prop) { tree }
        return Result.Imported(data.size, added)
    }
}

/** The location use cases, for injecting as one. */
class LocationUseCases @Inject constructor(
    val observeTrees: ObserveLocationTreesUseCase,
    val tree: GetLocationTreeUseCase,
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
