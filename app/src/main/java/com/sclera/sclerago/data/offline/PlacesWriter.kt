package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.PlaceRow
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Writes the building, floor and location tables. Names are unique within their parent (as on the server), a place
 * that still holds floors, locations or assets cannot be removed, and removing is marking removed.
 */
class PlacesWriter(
    private val db: AdcDatabase,
    private val io: CoroutineDispatcher,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    /** Adds a building at the end of the list; returns its id. */
    suspend fun addBuilding(name: String): String = db.write(io) { insertBuilding(name) }

    /** Adds a floor to [buildingId]; returns its id. */
    suspend fun addFloor(buildingId: String, name: String): String = db.write(io) {
        if (!exists(live(BUILDING_ROW, "buildings"), listOf(buildingId))) throw DataException(DataError.FAILED)
        insertFloor(buildingId, name, "", "")
    }

    /** Renames a building, floor or location. */
    suspend fun rename(id: String, name: String) = db.write(io) {
        requireName(name)
        val now = now()
        if (exists(live(BUILDING_ROW, "buildings"), listOf(id))) {
            checkFree(live(BUILDING_NAME_TAKEN, "buildings"), listOf(name, id))
            execute(BUILDING_UPDATE, listOf(name, now, id), setOf("buildings"))
            return@write
        }
        val floor = queryOne(live(FLOOR_ROW, "floors"), listOf(id))
        if (floor != null) {
            checkFree(live(FLOOR_NAME_TAKEN, "floors"), listOf(floor.string("building_id").orEmpty(), name, id))
            execute(FLOOR_UPDATE, listOf(name, floor.string("type"), floor.string("code"), floor.int("position") ?: 0, now, id), setOf("floors"))
            return@write
        }
        val location = queryOne(live(LOCATION_ROW, "locations"), listOf(id)) ?: throw DataException(DataError.FAILED)
        checkFree(live(LOCATION_NAME_TAKEN, "locations"), listOf(location.string("floor_id").orEmpty(), name, id))
        execute(LOCATION_UPDATE, listOf(name, location.string("type"), location.string("code"), now, id), setOf("locations"))
    }

    /** Edits a location's name, type and code. */
    suspend fun updateLocation(id: String, name: String, type: String, code: String) = db.write(io) {
        requireName(name)
        val location = queryOne(live(LOCATION_ROW, "locations"), listOf(id)) ?: throw DataException(DataError.FAILED)
        checkFree(live(LOCATION_NAME_TAKEN, "locations"), listOf(location.string("floor_id").orEmpty(), name, id))
        execute(LOCATION_UPDATE, listOf(name, type.orNull(), code.orNull(), now(), id), setOf("locations"))
    }

    /** Sets the floors of [buildingId] to [floors] in that order: names, types and codes are updated and floors left out are removed. */
    suspend fun saveFloors(buildingId: String, floors: List<LocNode>) = db.write(io) {
        val current = query(live(FLOORS_OF_BUILDING, "floors"), listOf(buildingId)).associateBy { it.string("id").orEmpty() }
        val kept = floors.filter { it.id in current }
        kept.forEach { requireName(it.name) }
        if (kept.map { it.name.lowercase() }.toSet().size != kept.size) throw DataException(DataError.NAME_TAKEN)
        val now = now()
        (current.keys - kept.map { it.id }.toSet()).forEach { removeFloor(it, now) }
        kept.forEachIndexed { position, f ->
            val old = current.getValue(f.id)
            val changed = old.string("name") != f.name || old.string("type").orEmpty() != f.type ||
                old.string("code").orEmpty() != f.code || (old.int("position") ?: 0) != position
            if (changed) execute(FLOOR_UPDATE, listOf(f.name, f.type.orNull(), f.code.orNull(), position, now, f.id), setOf("floors"))
        }
    }

    /** Adds [locations] to [floorId]; returns how many. */
    suspend fun addLocations(floorId: String, locations: List<NewLocation>): Int = db.write(io) {
        if (!exists(live(FLOOR_ROW, "floors"), listOf(floorId))) throw DataException(DataError.FAILED)
        locations.forEach { insertLocation(floorId, it.name, it.type, it.code) }
        locations.size
    }

    /** Removes the places [placeIds]; throws IN_USE (and removes nothing) when one still holds floors, locations or assets. */
    suspend fun delete(placeIds: Set<String>) = db.write(io) {
        val now = now()
        val locations = placeIds.filter { exists(live(LOCATION_ROW, "locations"), listOf(it)) }
        val floors = placeIds.filter { exists(live(FLOOR_ROW, "floors"), listOf(it)) }
        val buildings = placeIds.filter { exists(live(BUILDING_ROW, "buildings"), listOf(it)) }
        locations.forEach {
            if (exists(live(ASSET_AT_LOCATION, "assets"), listOf(it))) throw DataException(DataError.IN_USE)
            removeRows("locations", "id = ?", listOf(it), now)
        }
        floors.forEach { removeFloor(it, now) }
        buildings.forEach {
            if (exists(live(FLOOR_IN_USE, "floors"), listOf(it)) || exists(live(ASSET_IN_BUILDING, "assets"), listOf(it))) {
                throw DataException(DataError.IN_USE)
            }
            removeRows("buildings", "id = ?", listOf(it), now)
        }
    }

    /** Adds the buildings, floors and locations of [rows] that are not there yet (matched by name); returns how many were new. */
    suspend fun import(rows: List<PlaceRow>): Int = db.write(io) {
        var added = 0
        rows.forEach { row ->
            val building = queryOne(live(BUILDING_BY_NAME, "buildings"), listOf(row.building))?.string("id")
                ?: insertBuilding(row.building).also { added++ }
            if (row.floor.isEmpty()) return@forEach
            val floor = queryOne(live(FLOOR_BY_NAME, "floors"), listOf(building, row.floor))?.string("id")
                ?: insertFloor(building, row.floor, "", "").also { added++ }
            if (row.location.isEmpty() || exists(live(LOCATION_BY_NAME, "locations"), listOf(floor, row.location))) return@forEach
            insertLocation(floor, row.location, row.type, row.code)
            added++
        }
        added
    }

    // ---- inside a transaction

    private fun AdcDatabase.insertBuilding(name: String): String {
        requireName(name)
        checkFree(live(BUILDING_NAME_TAKEN, "buildings"), listOf(name, ""))
        val id = ids.next()
        val now = now()
        val position = queryOne(live(BUILDING_NEXT_POSITION, "buildings"))?.int("p") ?: 0
        execute(BUILDING_INSERT, listOf(id, name, position, now, now), setOf("buildings"))
        return id
    }

    private fun AdcDatabase.insertFloor(buildingId: String, name: String, type: String, code: String): String {
        requireName(name)
        checkFree(live(FLOOR_NAME_TAKEN, "floors"), listOf(buildingId, name, ""))
        val id = ids.next()
        val now = now()
        val position = queryOne(live(FLOOR_NEXT_POSITION, "floors"), listOf(buildingId))?.int("p") ?: 0
        execute(FLOOR_INSERT, listOf(id, buildingId, name, code.orNull(), type.orNull(), position, now, now), setOf("floors"))
        return id
    }

    private fun AdcDatabase.insertLocation(floorId: String, name: String, type: String, code: String): String {
        requireName(name)
        checkFree(live(LOCATION_NAME_TAKEN, "locations"), listOf(floorId, name, ""))
        val id = ids.next()
        val now = now()
        val position = queryOne(live(LOCATION_NEXT_POSITION, "locations"), listOf(floorId))?.int("p") ?: 0
        execute(LOCATION_INSERT, listOf(id, floorId, name, code.orNull(), type.orNull(), position, now, now), setOf("locations"))
        return id
    }

    private fun AdcDatabase.removeFloor(id: String, now: String) {
        if (exists(live(LOCATION_IN_USE, "locations"), listOf(id)) || exists(live(ASSET_ON_FLOOR, "assets"), listOf(id))) {
            throw DataException(DataError.IN_USE)
        }
        removeRows("floors", "id = ?", listOf(id), now)
    }

    /** Throws NAME_TAKEN when [sql], a name-taken query, finds a row for [args]. */
    private fun AdcDatabase.checkFree(sql: String, args: List<Any>) {
        if (exists(sql, args)) throw DataException(DataError.NAME_TAKEN)
    }

    private fun requireName(name: String) {
        if (name.isBlank()) throw DataException(DataError.FAILED)
    }

    private fun now() = formatDbTime(time.nowMillis())

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }
}
