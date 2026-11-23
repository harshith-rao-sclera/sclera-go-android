package com.sclera.sclerago.presentation.integration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.PlaceKind
import com.sclera.sclerago.domain.model.PlaceStatus
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Send buildings, floors & locations to VDMS, or import them from VDMS, as the web's dialog: the side's tree with what the
 * other side already has, a search, "hide existing", ticks (a building or floor ticks what is new under it), then the
 * result. A send is done by VDMS in the background: the sheet waits up to a minute for it. Docked on phones, centred on tablets.
 */
@Composable
internal fun PlacesImportSheet(sh: PlacesSheetState, s: PlacesUiState, vm: PlacesImportViewModel) {
    val toVdms = sh.direction == PlacesDirection.TO_VDMS
    val noSnapshot = s.noSnapshot(sh.direction)
    val busy = sh.waiting && !sh.timedOut
    GuardedClose(sh.selected.isNotEmpty() && !sh.done && !sh.waiting, vm::close) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(if (toVdms) R.string.pi_to_title else R.string.pi_from_title), null, close)
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("places_sheet")) {
                when {
                    sh.result != null -> ResultBody(sh)
                    sh.waiting || sh.timedOut -> WaitingBody(sh.timedOut)
                    else -> Picker(sh, s, noSnapshot, vm)
                }
            }
            Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (sh.done) {
                    Pill(
                        stringResource(R.string.int_done), close,
                        Modifier.fillMaxWidth().testTag("places_done"), bg = C.Yellow, fg = C.OnYellow, height = 48.dp, weight = 700,
                    )
                } else {
                    val ok = sh.selected.isNotEmpty() && !noSnapshot && !sh.submitting && !busy
                    val label = if (sh.submitting || busy) stringResource(R.string.pi_working) else submitText(sh.direction, sh.view.split).asString()
                    Pill(
                        label, { if (ok) vm.submit() },
                        Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.4f).testTag("places_submit"),
                        bg = C.Yellow, fg = C.OnYellow, height = 48.dp, weight = 700,
                    )
                }
            }
        }
    }
}

/** Asks VDMS for its list again; its description says how old the list is. */
@Composable
private fun RefreshButton(s: PlacesUiState, vm: PlacesImportViewModel) {
    val ago = agoText(s.places?.snapshotAt)
    val label = when {
        s.refreshing -> stringResource(R.string.pi_refreshing_list)
        ago.isNotEmpty() -> stringResource(R.string.pi_list_from, ago)
        else -> stringResource(R.string.pi_ask_list)
    }
    Box(
        Modifier.size(48.dp).clip(r(10)).clickable(enabled = !s.refreshing, role = Role.Button, onClick = vm::refresh)
            .semantics { contentDescription = label }.testTag("places_refresh"),
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Refresh, 18.dp, if (s.refreshing) C.Muted else C.Yellow, 2.2f) }
}

