package com.sclera.sclerago.domain.model

// The rules of the places import tree (buildings › floors › locations), as the web's placesTree.js: which places can be
// ticked, what a tick on a building or floor takes along, and what the search and "hide existing" leave showing.

val PlaceNode.key: PlaceKey get() = PlaceKey(kind, id)

/** Not on the other side (new), or deleted there since (gone): ticking it adds it. */
val PlaceNode.isAddable: Boolean get() = status == PlaceStatus.NEW || status == PlaceStatus.GONE

/** On the other side but renamed or moved since: ticking it updates the other side. */
val PlaceNode.isChanged: Boolean get() = status == PlaceStatus.CHANGED

val PlaceNode.isSelectable: Boolean get() = isAddable || isChanged

/** The place itself, or something under it, can be ticked. */
fun PlaceNode.hasSelectable(): Boolean = isSelectable || children.any { it.hasSelectable() }

/** Every place in the tree, each followed by what is under it. */
fun List<PlaceNode>.allPlaces(): Sequence<PlaceNode> = asSequence().flatMap { sequenceOf(it) + it.children.allPlaces() }

/** Counts: every place, those that can be added (new or gone), and the changed ones. */
data class PlaceTally(val total: Int = 0, val fresh: Int = 0, val changed: Int = 0)

fun List<PlaceNode>.tally(): PlaceTally = allPlaces().fold(PlaceTally()) { t, n ->
    PlaceTally(t.total + 1, t.fresh + if (n.isAddable) 1 else 0, t.changed + if (n.isChanged) 1 else 0)
}

/** The keys of the changed places, to tell updates from additions in a selection. */
fun List<PlaceNode>.changedKeys(): Set<PlaceKey> = allPlaces().filter { it.isChanged }.map { it.key }.toSet()

/** "Select all": every place that can be added. Updates aren't included ([changedKeys] picks those). */
fun List<PlaceNode>.addableKeys(): Set<PlaceKey> = allPlaces().filter { it.isAddable }.map { it.key }.toSet()

/** How a selection splits into places to add and places to update. */
data class SelectionSplit(val add: Int, val update: Int)

fun Set<PlaceKey>.splitBy(changed: Set<PlaceKey>): SelectionSplit {
    val update = count { it in changed }
    return SelectionSplit(size - update, update)
}

/**
 * The keys a tick on this place adds or removes: the place itself when it can be ticked, and what under it can be added
 * (new or gone). Changed places under it aren't ticked along: updates are chosen one by one.
 */
fun PlaceNode.keysUnder(): List<PlaceKey> = buildList {
    if (isSelectable) add(key)
    children.forEach { child ->
        if (child.kind == PlaceKind.LOCATION) {
            if (child.isAddable) add(child.key)
        } else {
            addAll(child.keysUnder().filter { it != child.key || child.isAddable })
        }
    }
}

/**
 * The tree as shown: [search] applied (a match shows its parents and everything under it), and with [hideExisting], the
 * places already the same on the other side left out unless something under them can be ticked. Changed and gone places
 * always show.
 */
fun List<PlaceNode>.visible(search: String, hideExisting: Boolean): List<PlaceNode> {
    val q = search.trim().lowercase()
    fun keep(n: PlaceNode) = !hideExisting || n.hasSelectable()
    fun hit(n: PlaceNode) = q.isEmpty() || n.name.lowercase().contains(q) || n.otherName?.lowercase()?.contains(q) == true
    return mapNotNull { b ->
        if (!keep(b)) return@mapNotNull null
        val buildingHit = hit(b)
        val floors = b.children.mapNotNull { f ->
            if (!keep(f)) return@mapNotNull null
            val floorHit = buildingHit || hit(f)
            val locations = f.children.filter { keep(it) && (floorHit || hit(it)) }
            if (!floorHit && locations.isEmpty()) null else f.copy(children = locations)
        }
        if (!buildingHit && floors.isEmpty()) null else b.copy(children = floors)
    }
}
