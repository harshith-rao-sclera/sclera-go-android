package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlaceNode
import com.sclera.sclerago.domain.model.PlaceTally
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.SelectionSplit
import com.sclera.sclerago.domain.model.VdmsPlaces
import com.sclera.sclerago.domain.model.addableKeys
import com.sclera.sclerago.domain.model.changedKeys
import com.sclera.sclerago.domain.model.isSelectable
import com.sclera.sclerago.domain.model.key
import com.sclera.sclerago.domain.model.keysUnder
import com.sclera.sclerago.domain.model.splitBy
import com.sclera.sclerago.domain.model.tally
import com.sclera.sclerago.domain.model.visible
import com.sclera.sclerago.presentation.common.UiText

/** A row's tick box: ticked, partly ticked (some of what is under it), or not. */
enum class Tick { ON, PARTLY, OFF }

/** One row of the tree as shown: the place (with only its shown children), how deep it sits, and its tick box. */
data class PlaceTreeRow(
    val node: PlaceNode,
    val depth: Int,
    val open: Boolean,
    val tick: Tick,
    /** Something can be ticked with it (itself, or something new under it). */
    val enabled: Boolean,
)

/** The picker's view of the tree, worked out from the places, the search, "hide existing" and the selection. */
data class PlacesPickerView(
    val tally: PlaceTally = PlaceTally(),
    val split: SelectionSplit = SelectionSplit(0, 0),
    val rows: List<PlaceTreeRow> = emptyList(),
    /** Nothing shown (the search matches nothing). */
    val noneShown: Boolean = false,
    /** What "Select all" ticks: the places shown that can be added. */
    val addable: Set<PlaceKey> = emptySet(),
    /** What "Select all changes" ticks: the changed places shown. */
    val changes: Set<PlaceKey> = emptySet(),
)

/** The Send to VDMS / Import from VDMS sheet. */
data class PlacesSheetState(
    val direction: PlacesDirection,
    val search: String = "",
    val hideExisting: Boolean = true,
    val selected: Set<PlaceKey> = emptySet(),
    val expanded: Set<PlaceKey> = emptySet(),
    val submitting: Boolean = false,
    /** A send VDMS is working on: the sheet waits for it (a minute at most). */
    val waiting: Boolean = false,
    /** VDMS didn't finish the send within the wait; its result shows on the page later. */
    val timedOut: Boolean = false,
    val result: PlacesImportResult? = null,
    val view: PlacesPickerView = PlacesPickerView(),
) {
    val searching: Boolean get() = search.isNotBlank()
    val done: Boolean get() = result != null || timedOut
}

data class PlacesUiState(
    val propertyId: String = "",
    /** Sending and importing can be used (not for the plain "user" role, as on the web). */
    val canImport: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val places: VdmsPlaces? = null,
    /** VDMS was asked for its list and hasn't answered yet. */
    val refreshing: Boolean = false,
    /** VDMS didn't answer within the wait; its list updates once it does. */
    val refreshLate: Boolean = false,
    val sheet: PlacesSheetState? = null,
    val message: UiText? = null,
) {
    val pushActive: Boolean get() = places?.push?.active == true

    /** VDMS hasn't sent its list yet, so nothing can be imported from it. */
    fun noSnapshot(direction: PlacesDirection): Boolean = direction == PlacesDirection.FROM_VDMS && places?.snapshotAt == null
}

/** The sheet with its [PlacesSheetState.view] worked out again from [places] (the tree for its direction). */
internal fun PlacesSheetState.withView(places: VdmsPlaces?): PlacesSheetState {
    val tree = places?.treeFor(direction).orEmpty()
    val shown = tree.visible(search, hideExisting)
    val rows = buildList {
        fun walk(nodes: List<PlaceNode>, depth: Int) {
            nodes.forEach { n ->
                val open = n.children.isNotEmpty() && (searching || n.key in expanded)
                val under = n.keysUnder()
                val on = if (n.isSelectable) n.key in selected else under.isNotEmpty() && under.all { it in selected }
                val tick = when {
                    on -> Tick.ON
                    under.any { it in selected } -> Tick.PARTLY
                    else -> Tick.OFF
                }
                add(PlaceTreeRow(n, depth, open, tick, enabled = under.isNotEmpty()))
                if (open) walk(n.children, depth + 1)
            }
        }
        walk(shown, 0)
    }
    return copy(
        view = PlacesPickerView(
            tally = tree.tally(),
            split = selected.splitBy(tree.changedKeys()),
            rows = rows,
            noneShown = shown.isEmpty(),
            addable = shown.addableKeys(),
            changes = shown.changedKeys(),
        ),
    )
}
