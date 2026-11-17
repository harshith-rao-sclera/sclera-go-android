package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.MAX_PLACE_CODE
import com.sclera.sclerago.domain.model.MAX_PLACE_NAME
import com.sclera.sclerago.domain.model.MAX_PLACE_TYPE
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PlaceChange
import com.sclera.sclerago.domain.repository.ConfigPlacesRepository
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

// Configuration → Building info: the property's buildings, floors and locations at the server (as the web does it).
// Every call needs a connection and throws OnlineException; changes need the Configure permission.

/** Why a change to a place was refused (the server's reasons). */
enum class PlaceProblem {
    /** A building, floor or location of that name is already there. */
    DUPLICATE,

    /** A building that still has floors can't be deleted. */
    BUILDING_HAS_FLOORS,

    /** A floor that still has locations can't be deleted. */
    FLOOR_HAS_LOCATIONS,

    /** The place is still in use (assets are there). */
    IN_USE,
}

/** A conflict from the server ([OnlineError.Conflict]) as [problem]; other failures are thrown on. */
private inline fun <T> conflictAs(problem: PlaceProblem, block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: OnlineException) {
    if (e.error is OnlineError.Conflict) Result.failure(PlaceRefused(problem)) else throw e
}

/** A place change the server refused, with why. */
class PlaceRefused(val problem: PlaceProblem) : Exception(problem.name)

private fun String.cleanName() = trim().take(MAX_PLACE_NAME)
private fun String.cleanCode() = trim().take(MAX_PLACE_CODE)
private fun String.cleanType() = trim().take(MAX_PLACE_TYPE)

/** True when [name] is already used by one of [siblings] other than [except] (names compare without case). */
fun placeNameTaken(siblings: List<LocNode>, name: String, except: String? = null): Boolean =
    siblings.any { it.id != except && it.name.trim().equals(name.trim(), ignoreCase = true) }

class LoadPlacesUseCase @Inject constructor(private val repo: ConfigPlacesRepository) {
    suspend operator fun invoke(propertyId: String): List<LocNode> = repo.load(propertyId)
}

/** Adds a building (its details are kept on this phone). Returns its id, or why it was refused. */
class AddBuildingUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, tree: List<LocNode>, name: String, info: BuildingInfo?): Result<String> {
        val n = name.cleanName()
        if (placeNameTaken(tree, n)) return Result.failure(PlaceRefused(PlaceProblem.DUPLICATE))
        guard.requireOnline(Permission.CONFIGURE)
        // The access code is the building's code at the server, as the web saves it.
        return conflictAs(PlaceProblem.DUPLICATE) { repo.addBuilding(propertyId, n, info?.copy(accessCode = info.accessCode.cleanCode())) }
    }
}

/** What adding several floors or locations did: how many were added, how many were already there, and the first new id. */
data class PlacesAdded(val added: Int, val skipped: Int, val firstId: String? = null)

/**
 * Adds [rows] (each with its own name, type and code, as the web's add and bulk add) under [parentId], one request each.
 * Names already there ([siblings]) or repeated in the list are skipped.
 */
private suspend fun addPlaces(
    siblings: List<LocNode>,
    rows: List<NewLocation>,
    create: suspend (NewLocation) -> String,
): PlacesAdded {
    val clean = rows.map { NewLocation(it.name.cleanName(), it.type.cleanType(), it.code.cleanCode()) }.filter { it.name.isNotEmpty() }
    if (clean.isEmpty()) return PlacesAdded(0, 0)
    val taken = siblings.map { it.name.trim().lowercase() }.toMutableSet()
    var added = 0
    var first: String? = null
    clean.forEach { row ->
        if (!taken.add(row.name.lowercase())) return@forEach
        conflictAs(PlaceProblem.DUPLICATE) { create(row) }.getOrNull()?.let { id ->
            added++
            if (first == null) first = id
        }
    }
    return PlacesAdded(added, clean.size - added, first)
}

/** Adds one floor, or several at once, to [building], each with its name, type and code. */
class AddFloorsUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, building: LocNode, rows: List<NewLocation>): PlacesAdded {
        if (rows.none { it.name.isNotBlank() }) return PlacesAdded(0, 0)
        guard.requireOnline(Permission.CONFIGURE)
        return addPlaces(building.kids.orEmpty(), rows) { r -> repo.addFloor(propertyId, building.id, r.name, r.type, r.code) }
    }
}

