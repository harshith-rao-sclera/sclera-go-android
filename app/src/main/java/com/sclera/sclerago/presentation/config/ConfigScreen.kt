package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.presentation.components.pageGutter
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sclera.sclerago.domain.model.ASSET_TYPES
import com.sclera.sclerago.domain.model.BUILDING_TYPES
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.COUNTRIES
import com.sclera.sclerago.domain.model.DATE_DISPLAY
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.JACS_CODES
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.REGIONS
import com.sclera.sclerago.domain.model.SUB_TYPES
import com.sclera.sclerago.domain.model.SYSTEM_TYPES
import com.sclera.sclerago.domain.model.TIME_DISPLAY
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.model.noRuleLabel
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.BottomTabBar
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.NavItem
import com.sclera.sclerago.presentation.components.PageBar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.Segmented
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.CfgPage
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

private class CfgTabSpec(val page: CfgPage, val label: String, val icon: Ico)

private val CFG_TABS = listOf(
    CfgTabSpec(CfgPage.FIELDS, "Asset Fields", Icons.Box),
    CfgTabSpec(CfgPage.BUILDING, "Building Info", Icons.Building),
    CfgTabSpec(CfgPage.GEO, "Geofencing", Icons.Pin),
    CfgTabSpec(CfgPage.SETTINGS, "Settings", Icons.Toggles),
)

private val PageEasing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/** What Import buildings lets you pick: CSV or Excel files. */
private val IMPORT_SHEET_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
)

/**
 * The Configuration page (side menu → Configuration), as four bottom tabs that work like the main
 * screen's (tap one, or swipe the page; the highlight follows your finger): Building info (buildings, floors, locations), Asset fields (which fields show / are
 * required, formats, photo counts), Geofencing (map) and Property Settings (Bluetooth / Wi-Fi
 * onboarding). System back closes Configuration and returns to the app.
 */
@Composable
fun ConfigScreen(vm: AppViewModel) {
    val s = vm.state
    var addingField by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = CFG_TABS.indexOfFirst { it.page == s.cfgPage }.coerceAtLeast(0)) { CFG_TABS.size }
    // A tapped tab slides the pager there; a finished swipe selects the tab it rests on.
    LaunchedEffect(s.cfgPage) {
        val i = CFG_TABS.indexOfFirst { it.page == s.cfgPage }
        if (i >= 0 && pager.currentPage != i) pager.animateScrollToPage(i, animationSpec = tween(380, easing = PageEasing))
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) CFG_TABS[pager.currentPage].page.let { if (it != vm.state.cfgPage) vm.cfgGo(it) }
        }
    }
    BackHandler(onBack = vm::closeConfig)
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            PageBar("Configuration", vm.propName, vm::closeConfig, property = true)
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { it }) { i ->
                Box(Modifier.fillMaxSize()) {
                    when (CFG_TABS[i].page) {
                        CfgPage.BUILDING -> BuildingInfo(vm)
                        CfgPage.FIELDS -> AssetFields(vm, addingField) { addingField = it }
                        CfgPage.GEO -> Geofencing(vm)
                        CfgPage.SETTINGS -> PropertySettingsPage(vm)
                    }
                }
            }
            // The highlight follows the pager's live position, so it slides with your finger.
            val pos = pager.currentPage + pager.currentPageOffsetFraction
            BottomTabBar {
                CFG_TABS.forEachIndexed { i, t ->
                    val on = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                    NavItem(t.label, t.icon, 21.dp, on, first = i == 0, last = i == CFG_TABS.size - 1) { vm.cfgGo(t.page) }
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

// ---------------------------------------------------------------- Tabs

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
    // Add / edit building sheet: null = closed, "" = new building, else the building's id.
    var buildingSheet by remember { mutableStateOf<String?>(null) }
    var floorsSheet by remember { mutableStateOf(false) }
    // Import buildings: pick a spreadsheet (CSV or Excel) in the phone's file manager.
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBuildings) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(horizontal = 4.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Buildings, floors and the locations on each floor.", 12, 400, C.Muted, Modifier.weight(1f), maxLines = 2)
                Pill("Import buildings", { importer.launch(IMPORT_SHEET_TYPES) }, height = 34.dp, hPad = 12.dp, size = 12)
            }
            // Building and floor pickers, each with a ⋮ menu.
            Column(
                Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodePicker(
                    Icons.Building, "BUILDING", building, tree, empty = "No buildings yet",
                    onPick = { bId = it; fId = null },
                    menu = buildList {
                        add(MenuAct(Icons.PlusCircle, "Add building") { buildingSheet = "" })
                        if (building != null) add(MenuAct(Icons.Pencil, "Edit ${building.name}") { buildingSheet = building.id })
                        add(MenuAct(Icons.Import, "Import buildings") { importer.launch(IMPORT_SHEET_TYPES) })
                        if (building != null) add(MenuAct(Icons.Trash, "Delete ${building.name}", danger = true, divider = true) {
                            deleteAsk = DeleteAsk("Delete ${building.name}?", "Its floors and locations are removed too.") {
                                vm.deleteNodes(setOf(building.id), "Building"); bId = null; fId = null
                            }
                        })
                    },
                )
                NodePicker(
                    Icons.Floor, "FLOOR", floor, floors, empty = if (building == null) "Add a building first" else "No floors yet",
                    enabled = building != null,
                    onPick = { fId = it },
                    menu = buildList {
                        if (building != null) add(MenuAct(Icons.PlusCircle, "Add floor") {
                            nameAsk = NameAsk("Add floor", "", "Floor name, e.g. Level 2") { n -> fId = vm.addFloor(building.id, n) }
                        })
                        if (building != null && floors.isNotEmpty()) add(MenuAct(Icons.Pencil, "Edit floors") { floorsSheet = true })
                    },
                )
            }

            // Locations on the chosen floor.
            Row(Modifier.padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Locations", 14, 700)
                    Txt(if (floor != null && building != null) "On ${floor.name}, ${building.name}" else "Choose a floor", 12, 400, C.Muted)
                }
                if (floor != null) Pill("+  Location", { adding = true }, bg = C.Yellow, fg = C.OnYellow, height = 36.dp, hPad = 16.dp, weight = 700)
            }
            if (floor != null && locs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(q, { q = it }, "Search locations", Modifier.weight(1f))
                RoundButton(44.dp, C.Bg, { q = ""; sel = emptySet(); vm.flash("Locations up to date") }, Modifier.border(1.dp, C.Border, r(12)), shape = r(12)) {
                    LineIcon(Icons.Refresh, 18.dp, C.Yellow, 2.2f)
                }
            }
            val shown = locs.filter { q.isBlank() || "${it.name} ${it.type} ${it.code}".contains(q.trim(), ignoreCase = true) }
            when {
                floor == null -> EmptyNote(if (building == null) "Add a building, then its floors and locations." else "Add a floor to this building to start adding locations.")
                locs.isEmpty() -> EmptyNote("No locations on this floor yet. Tap + Location.")
                shown.isEmpty() -> EmptyNote("No location matches “${q.trim()}”.")
                else -> shown.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Txt(plural(sel.size, "location", "locations") + " selected", 13, 700, modifier = Modifier.weight(1f))
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
    buildingSheet?.let { id ->
        val b = if (id.isEmpty()) null else tree.firstOrNull { it.id == id }
        EditBuildingSheet(b, onClose = { buildingSheet = null }) { n, info ->
            if (b == null) { bId = vm.addBuilding(n, info); fId = null } else vm.saveBuilding(b.id, n, info)
            buildingSheet = null
        }
    }
    if (floorsSheet && building != null) EditFloorsSheet(building, onClose = { floorsSheet = false }) { list ->
        vm.saveFloors(building.id, list); floorsSheet = false
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
    menu: List<MenuAct>,
    enabled: Boolean = true,
) {
    var pickOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            RoundButton(40.dp, C.Btn, { if (enabled) menuOpen = true }, Modifier.padding(top = 10.dp).alpha(if (enabled) 1f else 0.4f), shape = r(9)) {
                LineIcon(Icons.Dots, 18.dp, C.Text)
            }
            IconMenu(menuOpen, { menuOpen = false }, menu)
        }
    }
}

