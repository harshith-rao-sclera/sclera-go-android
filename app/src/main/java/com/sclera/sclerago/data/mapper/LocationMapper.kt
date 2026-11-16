package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind

/**
 * The building → floor → location tree from the three tables' rows. Inactive rows are left out and
 * siblings follow `position`. [counts] is the number of assets per node id; a node without an entry
 * counts the assets of its children.
 */
fun buildLocationTree(
    buildings: List<DbRow>,
    floors: List<DbRow>,
    locations: List<DbRow>,
    counts: Map<String, Int> = emptyMap(),
): List<LocNode> {
    val locsByFloor = locations.active().groupBy { it.string("floor_id") }
    val floorsByBuilding = floors.active().groupBy { it.string("building_id") }
    fun locNode(r: DbRow) = r.toNode(NodeKind.LOCATION, null, counts[r.string("id")] ?: 0)
    fun floorNode(r: DbRow): LocNode {
        val kids = locsByFloor[r.string("id")].orEmpty().map(::locNode)
        return r.toNode(NodeKind.FLOOR, kids, counts[r.string("id")] ?: kids.sumOf { it.count })
    }
    return buildings.active().map { b ->
        val kids = floorsByBuilding[b.string("id")].orEmpty().map(::floorNode)
        b.toNode(NodeKind.BUILDING, kids, counts[b.string("id")] ?: kids.sumOf { it.count })
    }
}

private fun List<DbRow>.active() = filter { it.bool("is_active") }.sortedBy { it.int("position") ?: 0 }

private fun DbRow.toNode(kind: NodeKind, kids: List<LocNode>?, count: Int) = LocNode(
    id = string("id").orEmpty(),
    name = string("name").orEmpty(),
    kind = kind,
    kids = kids,
    count = count,
    type = string("type").orEmpty(),
    code = string("code").orEmpty(),
)

/** Where each location is, by location id, for [toAsset][com.sclera.sclerago.data.mapper.toAsset]: its name and "location · floor". */
fun placeLabels(floors: List<DbRow>, locations: List<DbRow>): Map<String, PlaceLabel> {
    val floorNames = floors.associate { it.string("id") to it.string("name").orEmpty() }
    return locations.mapNotNull { l ->
        val id = l.string("id") ?: return@mapNotNull null
        val name = l.string("name").orEmpty()
        id to PlaceLabel(name, listOf(name, floorNames[l.string("floor_id")]).filter { !it.isNullOrBlank() }.joinToString(" · "))
    }.toMap()
}

/** The `buildings`, `floors` or `locations` columns to insert or update for [this]; [parentId] is the floor's building or the location's floor. */
fun LocNode.toLocationRow(parentId: String?, position: Int, version: Int = 1): Map<String, Any?> = buildMap {
    put("id", id)
    when (kind) {
        NodeKind.FLOOR -> put("building_id", parentId)
        NodeKind.LOCATION -> put("floor_id", parentId)
        NodeKind.BUILDING -> Unit
    }
    put("name", name)
    put("code", code.ifBlank { null })
    if (kind != NodeKind.BUILDING) put("type", type.ifBlank { null })
    put("is_active", 1)
    put("position", position)
    put("version", version)
}
