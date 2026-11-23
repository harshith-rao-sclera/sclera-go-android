package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.MAX_PLACE_CODE
import com.sclera.sclerago.domain.model.MAX_PLACE_NAME
import com.sclera.sclerago.domain.model.MAX_PLACE_TYPE
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** The red of delete buttons' wash, as the location and delete confirmations use. */
private val DangerWash = Color(0xFF2A1919)

/** A building's, floor's or location's name, with "· Hidden" when field workers can't pick it. */
@Composable
internal fun placeName(p: LocNode): String = if (p.active) p.name else stringResource(R.string.cfgt_name_hidden, p.name)

/** "3 floors · 5 locations" for a building. */
@Composable
internal fun buildingSummary(b: LocNode): String {
    val floors = b.kids.orEmpty()
    val locations = floors.sumOf { it.kids.orEmpty().size }
    return stringResource(
        R.string.cfgt_building_summary,
        pluralStringResource(R.plurals.pi_count_floors, floors.size, floors.size),
        pluralStringResource(R.plurals.pi_count_locations, locations, locations),
    )
}

/** The square tile with a place's icon: yellow on a yellow wash, or grey when [muted]. */
@Composable
private fun PlaceTile(icon: Ico, muted: Boolean = false, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(r(10)).background(if (muted) C.Raised else C.YTintBg),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, (size / 2).dp, if (muted) C.Muted else C.Yellow, 2f) }
}

/** [items] as one list in a single card, a line between rows; [row] gets the modifier that draws its line. */
@Composable
private fun <T> PlaceList(items: List<T>, row: @Composable (T, Modifier) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12))) {
        items.forEachIndexed { i, item -> row(item, if (i == 0) Modifier else Modifier.topBorder(C.Border)) }
    }
}

/** Places whose name contains [query] (all of them for a blank one). */
private fun List<LocNode>.matching(query: String): List<LocNode> =
    query.trim().let { q -> if (q.isEmpty()) this else filter { it.name.contains(q, ignoreCase = true) } }

/** One row of an edit list: the place, its title (with "· Hidden") and the line under it. */
internal class EditRow(val place: LocNode, val title: String, val sub: String)

/**
 * Select building: the property's buildings with how many floors and locations each has, searchable; the chosen one
 * is marked. Import and Add building at the bottom need the server, so offline they are greyed out ([onBlocked] says
 * why when tapped).
 */
