package com.example.sclerago.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sclerago.AppViewModel
import com.example.sclerago.CfgPage
import com.example.sclerago.data.*
import com.example.sclerago.ui.icons.Ico
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C
import com.example.sclerago.ui.theme.Txt

/**
 * The Configuration page (side menu → Configuration): Building info (buildings, floors, locations),
 * Asset fields (which fields show / are required, photo counts) and Geofencing (map).
 * System back goes from a sub-page to the Configuration home, and from there back to the app.
 */
@Composable
fun ConfigScreen(vm: AppViewModel) {
    val s = vm.state
    BackHandler(onBack = vm::cfgBack)
    val title = when (s.cfgPage) {
        CfgPage.HOME -> "Configuration"
        CfgPage.BUILDING -> "Building info"
        CfgPage.FIELDS -> "Asset fields"
        CfgPage.GEO -> "Geofencing"
    }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            PageBar(title, if (s.cfgPage == CfgPage.HOME) vm.propName else "Configuration · ${vm.propName}", vm::cfgBack)
            Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                when (s.cfgPage) {
                    CfgPage.HOME -> ConfigHome(vm)
                    CfgPage.BUILDING -> BuildingInfo(vm)
                    CfgPage.FIELDS -> AssetFields(vm)
                    CfgPage.GEO -> Geofencing(vm)
                }
            }
        }
        AnimatedVisibility(
            s.toastOn,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { Toast(s.toast) }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun ConfigHome(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeCard(Icons.Building, C.Yellow, C.YTintBg, "Building info", "Property details, floors and site metadata") { vm.cfgGo(CfgPage.BUILDING) }
        HomeCard(Icons.Box, C.Purple, Color(0xFF26233A), "Asset fields", "Fields and photos collected for each asset") { vm.cfgGo(CfgPage.FIELDS) }
        HomeCard(Icons.Pin, C.Green, Color(0xFF17291F), "Geofencing", "Set the boundaries where collection is allowed") { vm.cfgGo(CfgPage.GEO) }
    }
}

@Composable
private fun HomeCard(icon: Ico, fg: Color, bg: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(r(11)).background(bg), contentAlignment = Alignment.Center) { LineIcon(icon, 22.dp, fg, 2.1f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 16, 700)
            Txt(sub, 13, 400, C.Muted, maxLines = 2)
        }
        LineIcon(Icons.ChevronRight, 18.dp, C.Muted, 2.4f)
    }
}

// ---------------------------------------------------------------- Building info

private class NameAsk(val title: String, val initial: String, val placeholder: String, val onSave: (String) -> Unit)
private class DeleteAsk(val title: String, val body: String, val onYes: () -> Unit)

