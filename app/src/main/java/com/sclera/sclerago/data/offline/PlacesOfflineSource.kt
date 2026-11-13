package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.PlaceLabel
import com.sclera.sclerago.data.mapper.buildLocationTree
import com.sclera.sclerago.data.mapper.placeLabels
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.PathStep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val LOCATION_PATH = """
    SELECT b.id AS bid, b.name AS bname, f.id AS fid, f.name AS fname, l.id AS lid, l.name AS lname
    FROM locations l JOIN floors f ON f.id = l.floor_id JOIN buildings b ON b.id = f.building_id
    WHERE l.id = ?"""

private const val FLOOR_PATH = """
    SELECT b.id AS bid, b.name AS bname, f.id AS fid, f.name AS fname
    FROM floors f JOIN buildings b ON b.id = f.building_id WHERE f.id = ?"""

private const val BUILDING_PATH = "SELECT id AS bid, name AS bname FROM buildings WHERE id = ?"

/** Where assets are: the building, floor and location tree and the way to a place. Reads only. */
class PlacesOfflineSource(private val db: AdcDatabase, private val io: CoroutineDispatcher) {

    /** Buildings with their floors and locations; each node counts the assets in it. */
    suspend fun tree(): List<LocNode> = withContext(io) { loadTree() }

    fun treeFlow(): Flow<List<LocNode>> = db.watch(io, *PLACE_TABLES) { loadTree() }

    /** Buildings only (no floors), each with how many assets it holds. Building info is not in the database yet. */
    suspend fun buildings(): List<LocNode> = withContext(io) { loadBuildings() }

    fun buildingsFlow(): Flow<List<LocNode>> = db.watch(io, *PLACE_TABLES) { loadBuildings() }

    /** Building, floor, location (or the shorter way to a floor or building) of [placeId]; empty when it is not there. */
    suspend fun findLocationPath(placeId: String): List<PathStep> = withContext(io) { loadPath(placeId) }

    /** A lookup of place names for building asset labels. Blocking: call it on the IO dispatcher. */
    internal fun index(): PlaceIndex = db.whenOpen(PlaceIndex.EMPTY) {
        val floors = rows("floors")
        val buildings = rows("buildings")
        val locations = rows("locations")
        PlaceIndex(
            labels = placeLabels(floors, locations),
            floorNames = floors.associate { it.string("id").orEmpty() to it.string("name").orEmpty() },
            buildingNames = buildings.associate { it.string("id").orEmpty() to it.string("name").orEmpty() },
        )
    }

    private fun loadTree(): List<LocNode> = db.whenOpen(emptyList()) {
        buildLocationTree(rows("buildings"), rows("floors"), rows("locations"), counts())
    }

    private fun loadBuildings(): List<LocNode> = db.whenOpen(emptyList()) {
        buildLocationTree(rows("buildings"), emptyList(), emptyList(), counts())
    }

    private fun rows(table: String): List<DbRow> =
        db.whenTable(table, emptyList()) { db.query("SELECT * FROM $table WHERE ${db.notRemoved(table)}") }

    /** Assets per building, floor and location id: one grouped query. */
    private fun counts(): Map<String, Int> = db.whenTable("assets", emptyMap()) {
        val live = db.notRemoved("assets")
        val sql = listOf("building_id", "floor_id", "location_id").joinToString(" UNION ALL ") { col ->
            "SELECT $col AS id, COUNT(*) AS n FROM assets WHERE $col IS NOT NULL AND $live GROUP BY $col"
        }
        db.query(sql).associate { it.string("id").orEmpty() to (it.int("n") ?: 0) }
    }

    private fun loadPath(placeId: String): List<PathStep> = db.whenOpen(emptyList()) {
        if (!db.hasTable("buildings")) return@whenOpen emptyList()
        val hasFloors = db.hasTable("floors")
        val hasLocations = hasFloors && db.hasTable("locations")
        val row = if (hasLocations) db.queryOne(LOCATION_PATH, listOf(placeId)) else null
        if (row != null) return@whenOpen listOfNotNull(building(row), floor(row), step(row, "lid", "lname", NodeKind.LOCATION))
        val floorRow = if (hasFloors) db.queryOne(FLOOR_PATH, listOf(placeId)) else null
        if (floorRow != null) return@whenOpen listOfNotNull(building(floorRow), floor(floorRow))
        listOfNotNull(db.queryOne(BUILDING_PATH, listOf(placeId))?.let(::building))
    }

    private fun building(r: DbRow) = step(r, "bid", "bname", NodeKind.BUILDING)
    private fun floor(r: DbRow) = step(r, "fid", "fname", NodeKind.FLOOR)
    private fun step(r: DbRow, id: String, name: String, kind: NodeKind): PathStep? =
        r.string(id)?.let { PathStep(it, r.string(name).orEmpty(), kind) }

    private companion object {
        val PLACE_TABLES = arrayOf("buildings", "floors", "locations", "assets")
    }
}

/** Place names by id, for the "where" text of an asset. */
internal class PlaceIndex(
    private val labels: Map<String, PlaceLabel>,
    private val floorNames: Map<String, String>,
    private val buildingNames: Map<String, String>,
) {
    /** The location's name and "location · floor", or null when the asset has no known location. */
    fun location(locationId: String?): PlaceLabel? = locationId?.let(labels::get)

    /** "floor · building" for an asset that has no location, empty when it has neither. */
    fun floorOrBuilding(floorId: String?, buildingId: String?): String =
        listOfNotNull(floorNames[floorId], buildingNames[buildingId]).filter { it.isNotBlank() }.joinToString(" · ")

    companion object {
        val EMPTY = PlaceIndex(emptyMap(), emptyMap(), emptyMap())
    }
}