/** One item in a ⋮ menu: icon, text, and what it does. [danger] items are red; [divider] draws a line above. */
internal class MenuAct(
    val icon: Ico,
    val label: String,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val divider: Boolean = false,
    val go: () -> Unit,
)

/** A rounded pop-up menu with an icon beside each item. */
@Composable
internal fun IconMenu(open: Boolean, onDismiss: () -> Unit, items: List<MenuAct>) {
    DropdownMenu(
        open, onDismiss,
        modifier = Modifier.widthIn(min = 230.dp),
        shape = r(24),
        containerColor = Color(0xFF38393E),
    ) {
        items.forEach { m ->
            if (m.divider) HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = C.Border2)
            DropdownMenuItem(
                text = { Txt(m.label, 14, 500, if (!m.enabled) C.Dim else if (m.danger) C.Red else C.Text, maxLines = 1) },
                leadingIcon = { LineIcon(m.icon, 19.dp, if (m.enabled) C.Yellow else C.Yellow.copy(alpha = 0.6f), 2f) },
                enabled = m.enabled,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                onClick = { onDismiss(); m.go() },
            )
        }
    }
}

/** A labelled box that opens a list to pick from. */
@Composable
private fun PickField(label: String, value: String, placeholder: String, options: List<String>, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        Box {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12))
                    .clickable { open = true }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(value.ifEmpty { placeholder }, 14, 400, if (value.isEmpty()) C.Muted else C.Text, Modifier.weight(1f))
                LineIcon(Icons.ChevronDown, 18.dp, C.Soft, 2.4f)
            }
            DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp), containerColor = C.Surface) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Txt(o, 15, if (o == value) 700 else 500, if (o == value) C.Yellow else C.Text) },
                        onClick = { onPick(o); open = false },
                    )
                }
            }
        }
    }
}

/** A labelled box that opens the phone's time picker; the value is HH:mm. */
@Composable
private fun TimeField(label: String, value: String, placeholder: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12))
                .clickable {
                    val (h, m) = value.split(":").mapNotNull { it.toIntOrNull() }.let { (it.getOrNull(0) ?: 9) to (it.getOrNull(1) ?: 0) }
                    android.app.TimePickerDialog(context, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, true).show()
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(value.ifEmpty { placeholder }, 14, 400, if (value.isEmpty()) C.Muted else C.Text, Modifier.weight(1f))
            LineIcon(Icons.Clock, 20.dp, C.Soft, 2f)
        }
    }
}

/** A field label; a trailing * is drawn red (mandatory). */
@Composable
private fun FieldLabel(label: String) {
    val req = label.endsWith("*")
    Row {
        Txt(label.removeSuffix("*").trimEnd(), 12, 600, C.Soft)
        if (req) Txt(" *", 12, 700, C.Red)
    }
}

private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private fun phoneOk(p: String) = Regex("^[+0-9 ()-]+$").matches(p) && p.count(Char::isDigit) >= 6