@Composable
private fun BuildingInfo(vm: AppViewModel) {
    val tree = vm.treeFor()
    var bId by remember { mutableStateOf<String?>(null) }
    var fId by remember { mutableStateOf<String?>(null) }
    val building = tree.firstOrNull { it.id == bId } ?: tree.firstOrNull()
    val floors = building?.kids.orEmpty()
    val floor = floors.firstOrNull { it.id == fId } ?: floors.firstOrNull()
    val locs = floor?.kids.orEmpty()
    var q by remember { mutableStateOf("") }
    var sel by remember(floor?.id) { mutableStateOf(setOf<String>()) }
    var nameAsk by remember { mutableStateOf<NameAsk?>(null) }
    var deleteAsk by remember { mutableStateOf<DeleteAsk?>(null) }
    var openLoc by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Building and floor pickers, each with a ⋮ menu.
            Column(
                Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NodePicker(
                    Icons.Building, "BUILDING", building, tree, empty = "No buildings yet",
                    onPick = { bId = it; fId = null },
                    menu = buildList {
                        add("Add building" to {
                            nameAsk = NameAsk("Add building", "", "Building name, e.g. North Wing") { n -> bId = vm.addBuilding(n); fId = null }
                        })
                        if (building != null) {
                            add("Rename building" to { nameAsk = NameAsk("Rename building", building.name, "Building name") { vm.renameNode(building.id, it) } })
                            add("Delete building" to {
                                deleteAsk = DeleteAsk("Delete ${building.name}?", "Its floors and locations are removed too.") {
                                    vm.deleteNodes(setOf(building.id), "Building"); bId = null; fId = null
                                }
                            })
                        }
                    },
                )
                NodePicker(
                    Icons.Floor, "FLOOR", floor, floors, empty = if (building == null) "Add a building first" else "No floors yet",
                    enabled = building != null,
                    onPick = { fId = it },
                    menu = buildList {
                        if (building != null) add("Add floor" to {
                            nameAsk = NameAsk("Add floor", "", "Floor name, e.g. Level 2") { n -> fId = vm.addFloor(building.id, n) }
                        })
                        if (floor != null) {
                            add("Rename floor" to { nameAsk = NameAsk("Rename floor", floor.name, "Floor name") { vm.renameNode(floor.id, it) } })
                            add("Delete floor" to {
                                deleteAsk = DeleteAsk("Delete ${floor.name}?", "Its locations are removed too.") {
                                    vm.deleteNodes(setOf(floor.id), "Floor"); fId = null
                                }
                            })
                        }
                    },
                )
            }

            // Locations on the chosen floor.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Locations", 16, 700)
                    Txt(if (floor != null && building != null) "On ${floor.name}, ${building.name}" else "Choose a floor", 12, 400, C.Muted)
                }
                if (floor != null) Pill("Add location", { adding = true }, bg = C.Yellow, fg = C.OnYellow, height = 36.dp, hPad = 14.dp, weight = 700)
            }
            if (floor != null && locs.isNotEmpty()) SearchField(q, { q = it }, "Search locations")
            val shown = locs.filter { q.isBlank() || "${it.name} ${it.type} ${it.code}".contains(q.trim(), ignoreCase = true) }
            when {
                floor == null -> EmptyNote(if (building == null) "Add a building, then its floors and locations." else "Add a floor to this building to start adding locations.")
                locs.isEmpty() -> EmptyNote("No locations on this floor yet. Tap Add location.")
                shown.isEmpty() -> EmptyNote("No location matches “${q.trim()}”.")
                else -> shown.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { l ->
                            LocationCard(l, l.id in sel, Modifier.weight(1f),
                                onOpen = { openLoc = l.id },
                                onTick = { sel = if (l.id in sel) sel - l.id else sel + l.id })
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (sel.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(plural(sel.size, "location", "locations") + " selected", 14, 700, modifier = Modifier.weight(1f))
                Pill("Clear", { sel = emptySet() }, height = 40.dp, hPad = 14.dp)
                Pill("Delete", {
                    val ids = sel
                    deleteAsk = DeleteAsk("Delete ${plural(ids.size, "location", "locations")}?", "This can’t be undone.") {
                        vm.deleteNodes(ids, if (ids.size == 1) "Location" else "Locations"); sel = emptySet()
                    }
                }, bg = Color(0xFF2A1919), fg = C.Red, height = 40.dp, hPad = 16.dp, weight = 700)
            }
        }
    }

    if (adding && floor != null) AddLocationSheet(onClose = { adding = false }) { names, type, code ->
        vm.addLocations(floor.id, names, type, code); adding = false
    }
    openLoc?.let { id -> tree.findNode(id) }?.let { l ->
        LocationSheet(l, onClose = { openLoc = null },
            onSave = { n, t, c -> vm.updateLocation(l.id, n, t, c) },
            onDelete = {
                deleteAsk = DeleteAsk("Delete ${l.name}?", "This can’t be undone.") { vm.deleteNodes(setOf(l.id), "Location"); openLoc = null }
            })
    }
    nameAsk?.let { a -> NameDialog(a) { nameAsk = null } }
    deleteAsk?.let { d ->
        ConfirmDialog(
            icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red, title = d.title, body = d.body,
            no = "Cancel", yes = "Delete", onNo = { deleteAsk = null }, onYes = { d.onYes(); deleteAsk = null },
        )
    }
}

