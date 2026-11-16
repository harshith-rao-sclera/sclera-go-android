package com.sclera.sclerago.domain.model

/** The names of the building, floor and location an asset is filed under (null where it has none, or the place is gone). */
data class PlaceNames(val building: String? = null, val floor: String? = null, val location: String? = null)

/**
 * The names of the places with these ids in the property's [tree], each filed by its kind together with the places
 * above it, so one id (a new asset carries only the place it goes in) still names its floor and building.
 */
fun placeNames(tree: List<LocNode>, vararg ids: String?): PlaceNames {
    val parentOf = HashMap<String, LocNode?>()
    val byId = HashMap<String, LocNode>()
    fun walk(nodes: List<LocNode>?, parent: LocNode?) {
        nodes?.forEach { n ->
            byId[n.id] = n
            parentOf[n.id] = parent
            walk(n.kids, n)
        }
    }
    walk(tree, null)
    val names = HashMap<NodeKind, String>()
    ids.filterNotNull().forEach { id ->
        var node = byId[id]
        while (node != null) {
            if (node.name.isNotBlank()) names.putIfAbsent(node.kind, node.name)
            node = parentOf[node.id]
        }
    }
    return PlaceNames(names[NodeKind.BUILDING], names[NodeKind.FLOOR], names[NodeKind.LOCATION])
}