/** Add or edit a building: details, address, primary contact, notes and site access. */
@Composable
private fun EditBuildingSheet(b: LocNode?, onClose: () -> Unit, onSave: (String, BuildingInfo) -> Unit) {
    val i0 = b?.info ?: BuildingInfo()
    var name by remember { mutableStateOf(b?.name.orEmpty()) }
    var info by remember { mutableStateOf(i0) }
    val emailBad = info.email.isNotBlank() && !EMAIL_RE.matches(info.email.trim())
    val phoneBad = info.phone.isNotBlank() && !phoneOk(info.phone.trim())
    val ok = name.isNotBlank() && info.type.isNotBlank() && !emailBad && !phoneBad
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (b == null) "Add building" else "Edit ${b.name}", 17, 700, modifier = Modifier.weight(1f), maxLines = 2)
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetSection("Building details")
            LabeledInput("Building name *", name, { name = it }, "Enter building name")
            PickField("Building type *", info.type, "Select type", BUILDING_TYPES) { info = info.copy(type = it) }
            LabeledInput("Address line 1", info.address1, { info = info.copy(address1 = it) }, "Enter address line 1")
            LabeledInput("Address line 2", info.address2, { info = info.copy(address2 = it) }, "Enter address line 2")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickField("Country", info.country, "Select country", COUNTRIES, Modifier.weight(1f)) { info = info.copy(country = it) }
                Box(Modifier.weight(1f)) { LabeledInput("State", info.state, { info = info.copy(state = it) }, "Enter state") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickField("Region", info.region, "Select region", REGIONS, Modifier.weight(1f)) { info = info.copy(region = it) }
                Box(Modifier.weight(1f)) { LabeledInput("City", info.city, { info = info.copy(city = it) }, "Enter city") }
            }

            Spacer(Modifier.height(4.dp))
            SheetSection("Primary contact")
            LabeledInput("Name", info.contactName, { info = info.copy(contactName = it) }, "Enter name")
            LabeledInput("Email", info.email, { info = info.copy(email = it) }, "Enter valid email")
            if (emailBad) Txt("That doesn’t look like an email address.", 12, 500, C.Red)
            LabeledInput("Phone number", info.phone, { info = info.copy(phone = it) }, "Enter valid phone number")
            if (phoneBad) Txt("Use digits, spaces, + ( ) and -, at least 6 digits.", 12, 500, C.Red)

            Spacer(Modifier.height(4.dp))
            SheetSection("Notes")
            LabeledInput("Notes", info.notes, { info = info.copy(notes = it) }, "Enter notes", singleLine = false, minLines = 3)

            Spacer(Modifier.height(4.dp))
            SheetSection("Availability")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Start time", info.startTime, "Select start time", Modifier.weight(1f)) { info = info.copy(startTime = it) }
                TimeField("End time", info.endTime, "Select end time", Modifier.weight(1f)) { info = info.copy(endTime = it) }
            }
            LabeledInput("Access code", info.accessCode, { info = info.copy(accessCode = it) }, "Enter access code")
        }
        SheetButtons(if (b == null) "Add building" else "Save", ok, onClose) { onSave(name, info) }
    }
}

/** A building's floors: type and code for each, their order (top first), and removing floors. */
@Composable
private fun EditFloorsSheet(b: LocNode, onClose: () -> Unit, onSave: (List<LocNode>) -> Unit) {
    var floors by remember(b.id) { mutableStateOf(b.kids.orEmpty()) }
    val removed = b.kids.orEmpty().filter { o -> floors.none { it.id == o.id } }
    fun edit(id: String, f: (LocNode) -> LocNode) { floors = floors.map { if (it.id == id) f(it) else it } }
    BottomSheet(onClose) {
        SheetHeader("Edit floors", "${b.name} · top of the list is shown first", onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (floors.isEmpty()) EmptyNote("No floors left. Save to remove them all, or Cancel to keep them.")
            floors.forEachIndexed { i, f ->
                var menu by remember(f.id) { mutableStateOf(false) }
                CfgCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LineIcon(Icons.Floor, 19.dp, C.Yellow, 2.1f)
                            PlainField(f.name, { v -> edit(f.id) { it.copy(name = v) } }, "Floor name", Modifier.weight(1f), size = 14, weight = 700)
                            Txt(plural(f.kids.orEmpty().size, "location", "locations"), 12, 400, C.Muted)
                            Box {
                                RoundButton(36.dp, Color.Transparent, { menu = true }) { LineIcon(Icons.Dots, 18.dp, C.Soft) }
                                IconMenu(menu, { menu = false }, listOf(
                                    MenuAct(Icons.ArrowUp, "Move up", enabled = i > 0) {
                                        floors = floors.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                                    },
                                    MenuAct(Icons.ArrowDown, "Move down", enabled = i < floors.size - 1) {
                                        floors = floors.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                                    },
                                    MenuAct(Icons.Trash, "Remove", danger = true) { floors = floors.filter { it.id != f.id } },
                                ))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallInput(f.type, { v -> edit(f.id) { it.copy(type = v) } }, "Type", Modifier.weight(1f))
                            SmallInput(f.code, { v -> edit(f.id) { it.copy(code = v) } }, "Code", Modifier.weight(1f))
                        }
                    }
                }
            }
            if (removed.isNotEmpty()) {
                val locs = removed.sumOf { it.kids.orEmpty().size }
                Txt(
                    "Removing ${removed.joinToString { it.name }}" + (if (locs > 0) " deletes ${plural(locs, "location", "locations")} too" else "") + " when you save.",
                    13, 500, C.Orange, maxLines = 3,
                )
            }
        }
        SheetButtons("Save", floors.all { it.name.isNotBlank() }, onClose) { onSave(floors) }
    }
}