@Composable
private fun NodePicker(
    icon: Ico,
    label: String,
    current: LocNode?,
    options: List<LocNode>,
    empty: String,
    onPick: (String) -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    enabled: Boolean = true,
) {
    var pickOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
            LineIcon(icon, 19.dp, C.Yellow, 2.1f)
        }
        Column(Modifier.weight(1f)) {
            Txt(label, 10, 700, C.Muted, spacing = 0.6f)
            Box {
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(r(9)).background(C.Bg).border(1.dp, C.Border2, r(9))
                        .clickable(enabled = enabled && options.isNotEmpty()) { pickOpen = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(current?.name ?: empty, 14, if (current != null) 600 else 400, if (current != null) C.Text else C.Muted, Modifier.weight(1f))
                    LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
                }
                DropdownMenu(pickOpen, { pickOpen = false }, containerColor = C.Surface) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = { Txt(o.name, 15, if (o.id == current?.id) 700 else 500, if (o.id == current?.id) C.Yellow else C.Text) },
                            onClick = { onPick(o.id); pickOpen = false },
                        )
                    }
                }
            }
        }
        Box {
            RoundButton(40.dp, C.Btn, { if (enabled) menuOpen = true }, Modifier.padding(top = 14.dp).alpha(if (enabled) 1f else 0.4f), shape = r(9)) {
                LineIcon(Icons.Dots, 18.dp, C.Text)
            }
            DropdownMenu(menuOpen, { menuOpen = false }, containerColor = C.Surface) {
                menu.forEach { (text, go) ->
                    DropdownMenuItem(
                        text = { Txt(text, 15, 500, if (text.startsWith("Delete")) C.Red else C.Text) },
                        onClick = { menuOpen = false; go() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationCard(l: LocNode, picked: Boolean, modifier: Modifier, onOpen: () -> Unit, onTick: () -> Unit) {
    Box(
        modifier.clip(r(14)).background(if (picked) C.PickedRow else C.Surface)
            .border(1.dp, if (picked) C.YTintBd else C.Border, r(14)).clickable(onClick = onOpen),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF3A2A1A)), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Pin, 24.dp, C.Orange, 2.1f)
            }
            Txt(l.name, 14, 700, maxLines = 2)
            Txt(listOf(l.type, l.code).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "No type yet" }, 11, 400, C.Muted)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(r(6)).clickable(onClick = onTick).padding(4.dp)) { CheckBox(picked) }
    }
}

@Composable
private fun LabeledInput(label: String, value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true, minLines: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, 12, 600, C.Soft)
        PlainField(
            value, onChange, placeholder,
            Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.5.dp, if (value.isNotBlank()) C.YTintBd else C.Border, r(10)).padding(12.dp),
            size = 15, singleLine = singleLine, minLines = minLines,
        )
    }
}

@Composable
private fun AddLocationSheet(onClose: () -> Unit, onAdd: (List<String>, String, String) -> Unit) {
    var bulk by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val names = if (bulk) name.split('\n').map { it.trim() }.filter { it.isNotEmpty() } else listOf(name.trim()).filter { it.isNotEmpty() }
    BottomSheet(onClose) {
        SheetHeader("Add location", if (bulk) "One location per line. They all get the same type." else null, onClose)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.clip(r(8)).clickable { bulk = !bulk }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CheckBox(bulk)
                Txt("Bulk addition", 14, 600)
            }
            LabeledInput(
                if (bulk) "Names *" else "Name *", name, { name = it },
                if (bulk) "Lobby\nMeeting room 1\nMeeting room 2" else "Enter location name (e.g. Lobby)",
                singleLine = !bulk, minLines = if (bulk) 4 else 1,
            )
            LabeledInput("Type", type, { type = it }, "e.g. Plant room, Office")
            LabeledInput("Code", code, { code = it }, if (bulk) "Prefix, e.g. B2 (becomes B2-1, B2-2…)" else "e.g. B2-PR-01")
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Cancel", onClose, height = 44.dp, hPad = 16.dp)
            Pill(
                if (names.size > 1) "Add ${names.size} locations" else "Add location",
                { if (names.isNotEmpty()) onAdd(names, type, code) },
                Modifier.weight(1f).alpha(if (names.isNotEmpty()) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
            )
        }
    }
}