/** Adds one location, or several at once, to [floor], each with its own name, type and code (as the web's bulk add). */
class AddLocationsUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, floor: LocNode, rows: List<NewLocation>): PlacesAdded {
        if (rows.none { it.name.isNotBlank() }) return PlacesAdded(0, 0)
        guard.requireOnline(Permission.CONFIGURE)
        return addPlaces(floor.kids.orEmpty(), rows) { r -> repo.addLocation(propertyId, floor.id, r) }
    }
}

/**
 * Edit building sheet: its name, visibility and access code (the building's code) at the server, its other details on
 * this phone. Null when saved, else why not.
 */
class SaveBuildingUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, tree: List<LocNode>, building: LocNode, name: String, info: BuildingInfo, visible: Boolean): PlaceProblem? {
        val n = name.cleanName()
        if (placeNameTaken(tree, n, except = building.id)) return PlaceProblem.DUPLICATE
        guard.requireOnline(Permission.CONFIGURE)
        val code = info.accessCode.cleanCode()
        val change = PlaceChange(
            name = n.takeIf { it != building.name },
            code = code.takeIf { it != building.code },
            active = visible.takeIf { it != building.active },
        )
        conflictAs(PlaceProblem.DUPLICATE) { repo.update(propertyId, NodeKind.BUILDING, building.id, change) }
            .onFailure { return (it as PlaceRefused).problem }
        repo.saveBuildingInfo(building.id, info.copy(accessCode = code))
        return null
    }
}

/**
 * Edit floors sheet: the building's floors in their new order, with names, types, codes and visibility; floors left
 * out are deleted (refused while a floor still has locations). Null when saved, else why not.
 */
class SaveFloorsUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, building: LocNode, floors: List<LocNode>): PlaceProblem? {
        val before = building.kids.orEmpty().associateBy { it.id }
        val kept = floors.filter { it.id in before }.map { it.copy(name = it.name.cleanName(), type = it.type.cleanType(), code = it.code.cleanCode()) }
        if (kept.map { it.name.lowercase() }.toSet().size != kept.size) return PlaceProblem.DUPLICATE
        val removed = before.values.filter { old -> kept.none { it.id == old.id } }
        if (removed.any { it.kids.orEmpty().isNotEmpty() }) return PlaceProblem.FLOOR_HAS_LOCATIONS
        guard.requireOnline(Permission.CONFIGURE)
        removed.forEach { f ->
            conflictAs(PlaceProblem.IN_USE) { repo.delete(propertyId, NodeKind.FLOOR, f.id) }.onFailure { return (it as PlaceRefused).problem }
        }
        kept.forEachIndexed { position, f ->
            val old = before.getValue(f.id)
            val change = PlaceChange(
                name = f.name.takeIf { it != old.name },
                type = f.type.takeIf { it != old.type },
                code = f.code.takeIf { it != old.code },
                active = f.active.takeIf { it != old.active },
                position = position.takeIf { old.id != building.kids.orEmpty().getOrNull(position)?.id },
            )
            conflictAs(PlaceProblem.DUPLICATE) { repo.update(propertyId, NodeKind.FLOOR, f.id, change) }.onFailure { return (it as PlaceRefused).problem }
        }
        return null
    }
}

/** A location's name, type and code. Null when saved, else why not. */
class UpdateLocationUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, floor: LocNode, location: LocNode, name: String, type: String, code: String): PlaceProblem? {
        val n = name.cleanName()
        if (n.isEmpty()) return null
        if (placeNameTaken(floor.kids.orEmpty(), n, except = location.id)) return PlaceProblem.DUPLICATE
        guard.requireOnline(Permission.CONFIGURE)
        val change = PlaceChange(
            name = n.takeIf { it != location.name },
            type = type.cleanType().takeIf { it != location.type },
            code = code.cleanCode().takeIf { it != location.code },
        )
        return conflictAs(PlaceProblem.DUPLICATE) { repo.update(propertyId, NodeKind.LOCATION, location.id, change) }
            .exceptionOrNull()?.let { (it as PlaceRefused).problem }
    }
}

/**
 * Deletes [places] (buildings, floors or locations) as the server allows: a building with floors, a floor with
 * locations, or a place in use is refused. Stops at the first refusal. Returns how many were deleted and why it stopped.
 */
class DeletePlacesUseCase @Inject constructor(private val repo: ConfigPlacesRepository, private val guard: PermissionGuard) {
    data class Deleted(val deleted: Int, val problem: PlaceProblem?)