@Composable
private fun SmallInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    PlainField(
        value, onChange, placeholder,
        modifier.clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12)).padding(horizontal = 12.dp, vertical = 10.dp),
        size = 13,
    )
}

@Composable
private fun LocationCard(l: LocNode, picked: Boolean, modifier: Modifier, onOpen: () -> Unit, onTick: () -> Unit) {
    Box(
        modifier.clip(r(14)).background(if (picked) C.PickedRow else C.Surface)
            .border(1.dp, if (picked) C.YTintBd else C.Border, r(14)).clickable(onClick = onOpen),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4A3B10)).border(1.dp, C.YTintBd, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Icons.Pin, 19.dp, C.Yellow, 2.1f)
            }
            Txt(l.name, 13, 700, maxLines = 2)
            Txt(listOf(l.type, l.code).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "No type yet" }, 11, 400, C.Muted)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(r(6)).clickable(onClick = onTick).padding(4.dp)) { CheckBox(picked) }
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
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clip(r(8)).clickable { bulk = !bulk }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CheckBox(bulk)
                Txt("Bulk addition", 13, 600)
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
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Cancel", onClose, height = 40.dp, hPad = 16.dp)
            Pill(
                if (names.size > 1) "Add ${names.size} locations" else "Add location",
                { if (names.isNotEmpty()) onAdd(names, type, code) },
                Modifier.weight(1f).alpha(if (names.isNotEmpty()) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
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
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(l.name, 15, 700, modifier = Modifier.weight(1f))
            if (!editing) RoundButton(36.dp, C.Btn, { editing = true }) { LineIcon(Icons.Pencil, 16.dp, C.Yellow, 2.2f) }
            Spacer(Modifier.width(8.dp))
            CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
        }
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                LabeledInput("Name *", name, { name = it }, "Location name")
                LabeledInput("Type", type, { type = it }, "e.g. Plant room")
                LabeledInput("Code", code, { code = it }, "e.g. B2-PR-01")
            } else {
                listOf("Name" to l.name, "Type" to l.type, "Code" to l.code).forEach { (k, v) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(k, 12, 600, C.Muted)
                        Txt(v.ifBlank { "–" }, 13, 600)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Pill("Cancel", { editing = false; name = l.name; type = l.type; code = l.code }, height = 40.dp, hPad = 16.dp)
                Pill(
                    "Save", { if (name.isNotBlank()) { onSave(name, type, code); editing = false } },
                    Modifier.weight(1f).alpha(if (name.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
                )
            } else {
                RoundButton(40.dp, Color(0xFF2A1919), onDelete) { LineIcon(Icons.Trash, 18.dp, C.Red, 2.2f) }
                Spacer(Modifier.weight(1f))
                Pill("Done", onClose, height = 40.dp, hPad = 20.dp)
            }
        }
    }
}