@Composable
private fun LocationSheet(l: LocNode, onClose: () -> Unit, onSave: (String, String, String) -> Unit, onDelete: () -> Unit) {
    var editing by remember(l.id) { mutableStateOf(false) }
    var name by remember(l.id) { mutableStateOf(l.name) }
    var type by remember(l.id) { mutableStateOf(l.type) }
    var code by remember(l.id) { mutableStateOf(l.code) }
    BottomSheet(onClose, maxHeightFraction = 0.8f) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(l.name, 18, 700, modifier = Modifier.weight(1f))
            if (!editing) RoundButton(36.dp, C.Btn, { editing = true }) { LineIcon(Icons.Pencil, 16.dp, C.Yellow, 2.2f) }
            Spacer(Modifier.width(8.dp))
            CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editing) {
                LabeledInput("Name *", name, { name = it }, "Location name")
                LabeledInput("Type", type, { type = it }, "e.g. Plant room")
                LabeledInput("Code", code, { code = it }, "e.g. B2-PR-01")
            } else {
                listOf("Name" to l.name, "Type" to l.type, "Code" to l.code).forEach { (k, v) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(k, 12, 600, C.Muted)
                        Txt(v.ifBlank { "–" }, 15, 600)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Pill("Cancel", { editing = false; name = l.name; type = l.type; code = l.code }, height = 44.dp, hPad = 16.dp)
                Pill(
                    "Save", { if (name.isNotBlank()) { onSave(name, type, code); editing = false } },
                    Modifier.weight(1f).alpha(if (name.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
                )
            } else {
                Txt("Delete location", 14, 700, C.Red, Modifier.clip(r(8)).clickable(onClick = onDelete).padding(8.dp))
                Spacer(Modifier.weight(1f))
                Pill("Done", onClose, height = 44.dp, hPad = 20.dp)
            }
        }
    }
}

