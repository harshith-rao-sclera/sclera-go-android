package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.MissingParent
import com.sclera.sclerago.domain.model.PlaceCounts
import com.sclera.sclerago.domain.model.PlaceKind
import com.sclera.sclerago.domain.model.PlaceNode
import com.sclera.sclerago.domain.model.PlaceOutcome
import com.sclera.sclerago.domain.model.PlaceSkipReason
import com.sclera.sclerago.domain.model.PlaceStatus
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.SelectionSplit
import com.sclera.sclerago.presentation.common.UiText

// The places import's wording, as the web's placesTree.js: what a row's badge says, the submit button, the result and
// the skipped places, worded for the side the places are chosen from.

/** "3 locations, 1 floor" style counts (buildings, floors, locations; zeros left out); null when there are none. */
fun placeCountsText(c: PlaceCounts): UiText? = joined(
    listOfNotNull(
        c.buildings.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.pi_count_buildings, it) },
        c.floors.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.pi_count_floors, it) },
        c.locations.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.pi_count_locations, it) },
    ),
)

/** Two or three parts as a list ("a, b" / "a, b, c"); one part as it is; null for none. */
private fun joined(parts: List<UiText>): UiText? = when (parts.size) {
    0 -> null
    1 -> parts[0]
    2 -> UiText.Res(R.string.pi_join_2, parts)
    else -> UiText.Res(R.string.pi_join_3, parts.take(3))
}

/** A row's badge: its label and, for a changed place, what differs on the other side. */
data class PlaceBadge(val label: UiText, val tone: PlaceStatus, val detail: UiText? = null)

fun badgeOf(n: PlaceNode, direction: PlacesDirection): PlaceBadge? {
    val toVdms = direction == PlacesDirection.TO_VDMS
    return when (n.status) {
        PlaceStatus.NEW -> null
        PlaceStatus.SAME -> PlaceBadge(UiText.Res(if (toVdms) R.string.pi_badge_same_vdms else R.string.pi_badge_same_here), n.status)
        PlaceStatus.GONE -> PlaceBadge(UiText.Res(if (toVdms) R.string.pi_badge_gone_vdms else R.string.pi_badge_gone_here), n.status)
        PlaceStatus.CHANGED -> PlaceBadge(
            UiText.Res(R.string.pi_badge_changed), n.status,
            n.otherName?.takeIf { it != n.name }?.let {
                UiText.Res(if (toVdms) R.string.pi_detail_vdms else R.string.pi_detail_here, listOf(UiText.Raw(it)))
            } ?: UiText.Res(R.string.pi_detail_moved),
        )
    }
}

/** "Export 3 to VDMS", "Export 3, update 1", "Update 2 in VDMS"; "Import 3", "Import 3, update 1", "Update 2 here". */
fun submitText(direction: PlacesDirection, split: SelectionSplit): UiText {
    val toVdms = direction == PlacesDirection.TO_VDMS
    return when {
        split.update == 0 -> UiText.Plural(if (toVdms) R.plurals.pi_submit_send else R.plurals.pi_submit_import, split.add)
        split.add == 0 -> UiText.Plural(if (toVdms) R.plurals.pi_submit_update_vdms else R.plurals.pi_submit_update_here, split.update)
        else -> UiText.Res(if (toVdms) R.string.pi_submit_send_update else R.string.pi_submit_import_update, listOf(split.add, split.update))
    }
}

/** The result's first line: how many were added and updated, or that nothing was. */
fun resultHead(r: PlacesImportResult, direction: PlacesDirection): UiText {
    val toVdms = direction == PlacesDirection.TO_VDMS
    val added = r.imported.size
    val updated = r.updated.size
    return when {
        added > 0 && updated > 0 ->
            UiText.Res(if (toVdms) R.string.pi_result_sent_updated else R.string.pi_result_imported_updated, listOf(added, updated))
        added > 0 -> UiText.Plural(if (toVdms) R.plurals.pi_result_sent else R.plurals.pi_result_imported, added)
        updated > 0 -> UiText.Plural(R.plurals.pi_result_updated, updated)
        else -> UiText.Res(if (toVdms) R.string.pi_result_nothing_exported else R.string.pi_result_nothing)
    }
}

/** "Lobby (location) updated in VDMS" / "… updated here". */
fun updatedText(o: PlaceOutcome, direction: PlacesDirection): UiText =
    UiText.Res(
        if (direction == PlacesDirection.TO_VDMS) R.string.pi_updated_vdms else R.string.pi_updated_here,
        listOf(UiText.Raw(o.name ?: o.id), kindText(o.kind)),
    )

/** "Lobby (location) already exists — skipped", and the other reasons. */
fun skippedText(o: PlaceOutcome, direction: PlacesDirection): UiText {
    val res = when (o.reason) {
        PlaceSkipReason.EXISTS -> R.string.pi_skip_exists
        PlaceSkipReason.PARENT_MISSING -> if (direction == PlacesDirection.TO_VDMS) R.string.pi_skip_parent_vdms else R.string.pi_skip_parent_here
        PlaceSkipReason.NOT_FOUND -> R.string.pi_skip_not_found
        PlaceSkipReason.ERROR -> R.string.pi_skip_error
        PlaceSkipReason.OTHER, null -> R.string.pi_skip_other
    }
    return UiText.Res(res, listOf(UiText.Raw(o.name ?: o.id), kindText(o.kind)))
}

/**
 * After a tick brought parents along: "Also ticked Ground (floor) and Annex (building): they aren't in VDMS yet, and a
 * location can't be added without its floor and building."
 */
fun parentsAddedText(added: List<MissingParent>, direction: PlacesDirection): UiText {
    val names = added.map { UiText.Res(R.string.pi_place_named, listOf(UiText.Raw(it.name), kindText(it.key.kind))) }
    val list = if (names.size == 1) names[0] else UiText.Res(R.string.pi_list_and, listOf(joinedNames(names.dropLast(1)), names.last()))
    return UiText.Res(if (direction == PlacesDirection.TO_VDMS) R.string.pi_parents_added_vdms else R.string.pi_parents_added_here, listOf(list))
}

/** "a, b, c" for the names before the last of [parentsAddedText]'s list. */
private fun joinedNames(names: List<UiText>): UiText =
    names.drop(1).fold(names.first()) { acc, n -> UiText.Res(R.string.pi_join_2, listOf(acc, n)) }

/** After unticking a parent took ticked places under it with it. */
fun orphansRemovedText(removed: Int): UiText = UiText.Plural(R.plurals.pi_orphans_removed, removed)

/** "Building", "Floor", "Location": a place's kind as the history's rows label it. */
fun kindLabel(kind: PlaceKind?): UiText = UiText.Res(
    when (kind) {
        PlaceKind.BUILDING -> R.string.pi_kind_building_label
        PlaceKind.FLOOR -> R.string.pi_kind_floor_label
        PlaceKind.LOCATION -> R.string.pi_kind_location_label
        null -> R.string.pi_kind_place_label
    },
)

private fun kindText(kind: PlaceKind?): UiText = UiText.Res(
    when (kind) {
        PlaceKind.BUILDING -> R.string.pi_kind_building
        PlaceKind.FLOOR -> R.string.pi_kind_floor
        PlaceKind.LOCATION -> R.string.pi_kind_location
        null -> R.string.pi_kind_place
    },
)