@Composable
internal fun SelectBuildingSheet(
    tree: List<LocNode>,
    selectedId: String?,
    offline: Boolean,
    onBlocked: () -> Unit,
    onPick: (LocNode) -> Unit,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    BottomSheet(onClose, maxHeightFraction = 0.85f) {
        SheetHeader(stringResource(R.string.cfgt_select_building), null, onClose)
        SearchField(query, { query = it }, stringResource(R.string.cfgt_search_buildings), Modifier.padding(horizontal = 12.dp))
        val shown = tree.matching(query)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (shown.isEmpty()) EmptyNote(stringResource(if (tree.isEmpty()) R.string.cfgt_buildings_empty else R.string.cfgt_no_match))
            PlaceList(shown) { b, divider ->
                val on = b.id == selectedId
                Row(
                    divider.pressClickable(pressed = PRESSED_CARD, role = Role.RadioButton) { onPick(b) }.fillMaxWidth().background(if (on) C.YTintBg else Color.Transparent)
                        
                        .alpha(if (b.active) 1f else 0.6f)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlaceTile(Icons.Building, muted = !on)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Txt(placeName(b), 14, 700, if (on) C.Yellow else C.Text, maxLines = 2)
                        Txt(buildingSummary(b), 11, 400, C.Muted)
                    }
                    Box(Modifier.size(18.dp).border(1.5.dp, if (on) C.Yellow else C.RadioOff, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(9.dp).background(C.Yellow, CircleShape))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OfflineGate(offline, onBlocked, Modifier.weight(1f)) {
                Pill(stringResource(R.string.import_action), onImport, Modifier.fillMaxWidth(), height = 44.dp, size = 13, weight = 700)
            }
            OfflineGate(offline, onBlocked, Modifier.weight(1f)) {
                Pill(stringResource(R.string.cfgt_add_building), onAdd, Modifier.fillMaxWidth(), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 13, weight = 700)
            }
        }
    }
}

/** Edit buildings: every building of [propName] with its floor count, to open to edit or delete. */
@Composable
internal fun EditBuildingsSheet(
    tree: List<LocNode>,
    propName: String,
    offline: Boolean,
    onBlocked: () -> Unit,
    onEdit: (LocNode) -> Unit,
    onDelete: (LocNode) -> Unit,
    onClose: () -> Unit,
) {
    EditPlacesSheet(
        stringResource(R.string.cfgt_edit_buildings), stringResource(R.string.cfgt_in_place, propName),
        tree.map { b -> EditRow(b, placeName(b), b.kids.orEmpty().size.let { n -> pluralStringResource(R.plurals.pi_count_floors, n, n) }) },
        Icons.Building, stringResource(R.string.cfgt_search_buildings), stringResource(R.string.cfgt_buildings_empty),
        offline, onBlocked, onEdit, onDelete, onClose,
    )
}

/**
 * An edit list (Edit buildings, Edit floors, Edit locations): [rows] under a search box, each with its [icon], a pencil
 * that opens it to edit and a bin that deletes it. Both change the server, so offline they are greyed out ([onBlocked]
 * says why when tapped). [empty] shows when there is nothing to list.
 */
@Composable
internal fun EditPlacesSheet(
    title: String,
    sub: String,
    rows: List<EditRow>,
    icon: Ico,
    searchHint: String,
    empty: String,
    offline: Boolean,
    onBlocked: () -> Unit,
    onEdit: (LocNode) -> Unit,
    onDelete: (LocNode) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    BottomSheet(onClose, maxHeightFraction = 0.9f) {
        SheetHeader(title, sub, onClose)
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(stringResource(R.string.action_search), 11, 600, C.Muted)
            SearchField(query, { query = it }, searchHint)
        }
        val matched = rows.map { it.place }.matching(query).map { it.id }.toSet()
        val shown = rows.filter { it.place.id in matched }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (shown.isEmpty()) EmptyNote(if (rows.isEmpty()) empty else stringResource(R.string.cfgt_no_match))
            PlaceList(shown) { row, divider ->
                val p = row.place
                Row(
                    divider.fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlaceTile(icon)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Txt(row.title, 14, 700, maxLines = 2)
                        Txt(row.sub, 11, 400, C.Muted, maxLines = 2)
                    }
                    val editLabel = stringResource(R.string.access_edit_group, p.name)
                    OfflineGate(offline, onBlocked) {
                        RoundButton(32.dp, Color.Transparent, { onEdit(p) }, Modifier.semantics { contentDescription = editLabel }) {
                            LineIcon(Icons.Pencil, 16.dp, C.Yellow, 2.2f)
                        }
                    }
                    val deleteLabel = stringResource(R.string.property_delete_label, p.name)
                    OfflineGate(offline, onBlocked) {
                        RoundButton(32.dp, Color.Transparent, { onDelete(p) }, Modifier.semantics { contentDescription = deleteLabel }) {
                            LineIcon(Icons.Trash, 16.dp, C.Red, 2.2f)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One location: its name, type and code under a header saying which floor it is on. The pencil turns the values into
 * fields (Cancel / Save), or they open as fields with [startEditing]; Delete location asks first. Both change the
 * server, so offline they are greyed out.
 */
@Composable
internal fun LocationSheet(
    l: LocNode,
    floorName: String?,
    offline: Boolean,
    onBlocked: () -> Unit,
    onClose: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
    startEditing: Boolean = false,
) {
    var editing by remember(l.id) { mutableStateOf(startEditing && !offline) }
    var name by remember(l.id) { mutableStateOf(l.name) }
    var type by remember(l.id) { mutableStateOf(l.type) }
    var code by remember(l.id) { mutableStateOf(l.code) }
    val changed = editing && (name != l.name || type != l.type || code != l.code)
    // The edit's own Cancel asks too when something was changed.
    var askCancel by remember(l.id) { mutableStateOf(false) }
    fun stopEditing() {
        editing = false; name = l.name; type = l.type; code = l.code
    }
    GuardedClose(changed, onClose) { onClose ->
        BottomSheet(onClose, maxHeightFraction = 0.8f) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Pin, 20.dp, C.Yellow, 2f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Txt(l.name, 15, 700, maxLines = 2)
                    floorName?.let { Txt(stringResource(R.string.cfgt_location_on, it), 12, 400, C.Muted) }
                }
                if (!editing) {
                    val editLabel = stringResource(R.string.access_edit_group, l.name)
                    OfflineGate(offline, onBlocked) {
                        RoundButton(36.dp, C.Btn, { editing = true }, Modifier.semantics { contentDescription = editLabel }) {
                            LineIcon(Icons.Pencil, 16.dp, C.Yellow, 2.2f)
                        }
                    }
                }
                CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editing) {
                    LabeledInput(stringResource(R.string.cfg_add_name), name, { name = it.take(MAX_PLACE_NAME) }, stringResource(R.string.cfgt_location_name))
                    LabeledInput(stringResource(R.string.cfg_add_type), type, { type = it.take(MAX_PLACE_TYPE) }, stringResource(R.string.cfgt_location_type_hint))
                    LabeledInput(stringResource(R.string.cfg_add_code), code, { code = it.take(MAX_PLACE_CODE) }, stringResource(R.string.cfg_add_code_hint))
                } else {
                    ValueLine(stringResource(R.string.field_name), l.name, required = true)
                    ValueLine(stringResource(R.string.cfg_add_type), l.type)
                    ValueLine(stringResource(R.string.cfg_add_code), l.code)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (editing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(stringResource(R.string.cfg_cancel), { if (changed) askCancel = true else stopEditing() }, height = 44.dp, hPad = 18.dp)
                    Pill(
                        stringResource(R.string.cfg_save), { if (name.isNotBlank()) { onSave(name, type, code); editing = false } },
                        Modifier.weight(1f).alpha(if (name.isNotBlank()) 1f else 0.4f),
                        bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 13, weight = 700,
                    )
                }
            } else {
                OfflineGate(offline, onBlocked, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).pressClickable(shape = Capsule, pressed = PRESSED_CARD, onClick = onDelete).background(DangerWash)
                            .border(1.dp, C.Red.copy(alpha = 0.55f), Capsule),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LineIcon(Icons.Trash, 16.dp, C.Red, 2.2f)
                        Spacer(Modifier.width(8.dp))
                        Txt(stringResource(R.string.cfgt_delete_location), 13, 700, C.Red)
                    }
                }
            }
        }
        if (askCancel) {
            DiscardChangesDialog(onKeep = { askCancel = false }, onDiscard = { askCancel = false; stopEditing() })
        }
    }
}

/** A read-only value with its label above it ([required] adds a red star); "–" when empty. */
@Composable
private fun ValueLine(label: String, value: String, required: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Txt(label, 11, 600, C.Muted)
            if (required) Txt(" *", 11, 700, C.Red)
        }
        Txt(value.ifBlank { "–" }, 14, 600, maxLines = Int.MAX_VALUE)
    }
}