@Composable
private fun NameDialog(a: NameAsk, onClose: () -> Unit) {
    var v by remember(a) { mutableStateOf(a.initial) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onClose).blockSwipe().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(r(18)).background(C.Surface).border(1.dp, C.Border, r(18)).noRippleClick { }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(a.title, 15, 700)
            PlainField(
                v, { v = it }, a.placeholder,
                Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.5.dp, if (v.isNotBlank()) C.Yellow else C.Border, r(10)).padding(12.dp),
                size = 13,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancel", onClose, Modifier.weight(1f), height = 40.dp)
                Pill(
                    "Save", { if (v.isNotBlank()) { a.onSave(v.trim()); onClose() } },
                    Modifier.weight(1f).alpha(if (v.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Asset fields

@Composable
private fun AssetFields(vm: AppViewModel, adding: Boolean, setAdding: (Boolean) -> Unit) {
    val s = vm.state
    var addingPhoto by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<String?>(null) }
    var checksFor by remember { mutableStateOf<String?>(null) }
    var dial by remember { mutableStateOf(false) }
    // Fields read from a spreadsheet, waiting in the Add field sheet.
    var drafts by remember { mutableStateOf<List<FieldCfg>>(emptyList()) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFields(it) { list -> drafts = list; setAdding(true) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(top = 10.dp)) {
        Txt(
            "Choose what’s collected for every asset in ${vm.propName}, and what validation checks in each field. Mandatory fields are always visible.",
            12, 400, C.Muted, Modifier.padding(horizontal = 16.dp), maxLines = 4,
        )
        // Column headings, lined up with the rows' content.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("FIELD", 10, 600, C.Muted, Modifier.weight(1f), spacing = 0.8f)
            Txt("SHOW", 10, 600, C.Muted, Modifier.width(SHOW_W), spacing = 0.8f, align = TextAlign.Center)
            Txt("MANDATORY", 10, 600, C.Muted, Modifier.width(MUST_W), spacing = 0.8f, align = TextAlign.Center)
            Txt("CHECKS", 10, 600, C.Muted, Modifier.width(CHECKS_W), spacing = 0.8f, align = TextAlign.Center)
        }
        vm.fieldCfg.forEach { f ->
            FieldCfgRow(f, vm.optionCount(f), vm, onEdit = { editFor = f.key }, onChecks = { checksFor = f.key })
        }

        // Photos
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Photos", 14, 700)
                Txt("How many of each kind to collect per asset · ${vm.minPhotos} needed in total", 12, 400, C.Muted, maxLines = 2)
            }
            Pill("Add type", { addingPhoto = true }, height = 32.dp, hPad = 12.dp, size = 12)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("PHOTO", 10, 600, C.Muted, Modifier.weight(1f), spacing = 0.8f)
            Txt("MIN", 10, 600, C.Muted, Modifier.width(92.dp), spacing = 0.8f, align = TextAlign.Center)
            Txt("MAX", 10, 600, C.Muted, Modifier.width(92.dp), spacing = 0.8f, align = TextAlign.Center)
        }
        vm.photoReqs.forEach { p ->
            Row(
                Modifier.listCard().heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(Modifier.weight(1f).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt(p.name, 15, 600, modifier = Modifier.weight(1f, fill = false))
                        Box(Modifier.clip(CircleShape).clickable { vm.removePhotoReq(p.id) }.padding(2.dp)) { LineIcon(Icons.Close, 12.dp, C.Dim, 2.4f) }
                    }
                    Txt(p.hint, 12, 400, C.Muted, maxLines = 2)
                }
                Stepper(p.min, Modifier.width(92.dp)) { vm.setPhotoLimits(p.id, it, maxOf(p.max, it)) }
                Stepper(p.max, Modifier.width(92.dp)) { vm.setPhotoLimits(p.id, minOf(p.min, it), it) }
            }
        }
        Spacer(Modifier.height(160.dp)) // room for the floating +, as on the Assets list
    }
    // The floating + opens two ways to add fields. Drawn under the sheets and hidden while one is open.
    if (!adding && editFor == null && checksFor == null && !addingPhoto) {
        AddDial(
            dial, onToggle = { dial = !dial },
            onExcel = { dial = false; importer.launch(IMPORT_SHEET_TYPES) },
            onNew = { dial = false; drafts = emptyList(); setAdding(true) },
        )
    }
    if (adding) AddFieldsSheet(vm.fieldCfg, drafts, onClose = { setAdding(false); drafts = emptyList() }) { list ->
        vm.addFields(list); setAdding(false); drafts = emptyList()
    }
    editFor?.let { k -> vm.fieldCfg.firstOrNull { it.key == k } }?.let { f ->
        FieldSheet(
            f, draft = false, options = vm.optionCount(f), editableOptions = f.key !in FIXED_OPTIONS,
            onClose = { editFor = null },
            onSubmit = { vm.saveField(it); editFor = null },
            onDelete = if (f.custom) ({ vm.removeField(f.key); editFor = null }) else null,
        )
    }
    checksFor?.let { k -> vm.fieldCfg.firstOrNull { it.key == k } }?.let { f ->
        FieldChecksSheet(f, vm.optionCount(f), onClose = { checksFor = null }) { e, rule, lo, hi ->
            vm.setFieldChecks(f.key, e, rule, lo, hi); checksFor = null
        }
    }
    if (addingPhoto) {
        var name by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        BottomSheet({ addingPhoto = false }) {
            SheetHeader("Add photo type", "E.g. Data plate, Valve position, Meter reading.", { addingPhoto = false })
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledInput("Name *", name, { name = it }, "e.g. Meter reading")
                LabeledInput("Hint for field workers", hint, { hint = it }, "What the photo should show")
            }
            SheetButtons("Add", name.isNotBlank(), { addingPhoto = false }) { vm.addPhotoReq(name, hint); addingPhoto = false }
        }
    }
}

private val SHOW_W = 52.dp
private val MUST_W = 92.dp
private val CHECKS_W = 72.dp

/** Dropdowns whose choices are fixed lists (classification, JACS), so they have no options to edit. */
private val FIXED_OPTIONS = setOf("sys", "typ", "sub", "jacs")

/** One line on what validation checks in [f], for the Edit sheet. */
private fun checksSummary(f: FieldCfg): String {
    val parts = mutableListOf<String>()
    if (f.required || f.flagEmpty) parts += "Flagged when empty"
    if (f.rule != ValueRule.NONE) parts += f.rule.label
    val unit = when (f.kind) {
        FieldKind.NUMBER -> ""
        FieldKind.DATE -> ""
        else -> " characters"
    }
    val lo = f.min.takeIf { it.isNotBlank() }
    val hi = f.max.takeIf { it.isNotBlank() }
    when {
        lo != null && hi != null -> parts += "$lo–$hi$unit"
        lo != null -> parts += (if (f.kind == FieldKind.DATE) "From $lo" else "At least $lo$unit")
        hi != null -> parts += (if (f.kind == FieldKind.DATE) "Up to $hi" else "At most $hi$unit")
    }
    return parts.joinToString(" · ").ifEmpty { "Nothing beyond its type" }
}

@Composable
private fun FieldCfgRow(f: FieldCfg, options: Int, vm: AppViewModel, onEdit: () -> Unit, onChecks: () -> Unit) {
    Row(
        Modifier.listCard().clickable(onClick = onEdit).heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 4.dp).alpha(if (f.visible) 1f else 0.55f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(f.label, 13, 600, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (f.locked) LineIcon(Icons.Lock, 13.dp, C.Dim, 2.4f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KindChip(f.kind)
                if (f.kind == FieldKind.DROPDOWN) Txt(plural(options, "option", "options"), 11, 700, C.Yellow, maxLines = 1)
            }
        }
        // Show: the eye. Mandatory fields stay visible.
        Box(
            Modifier.size(SHOW_W, 44.dp).clip(r(8)).clickable(enabled = !f.locked) { vm.toggleFieldVisible(f.key) },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                if (f.visible) Icons.Eye else Icons.EyeOff, 19.dp,
                when {
                    f.locked -> C.Yellow.copy(alpha = 0.35f)
                    f.visible -> C.Yellow
                    else -> C.Dim
                },
                2f,
            )
        }
        // Mandatory: the tick.
        Box(
            Modifier.size(MUST_W, 44.dp).clip(r(8)).clickable(enabled = !f.locked) { vm.toggleFieldRequired(f.key) },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.alpha(if (f.locked) 0.4f else 1f)) { CheckBox(f.required) } }
        // Checks: what validation looks for beyond the kind.
        Box(Modifier.width(CHECKS_W), contentAlignment = Alignment.Center) {
            val tag = when {
                f.hasFormat -> "Format"
                f.flagEmpty && !f.required -> "Empty"
                else -> null
            }
            Box(
                Modifier.clip(r(8)).background(if (tag != null) C.YTintBg else Color.Transparent).clickable(onClick = onChecks)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Txt(tag ?: "Add", 12, if (tag != null) 700 else 600, if (tag != null) C.Yellow else C.Soft) }
        }
    }
}