@Composable
private fun NameDialog(a: NameAsk, onClose: () -> Unit) {
    var v by remember(a) { mutableStateOf(a.initial) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onClose).imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(r(18)).background(C.Surface).border(1.dp, C.Border, r(18)).noRippleClick { }.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Txt(a.title, 18, 700)
            PlainField(
                v, { v = it }, a.placeholder,
                Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.5.dp, if (v.isNotBlank()) C.Yellow else C.Border, r(10)).padding(12.dp),
                size = 15,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancel", onClose, Modifier.weight(1f), height = 44.dp)
                Pill(
                    "Save", { if (v.isNotBlank()) { a.onSave(v.trim()); onClose() } },
                    Modifier.weight(1f).alpha(if (v.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Asset fields

@Composable
private fun AssetFields(vm: AppViewModel) {
    val s = vm.state
    var adding by remember { mutableStateOf(false) }
    var addingPhoto by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Choose which fields field workers see, and which they must fill.", 13, 400, C.Muted, Modifier.weight(1f), maxLines = 2)
            Spacer(Modifier.width(8.dp))
            Pill("Add field", { adding = true }, bg = C.Yellow, fg = C.OnYellow, height = 36.dp, hPad = 14.dp, weight = 700)
        }
        // Column headings
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().background(C.Strip).topBorder(C.Border).bottomBorder(C.Border)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("FIELD", 10, 700, C.Muted, Modifier.weight(1f), spacing = 0.6f)
            Txt("VISIBLE", 10, 700, C.Muted, Modifier.width(58.dp), spacing = 0.6f)
            Txt("REQUIRED", 10, 700, C.Muted, Modifier.width(64.dp), spacing = 0.6f)
        }
        FieldSection.entries.forEach { sec ->
            val list = s.fieldCfg.filter { it.section == sec }
            if (list.isNotEmpty()) {
                Txt(sec.title.uppercase(), 11, 700, C.Yellow, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), spacing = 0.6f)
                list.forEach { f -> FieldCfgRow(f, vm) }
            }
        }

        // Photos
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Photos", 16, 700)
                Txt("How many of each kind to collect per asset · ${vm.minPhotos} needed in total", 12, 400, C.Muted, maxLines = 2)
            }
            Pill("Add type", { addingPhoto = true }, height = 34.dp, hPad = 12.dp, size = 12)
        }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().background(C.Strip).topBorder(C.Border).bottomBorder(C.Border)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("PHOTO", 10, 700, C.Muted, Modifier.weight(1f), spacing = 0.6f)
            Txt("MINIMUM", 10, 700, C.Muted, Modifier.width(100.dp), spacing = 0.6f)
            Txt("MAXIMUM", 10, 700, C.Muted, Modifier.width(100.dp), spacing = 0.6f)
        }
        s.photoReqs.forEach { p ->
            Row(
                Modifier.fillMaxWidth().bottomBorder(C.Border).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt(p.name, 14, 600, modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.clip(CircleShape).clickable { vm.removePhotoReq(p.id) }.padding(2.dp)) { LineIcon(Icons.Close, 12.dp, C.Dim, 2.4f) }
                    }
                    Txt(p.hint, 11, 400, C.Muted, maxLines = 2)
                }
                Stepper(p.min, Modifier.width(100.dp)) { vm.setPhotoLimits(p.id, it, maxOf(p.max, it)) }
                Stepper(p.max, Modifier.width(100.dp)) { vm.setPhotoLimits(p.id, minOf(p.min, it), it) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (adding) AddFieldSheet(onClose = { adding = false }) { label, kind, req -> vm.addField(label, kind, req); adding = false }
    if (addingPhoto) {
        var name by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        BottomSheet({ addingPhoto = false }) {
            SheetHeader("Add photo type", "E.g. Data plate, Valve position, Meter reading.", { addingPhoto = false })
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledInput("Name *", name, { name = it }, "e.g. Meter reading")
                LabeledInput("Hint for field workers", hint, { hint = it }, "What the photo should show")
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp).topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancel", { addingPhoto = false }, height = 44.dp, hPad = 16.dp)
                Pill(
                    "Add", { if (name.isNotBlank()) { vm.addPhotoReq(name, hint); addingPhoto = false } },
                    Modifier.weight(1f).alpha(if (name.isNotBlank()) 1f else 0.4f), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

@Composable
private fun KindBadge(kind: FieldKind) {
    Box(Modifier.clip(r(6)).background(C.Raised).padding(horizontal = 7.dp, vertical = 2.dp)) { Txt(kind.label, 11, 600, C.Soft) }
}

@Composable
private fun FieldCfgRow(f: FieldCfg, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().bottomBorder(C.Border).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (f.visible) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(f.label, 14, 600, modifier = Modifier.weight(1f, fill = false))
                if (f.custom) Box(Modifier.clip(CircleShape).clickable { vm.removeField(f.key) }.padding(2.dp)) { LineIcon(Icons.Close, 12.dp, C.Dim, 2.4f) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KindBadge(f.kind)
                if (f.locked) Txt("Always on", 11, 500, C.Muted)
            }
        }
        Box(Modifier.width(58.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.clip(r(6)).clickable(enabled = !f.locked) { vm.toggleFieldVisible(f.key) }.padding(6.dp).alpha(if (f.locked) 0.5f else 1f)) { CheckBox(f.visible) }
        }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.clip(r(6)).clickable(enabled = !f.locked) { vm.toggleFieldRequired(f.key) }.padding(6.dp).alpha(if (f.locked) 0.5f else 1f)) { CheckBox(f.required) }
        }
    }
}

@Composable
private fun Stepper(value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(
        modifier.height(36.dp).clip(r(9)).background(C.Bg).border(1.dp, C.Border2, r(9)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = value > 0) { onChange(value - 1) }, contentAlignment = Alignment.Center) {
            LineIcon(Icons.Minus, 14.dp, if (value > 0) C.Text else C.Dim, 2.6f)
        }
        Txt(value.toString(), 15, 700, modifier = Modifier.width(26.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = value < 9) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            LineIcon(Icons.PlusSmall, 14.dp, if (value < 9) C.Text else C.Dim, 2.6f)
        }
    }
}