/** The toolbar and the tree, scrolling as one (so a short screen or large text still reaches the tree). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.Picker(sh: PlacesSheetState, s: PlacesUiState, noSnapshot: Boolean, vm: PlacesImportViewModel) {
    val toVdms = sh.direction == PlacesDirection.TO_VDMS
    val v = sh.view
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("places_tree"), contentPadding = PaddingValues(bottom = 8.dp)) {
        when {
            noSnapshot -> item {
                EmptyBlock(stringResource(if (s.refreshing) R.string.pi_getting_list else R.string.pi_no_snapshot)) {
                    LinkText(stringResource(if (s.refreshing) R.string.pi_asking_vdms else R.string.pi_ask_vdms), enabled = !s.refreshing, onClick = vm::refresh)
                }
            }
            v.tally.total == 0 -> item { EmptyBlock(stringResource(if (toVdms) R.string.pi_to_empty else R.string.pi_from_empty)) }
            v.tally.fresh == 0 && v.tally.changed == 0 && sh.hideExisting -> item {
                EmptyBlock(stringResource(if (toVdms) R.string.pi_to_nothing else R.string.pi_from_nothing)) {
                    LinkText(stringResource(R.string.pi_show_them)) { vm.setHideExisting(false) }
                }
            }
            else -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SearchField(sh.search, vm::setSearch, stringResource(R.string.pi_search), Modifier.weight(1f))
                        RefreshButton(s, vm)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(r(8)).clickable(role = Role.Checkbox) { vm.setHideExisting(!sh.hideExisting) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CheckBox(sh.hideExisting, size = 20.dp, checkSize = 12.dp)
                        Txt(stringResource(if (toVdms) R.string.pi_to_hide else R.string.pi_from_hide), 13, 500, C.Text, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                    }
                }
                item {
                    val summary = if (v.tally.changed > 0) {
                        stringResource(R.string.pi_selection_changed, sh.selected.size, v.tally.fresh, v.tally.total, v.tally.changed)
                    } else {
                        stringResource(R.string.pi_selection, sh.selected.size, v.tally.fresh, v.tally.total)
                    }
                    Txt(summary, 12, 500, C.Muted, Modifier.testTag("places_selection"), maxLines = Int.MAX_VALUE)
                }
                item {
                    FlowRow(verticalArrangement = Arrangement.Center) {
                        LinkText(
                            stringResource(if (sh.searching) R.string.pi_select_all_shown else R.string.pi_select_all),
                            enabled = v.addable.isNotEmpty(), onClick = vm::selectAll,
                        )
                        if (v.changes.isNotEmpty()) LinkText(stringResource(R.string.pi_select_changes), onClick = vm::selectChanges)
                        LinkText(stringResource(R.string.pi_clear), enabled = sh.selected.isNotEmpty(), onClick = vm::clear)
                    }
                }
                sh.parentNote?.let { note ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(r(10)).background(C.WarnRow).padding(10.dp).testTag("places_parent_note"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LineIcon(Icons.Info, 15.dp, C.Orange, 2.2f, Modifier.padding(top = 1.dp))
                            Txt(note.asString(), 12, 500, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                        }
                    }
                }
                if (v.noneShown) item { EmptyBlock(stringResource(R.string.pi_no_match, sh.search.trim())) }
                else items(v.rows) { row -> PlaceRowItem(row, sh.direction, vm) }
            }
        }
    }
}

@Composable
private fun EmptyBlock(text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(text, 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
        action?.invoke()
    }
}

/** A building, floor or location: open/close (when it has rows under it), its tick box, name, badge and child count. */
@Composable
private fun PlaceRowItem(row: PlaceTreeRow, direction: PlacesDirection, vm: PlacesImportViewModel) {
    val n = row.node
    val rot by animateFloatAsState(if (row.open) 90f else 0f, tween(200), label = "places_tree")
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = (row.depth * 20).dp), verticalAlignment = Alignment.CenterVertically) {
        if (n.children.isNotEmpty()) {
            val toggleLabel = stringResource(if (row.open) R.string.pi_collapse else R.string.pi_expand, n.name)
            Box(
                Modifier.size(40.dp, 48.dp).clip(r(8)).clickable(role = Role.Button) { vm.toggleOpen(n) }.semantics { contentDescription = toggleLabel },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.6f, Modifier.rotate(rot)) }
        } else {
            Spacer(Modifier.width(40.dp))
        }
        val tickState = stringResource(
            when (row.tick) {
                Tick.ON -> R.string.pi_ticked
                Tick.PARTLY -> R.string.pi_partly_ticked
                Tick.OFF -> R.string.pi_not_ticked
            },
        )
        val tip = if (n.status == PlaceStatus.CHANGED) stringResource(R.string.pi_update_tip) else null
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(r(8))
                .clickable(enabled = row.enabled, role = Role.Checkbox) { vm.toggle(n) }
                .semantics { stateDescription = listOfNotNull(tickState, tip).joinToString(", ") }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.alpha(if (row.enabled) 1f else 0.35f)) {
                CheckBox(row.tick == Tick.ON, size = 20.dp, checkSize = 12.dp, partly = row.tick == Tick.PARTLY)
            }
            LineIcon(
                when (n.kind) {
                    PlaceKind.BUILDING -> Icons.Building
                    PlaceKind.FLOOR -> Icons.Floor
                    PlaceKind.LOCATION -> Icons.Pin
                },
                16.dp, C.Muted,
            )
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val same = n.status == PlaceStatus.SAME
                Txt(n.name, 14, if (n.kind == PlaceKind.BUILDING) 700 else 500, if (same) C.Muted else C.Text, maxLines = 2)
                badgeOf(n, direction)?.let { b -> Badge(b) }
            }
            if (n.children.isNotEmpty()) {
                val count = n.children.size
                Txt(
                    if (n.kind == PlaceKind.BUILDING) pluralStringResource(R.plurals.pi_count_floors, count, count)
                    else pluralStringResource(R.plurals.pi_count_locations, count, count),
                    11, 500, C.Muted,
                )
            }
        }
    }
}

@Composable
private fun Badge(b: PlaceBadge) {
    val color = when (b.tone) {
        PlaceStatus.CHANGED -> C.Yellow
        PlaceStatus.GONE -> C.Orange
        else -> C.Muted
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TintChip(b.label.asString(), bg = color.copy(alpha = 0.15f), fg = color)
        b.detail?.let { Txt(it.asString(), 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
    }
}

/** What was sent or imported, updated and skipped. */
@Composable
private fun ColumnScope.ResultBody(sh: PlacesSheetState) {
    val r = sh.result ?: return
    val toVdms = sh.direction == PlacesDirection.TO_VDMS
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp).testTag("places_result"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(resultHead(r, sh.direction).asString(), 15, 700, maxLines = Int.MAX_VALUE)
        r.updated.forEach { Txt(updatedText(it, sh.direction).asString(), 12, 400, C.Soft, maxLines = Int.MAX_VALUE) }
        if (r.skipped.isNotEmpty()) {
            val n = r.skipped.size
            Txt(
                pluralStringResource(if (toVdms) R.plurals.pi_skipped_sent else R.plurals.pi_skipped_imported, n, n),
                12, 500, C.Muted, Modifier.padding(top = 4.dp), maxLines = Int.MAX_VALUE,
            )
            r.skipped.forEach { SkippedLine(skippedText(it, sh.direction).asString()) }
        }
    }
}

/** VDMS is adding the places sent (or didn't finish within the wait). */
@Composable
private fun ColumnScope.WaitingBody(timedOut: Boolean) {
    Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (timedOut) Txt(stringResource(R.string.pi_timed_out), 13, 400, C.Soft, maxLines = Int.MAX_VALUE)
        else ListLoader(stringResource(R.string.pi_waiting))
    }
}