    suspend operator fun invoke(propertyId: String, places: List<LocNode>): Deleted {
        places.firstOrNull { it.kind == NodeKind.BUILDING && it.kids.orEmpty().isNotEmpty() }?.let { return Deleted(0, PlaceProblem.BUILDING_HAS_FLOORS) }
        places.firstOrNull { it.kind == NodeKind.FLOOR && it.kids.orEmpty().isNotEmpty() }?.let { return Deleted(0, PlaceProblem.FLOOR_HAS_LOCATIONS) }
        if (places.isEmpty()) return Deleted(0, null)
        guard.requireOnline(Permission.CONFIGURE)
        var n = 0
        places.forEach { p ->
            val refused = conflictAs(PlaceProblem.IN_USE) { repo.delete(propertyId, p.kind, p.id) }.exceptionOrNull()
            if (refused != null) return Deleted(n, (refused as PlaceRefused).problem)
            n++
        }
        return Deleted(n, null)
    }
}

/**
 * Import buildings: rows of Building, Floor, Location, Type, Code (a header row is skipped) from a CSV or Excel file,
 * added at the server one by one. Buildings and floors are matched by name; locations already there are left alone.
 */
class ImportBuildingsUseCase @Inject constructor(
    private val repo: ConfigPlacesRepository,
    private val files: FileRepository,
    private val guard: PermissionGuard,
) {
    sealed interface Result {
        data object Unreadable : Result
        data object NothingFound : Result

        /** [rows] read from the file; [added] buildings, floors and locations that were new. */
        data class Imported(val rows: Int, val added: Int) : Result
    }

    suspend operator fun invoke(propertyId: String, tree: List<LocNode>, uri: String): Result {
        val rows = files.readSheet(uri) ?: return Result.Unreadable
        val data = rows.map { r -> List(5) { i -> r.getOrNull(i)?.trim().orEmpty() } }
            .filter { it[0].isNotEmpty() }
            .let { l -> if (l.firstOrNull()?.get(0)?.equals("building", ignoreCase = true) == true) l.drop(1) else l }
        if (data.isEmpty()) return Result.NothingFound
        guard.requireOnline(Permission.CONFIGURE)
        // Name (lower case) → id, filled as places are found or made.
        val buildings = tree.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
        val floors = tree.flatMap { b -> b.kids.orEmpty().map { f -> (b.id to f.name.trim().lowercase()) to f.id } }.toMap().toMutableMap()
        val locations = tree.flatMap { b -> b.kids.orEmpty().flatMap { f -> f.kids.orEmpty().map { l -> f.id to l.name.trim().lowercase() } } }.toMutableSet()
        var added = 0
        data.forEach { (building, floor, location, type, code) ->
            val bName = building.cleanName()
            val bId = buildings[bName.lowercase()]
                ?: conflictAs(PlaceProblem.DUPLICATE) { repo.addBuilding(propertyId, bName, null) }.getOrNull()?.also { buildings[bName.lowercase()] = it; added++ }
                ?: return@forEach
            val fName = floor.cleanName()
            if (fName.isEmpty()) return@forEach
            val fId = floors[bId to fName.lowercase()]
                ?: conflictAs(PlaceProblem.DUPLICATE) { repo.addFloor(propertyId, bId, fName) }.getOrNull()?.also { floors[bId to fName.lowercase()] = it; added++ }
                ?: return@forEach
            val lName = location.cleanName()
            if (lName.isEmpty() || !locations.add(fId to lName.lowercase())) return@forEach
            if (conflictAs(PlaceProblem.DUPLICATE) { repo.addLocation(propertyId, fId, NewLocation(lName, type.cleanType(), code.cleanCode())) }.isSuccess) added++
        }
        return Result.Imported(data.size, added)
    }
}

/** The Building info use cases, for injecting as one. */
class PlacesConfigUseCases @Inject constructor(
    val load: LoadPlacesUseCase,
    val addBuilding: AddBuildingUseCase,
    val addFloors: AddFloorsUseCase,
    val addLocations: AddLocationsUseCase,
    val saveBuilding: SaveBuildingUseCase,
    val saveFloors: SaveFloorsUseCase,
    val updateLocation: UpdateLocationUseCase,
    val delete: DeletePlacesUseCase,
    val importBuildings: ImportBuildingsUseCase,
)