/** The floating + and the two ways to add fields it opens (Import from Excel, New field). */
@Composable
private fun AddDial(open: Boolean, onToggle: () -> Unit, onExcel: () -> Unit, onNew: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, label = "dial")
    if (open) Box(Modifier.fillMaxSize().background(Color(0xA6000000)).noRippleClick(onToggle).blockSwipe())
    Column(
        Modifier.fillMaxSize().wrapContentSize(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialOption("Import from Excel", "One field per column", Icons.Table, C.Green, onExcel)
                DialOption("New field", "Pick types and set them up", Icons.FileDoc, C.Text, onNew)
            }
        }
        Box(
            Modifier.size(54.dp).fabShadow().clip(CircleShape).background(C.Yellow).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 26.dp, C.OnYellow, 2.6f, Modifier.rotate(turn)) }
    }
}

@Composable
private fun DialOption(title: String, sub: String, icon: Ico, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(r(28)).background(Color(0xFF2A2B30)).border(1.dp, C.Border2, r(28)).clickable(onClick = onClick)
            .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Txt(title, 14, 700)
            Txt(sub, 12, 400, C.Muted)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
            LineIcon(icon, 19.dp, tint, 2f)
        }
    }
}

/** A sheet title and line under it, with the tighter side padding of the add-field sheets. */
@Composable
private fun TightSheetHeader(title: String, sub: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 16, 700)
            sub?.let { Txt(it, 12, 400, C.Muted, maxLines = 2) }
        }
        CloseButton(onClose, size = 34.dp, iconSize = 14.dp)
    }
}

/** The kinds the Add field sheet offers, in its grid order. */
private val ADD_KINDS = listOf(
    FieldKind.TEXT, FieldKind.CHECKBOX, FieldKind.MULTI, FieldKind.YESNO, FieldKind.DATE,
    FieldKind.TIME, FieldKind.NUMBER, FieldKind.URL, FieldKind.DROPDOWN,
)