@Composable
private fun AddFieldSheet(onClose: () -> Unit, onAdd: (String, FieldKind, Boolean) -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 = choose, 1 = new field
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(FieldKind.TEXT) }
    var required by remember { mutableStateOf(false) }
    BottomSheet(onClose) {
        SheetHeader("Add field", if (mode == 0) "Add one field, or bring in many from a spreadsheet." else "It shows under More details on every asset.", onClose)
        if (mode == 0) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AddChoice(Icons.FileDoc, C.Text, "New field", "Name it and pick its type", Modifier.weight(1f)) { mode = 1 }
                AddChoice(Icons.FileUpload, C.Green, "Import from Excel", "Available on the web app", Modifier.weight(1f).alpha(0.55f)) { }
            }
        } else {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledInput("Field name *", label, { label = it }, "e.g. Refrigerant")
                Txt("Type", 12, 600, C.Soft)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldKind.entries.forEach { k ->
                        val on = k == kind
                        Box(
                            Modifier.height(34.dp).clip(Capsule).background(if (on) C.YTintBg else C.Bg)
                                .border(1.dp, if (on) C.Yellow else C.Border2, Capsule).clickable { kind = k }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Txt(k.label, 13, 600, if (on) C.Yellow else C.Soft) }
                    }
                }
                Row(
                    Modifier.clip(r(8)).clickable { required = !required }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CheckBox(required)
                    Txt("Required", 14, 600)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp).topBorder(C.Border).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Back", { mode = 0 }, height = 44.dp, hPad = 16.dp)
                Pill(
                    "Add field", { if (label.isNotBlank()) onAdd(label, kind, required) },
                    Modifier.weight(1f).alpha(if (label.isNotBlank()) 1f else 0.4f), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

@Composable
private fun AddChoice(icon: Ico, tint: Color, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(r(14)).background(C.Bg).border(1.dp, C.Border2, r(14)).clickable(onClick = onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).clip(r(12)).background(C.Raised), contentAlignment = Alignment.Center) { LineIcon(icon, 24.dp, tint, 2f) }
        Txt(title, 14, 700, maxLines = 2)
        Txt(sub, 11, 400, C.Muted, maxLines = 2)
    }
}

// ---------------------------------------------------------------- Geofencing

/** OpenStreetMap via Leaflet in a WebView, with address search (Nominatim). Drawing the fence comes later. */
private const val MAP_HTML = """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>
html,body{margin:0;height:100%;background:#aad3df}
#m{position:absolute;top:0;left:0;right:0;bottom:0}
#s{position:absolute;top:10px;left:54px;right:10px;z-index:1000}
#q{box-sizing:border-box;width:100%;height:40px;border-radius:20px;border:0;padding:0 16px;font:15px sans-serif;
   box-shadow:0 2px 8px rgba(0,0,0,.3);outline:none}
#e{position:absolute;inset:0;display:none;align-items:center;justify-content:center;font:15px sans-serif;color:#333;text-align:center;padding:24px}
</style></head><body>
<div id="m"></div>
<form id="s"><input id="q" type="search" placeholder="Search address" enterkeyhint="search" autocomplete="off"></form>
<div id="e">The map needs an internet connection.</div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
if (!window.L) { document.getElementById('e').style.display = 'flex'; }
else {
  var map = L.map('m', {worldCopyJump: true}).setView([20, 0], 2);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'}).addTo(map);
  // The view may get its final size after load; let Leaflet re-measure so tiles fill it.
  setTimeout(function () { map.invalidateSize(); }, 300);
  window.addEventListener('resize', function () { map.invalidateSize(); });
  var mk;
  document.getElementById('s').onsubmit = function (e) {
    e.preventDefault();
    var q = document.getElementById('q').value.trim();
    if (!q) return;
    document.getElementById('q').blur();
    fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(q))
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (!d.length) { alert('No place found for "' + q + '"'); return; }
        var p = [+d[0].lat, +d[0].lon];
        map.setView(p, 16);
        if (mk) mk.remove();
        mk = L.marker(p).addTo(map).bindPopup(d[0].display_name).openPopup();
      })
      .catch(function () { alert('Search needs an internet connection.'); });
  };
}
</script></body></html>"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Geofencing(vm: AppViewModel) {
    val s = vm.state
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
                .clickable { vm.setGeoEnforce(!s.geoEnforce) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CheckBox(s.geoEnforce)
            Column(Modifier.weight(1f)) {
                Txt("Enforce location restriction for collecting asset data", 14, 600, maxLines = 2)
                Txt(if (s.geoEnforce) "Assets can only be collected inside the geofence." else "Assets can be collected anywhere.", 12, 400, C.Muted)
            }
        }
        Txt("Draw geofence", 16, 700, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Without MATCH_PARENT the WebView sizes its viewport to the (empty) page: a 0-high map.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadDataWithBaseURL("https://sclerago.app/", MAP_HTML, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth().weight(1f).clip(r(14))
                .border(1.dp, C.Border, r(14)),
        )
    }
}