/** Pick types to add; each opens its setup sheet, and Save adds every field set up here. */
@Composable
private fun AddFieldsSheet(existing: List<FieldCfg>, initial: List<FieldCfg>, onClose: () -> Unit, onSave: (List<FieldCfg>) -> Unit) {
    var drafts by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<FieldCfg?>(null) }
    fun nameFor(k: FieldKind): String {
        val taken = (existing + drafts).map { it.label.lowercase() }.toSet()
        var n = k.shown
        var i = 2
        while (n.lowercase() in taken) n = "${k.shown} ${i++}"
        return n
    }
    BottomSheet(onClose) {
        TightSheetHeader("Add field", "Pick a type to add a field and set it up. Save adds them all.", onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ADD_KINDS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { k ->
                        KindTile(k, Modifier.weight(1f)) {
                            editing = FieldCfg("cf_${System.nanoTime()}", nameFor(k), k, FieldSection.MORE, custom = true)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (drafts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SheetSection("Selected fields")
                CfgCard {
                    drafts.forEachIndexed { i, d ->
                        Row(
                            Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier).clickable { editing = d }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LineIcon(d.kind.icon(), 20.dp, C.Yellow, 2.1f)
                            Column(Modifier.weight(1f)) {
                                Txt(d.label, 13, 700)
                                Txt(d.kind.shown + if (d.required) " · Mandatory" else "", 12, 400, C.Muted)
                            }
                            LineIcon(Icons.ChevronRight, 15.dp, C.Muted, 2.4f)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp)) {
            Pill(
                if (drafts.isEmpty()) "Save" else "Save ${plural(drafts.size, "field", "fields")}", { if (drafts.isNotEmpty()) onSave(drafts) },
                Modifier.fillMaxWidth().alpha(if (drafts.isNotEmpty()) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
            )
        }
    }
    editing?.let { d ->
        val isNew = drafts.none { it.key == d.key }
        FieldSheet(
            d, draft = true, options = d.options.size, editableOptions = true,
            onClose = { editing = null },
            onSubmit = { f -> drafts = if (isNew) drafts + f else drafts.map { if (it.key == f.key) f else it }; editing = null },
            onDelete = { drafts = drafts.filter { it.key != d.key }; editing = null },
        )
    }
}

@Composable
private fun KindTile(k: FieldKind, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(k.icon(), 18.dp, C.Soft, 2.1f)
        Txt(k.shown, 13, 600, modifier = Modifier.weight(1f))
        LineIcon(Icons.PlusSmall, 16.dp, C.Yellow, 2.6f)
    }
}

/** The fixed choices of the classification dropdowns. */
private fun fixedChoices(key: String): List<String> = when (key) {
    "sys" -> SYSTEM_TYPES
    "typ" -> ASSET_TYPES
    "sub" -> SUB_TYPES
    "jacs" -> JACS_CODES.map { it.first }.filter { it.isNotEmpty() }
    else -> emptyList()
}

/**
 * One field's setup: name, options (dropdowns), default value, tooltip, visible / mandatory, display
 * format (dates, times) and checks. [draft] fields are being added (Submit), the rest edited (Save).
 */
@Composable
private fun FieldSheet(
    f: FieldCfg,
    draft: Boolean,
    options: Int,
    editableOptions: Boolean,
    onClose: () -> Unit,
    onSubmit: (FieldCfg) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var cur by remember(f.key) { mutableStateOf(f) }
    var opt by remember(f.key) { mutableStateOf("") }
    var checks by remember(f.key) { mutableStateOf(false) }
    val ownOptions = f.kind in OPTION_KINDS && editableOptions
    val ok = cur.label.isNotBlank() && (!ownOptions || cur.options.isNotEmpty())
    fun addOption() {
        val o = opt.trim()
        if (o.isNotEmpty() && cur.options.none { it.equals(o, true) }) cur = cur.copy(options = cur.options + o)
        opt = ""
    }
    BottomSheet(onClose) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(34.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(f.kind.icon(), 19.dp, C.Yellow, 2.2f)
            }
            Txt(if (draft) f.kind.shown else "Edit ${f.label}", 17, 700, modifier = Modifier.weight(1f), maxLines = 2)
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledInput("Name *", cur.label, { cur = cur.copy(label = it) }, "Field name")
            if (ownOptions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Options *")
                if (cur.options.isNotEmpty()) CfgCard {
                    cur.options.forEachIndexed { i, o ->
                        Row(
                            Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                                .padding(start = 10.dp, end = 2.dp, top = 0.dp, bottom = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(o, 13, 600, modifier = Modifier.weight(1f))
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { cur = cur.copy(options = cur.options - o) }, contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Close, 14.dp, C.Muted, 2.4f)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12)).padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainField(opt, { opt = it }, "Add an option", Modifier.weight(1f), size = 14)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(C.Yellow).clickable(onClick = ::addOption), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.PlusSmall, 18.dp, C.OnYellow, 2.8f)
                    }
                }
            }
            DefaultValueInput(cur, if (ownOptions) cur.options else fixedChoices(f.key)) { cur = cur.copy(defaultValue = it) }
            LabeledInput("Tooltip", cur.tooltip, { cur = cur.copy(tooltip = it) }, "Help shown next to the field", singleLine = false, minLines = 3)
            CfgCard {
                if (!draft) ToggleRow("Visible", "Shown on the asset form", cur.visible, enabled = !f.locked && !cur.required) { cur = cur.copy(visible = it) }
                ToggleRow("Mandatory", "Must be filled before submitting", cur.required, enabled = !f.locked, divider = !draft) {
                    cur = cur.copy(required = it, visible = cur.visible || it)
                }
            }
            if (f.kind == FieldKind.DATE || f.kind == FieldKind.TIME) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Display format")
                CfgCard {
                    (listOf("") + if (f.kind == FieldKind.DATE) DATE_DISPLAY else TIME_DISPLAY).forEachIndexed { i, v ->
                        Row(
                            Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier).clickable { cur = cur.copy(display = v) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(v.ifEmpty { "No format" }, 15, if (cur.display == v) 700 else 500, modifier = Modifier.weight(1f))
                            RadioDot(cur.display == v)
                        }
                    }
                }
            }
            CfgCard { LinkRow(Icons.ShieldCheck, "Checks", checksSummary(cur), { checks = true }) }
            if (onDelete != null) Row(
                Modifier.fillMaxWidth().height(42.dp).clip(r(14)).border(1.dp, C.Red.copy(alpha = 0.7f), r(14)).clickable(onClick = onDelete),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Icons.Trash, 17.dp, C.Red, 2.2f)
                Txt("Delete field", 13, 700, C.Red)
            }
        }
        SheetButtons(if (draft) "Submit" else "Save", ok, onClose) { onSubmit(cur) }
    }
    if (checks) {
        FieldChecksSheet(cur, if (ownOptions) cur.options.size else options, onClose = { checks = false }) { e, rule, lo, hi ->
            cur = cur.copy(flagEmpty = e, rule = rule, min = lo, max = hi); checks = false
        }
    }
}

/** The default value input for [f]'s kind; [choices] are its options when it has some. */
@Composable
private fun DefaultValueInput(f: FieldCfg, choices: List<String>, onChange: (String) -> Unit) {
    val v = f.defaultValue
    when (f.kind) {
        FieldKind.CHECKBOX -> Row(
            Modifier.fillMaxWidth().clip(r(10)).clickable { onChange(if (v == "Yes") "" else "Yes") }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckBox(v == "Yes")
            Column {
                Txt("Default status", 13, 700)
                Txt("Ticked when a new asset is started", 12, 400, C.Muted)
            }
        }
        FieldKind.YESNO -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Default value")
            val vals = listOf("", "Yes", "No")
            Segmented(listOf("None", "Yes", "No"), vals.indexOf(v).coerceAtLeast(0), { onChange(vals[it]) })
        }
        FieldKind.DATE -> PickField("Default value", v, "Select…", listOf("None", "Today")) { onChange(if (it == "None") "" else it) }
        FieldKind.TIME -> PickField("Default value", v, "Select…", listOf("None", "Now")) { onChange(if (it == "None") "" else it) }
        FieldKind.DROPDOWN, FieldKind.MULTI ->
            PickField("Default value", v, if (choices.isEmpty()) "Add options first" else "Select…", if (choices.isEmpty()) emptyList() else listOf("None") + choices) {
                onChange(if (it == "None") "" else it)
            }
        FieldKind.NUMBER -> LabeledInput("Default value", v, { s -> onChange(s.filter { it.isDigit() || it == '.' || it == '-' }) }, "Pre-filled number")
        FieldKind.URL -> LabeledInput("Default value", v, onChange, "https://")
        else -> LabeledInput("Default value", v, onChange, "Pre-filled text")
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, enabled: Boolean, divider: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (divider) Modifier.topBorder(C.Border) else Modifier).alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 14, 700)
            Txt(sub, 12, 400, C.Muted)
        }
        OnOffSwitch(on) { if (enabled) onChange(it) }
    }
}

@Composable
private fun LinkRow(icon: Ico, title: String, sub: String, onClick: () -> Unit, divider: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().then(if (divider) Modifier.topBorder(C.Border) else Modifier).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(icon, 19.dp, C.Yellow, 2.1f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 14, 700)
            Txt(sub, 12, 400, C.Muted, maxLines = 2)
        }
        LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.4f)
    }
}

/** Yellow small-caps section title inside a sheet. */
@Composable
private fun SheetSection(text: String) = Txt(text.uppercase(), 12, 700, C.Yellow, spacing = 0.6f)

@Composable
private fun InfoBox(text: String) {
    Txt(text, 13, 400, C.Soft, Modifier.fillMaxWidth().clip(r(12)).background(C.Raised).padding(10.dp), maxLines = 4)
}

/** What validation looks for in a field: flag it when empty, and what its values must be. */
@Composable
private fun FieldChecksSheet(f: FieldCfg, options: Int, onClose: () -> Unit, onSave: (Boolean, ValueRule, String, String) -> Unit) {
    var flag by remember(f.key) { mutableStateOf(f.flagEmpty) }
    var rule by remember(f.key) { mutableStateOf(f.rule) }
    var lo by remember(f.key) { mutableStateOf(f.min) }
    var hi by remember(f.key) { mutableStateOf(f.max) }
    var ruleOpen by remember { mutableStateOf(false) }
    val rules = ValueRule.entries.filter { f.kind in it.kinds }
    fun ruleName(r: ValueRule) = if (r == ValueRule.NONE) noRuleLabel(f.kind) else r.label
    val numeric: (String) -> String = { v -> v.filter { it.isDigit() || it == '.' || it == '-' } }
    BottomSheet(onClose) {
        TightSheetHeader("Checks: ${f.label}", "What validation looks for in this field, beyond its type.", onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetSection("When it’s empty")
            if (f.required) InfoBox("This field is mandatory, so validation always flags it when it’s left empty.")
            else CfgCard { ToggleRow("Flag it when it’s left empty", "Validation lists it as something to fix.", flag, enabled = true) { flag = it } }

            Spacer(Modifier.height(4.dp))
            SheetSection("Format")
            val fixedNote = when (f.kind) {
                FieldKind.DROPDOWN, FieldKind.MULTI -> "Values must be one of its ${plural(options, "option", "options")}."
                FieldKind.CHECKBOX -> "Ticked or not: nothing more to check."
                FieldKind.YESNO -> "Yes or No: nothing more to check."
                FieldKind.URL -> "Values must be a web link starting with http:// or https://."
                FieldKind.TIME -> "Values must be a time, like 09:30."
                else -> null
            }
            if (fixedNote != null) {
                InfoBox(fixedNote)
            } else {
                Txt("Values must be", 13, 600, C.Soft)
                Box {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12))
                            .clickable { ruleOpen = true }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(ruleName(rule), 14, 500, modifier = Modifier.weight(1f))
                        Column {
                            LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f, Modifier.rotate(180f))
                            LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f)
                        }
                    }
                    DropdownMenu(ruleOpen, { ruleOpen = false }, containerColor = C.Surface) {
                        rules.forEach { r ->
                            DropdownMenuItem(
                                text = { Txt(ruleName(r), 15, if (r == rule) 700 else 500, if (r == rule) C.Yellow else C.Text) },
                                onClick = { rule = r; ruleOpen = false },
                            )
                        }
                    }
                }
                val (loLabel, hiLabel, loHint, hiHint) = when (f.kind) {
                    FieldKind.NUMBER -> listOf("Min value (optional)", "Max value (optional)", "e.g. 0", "e.g. 5000")
                    FieldKind.DATE -> listOf("Not before year (optional)", "Not after year (optional)", "e.g. 1950", "e.g. 2040")
                    else -> listOf("Min length (optional)", "Max length (optional)", "e.g. 4", "e.g. 20")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { LabeledInput(loLabel, lo, { lo = numeric(it) }, loHint) }
                    Box(Modifier.weight(1f)) { LabeledInput(hiLabel, hi, { hi = numeric(it) }, hiHint) }
                }
            }
        }
        SheetButtons("Save", true, onClose) { onSave(flag && !f.required, rule, lo, hi) }
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
        Txt(value.toString(), 13, 700, modifier = Modifier.width(26.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = value < 9) { onChange(value + 1) }, contentAlignment = Alignment.Center) {
            LineIcon(Icons.PlusSmall, 14.dp, if (value < 9) C.Text else C.Dim, 2.6f)
        }
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
                .clickable { vm.setGeoEnforce(!vm.geoEnforce) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckBox(vm.geoEnforce)
            Column(Modifier.weight(1f)) {
                Txt("Enforce location restriction for collecting asset data", 13, 600, maxLines = 2)
                Txt(if (vm.geoEnforce) "Assets can only be collected inside the geofence." else "Assets can be collected anywhere.", 12, 400, C.Muted)
            }
        }
        Txt("Draw geofence", 14, 700, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Without MATCH_PARENT the WebView sizes its viewport to the (empty) page: a 0-high map.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // Drags on the map pan the map instead of swiping to the next tab.
                    setOnTouchListener { v, e ->
                        if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
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
