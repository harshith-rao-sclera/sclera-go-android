package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.presentation.common.storedTimeText
import android.annotation.SuppressLint
import android.content.res.Resources
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.BUILDING_TYPES
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.COUNTRIES
import com.sclera.sclerago.domain.model.DEFAULT_DATE_PICKER
import com.sclera.sclerago.domain.model.DEFAULT_NOW
import com.sclera.sclerago.domain.model.DEFAULT_OPTION_COLOR
import com.sclera.sclerago.domain.model.DEFAULT_TIME_PICKER
import com.sclera.sclerago.domain.model.DEFAULT_TODAY
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.MAX_PATTERN_LENGTH
import com.sclera.sclerago.domain.model.MAX_PHOTOS_PER_ASSET
import com.sclera.sclerago.domain.model.MAX_PLACE_CODE
import com.sclera.sclerago.domain.model.MAX_PLACE_NAME
import com.sclera.sclerago.domain.model.MAX_PLACE_TYPE
import com.sclera.sclerago.domain.model.MAX_TOOLTIP_LENGTH
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.OPTION_COLORS
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.OptionProblem
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.REGIONS
import com.sclera.sclerago.domain.model.TEXT_KINDS
import com.sclera.sclerago.domain.model.UNCHECKED
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.model.optionProblem
import com.sclera.sclerago.domain.usecase.config.ChecksProblem
import com.sclera.sclerago.domain.usecase.config.PhotoBound
import com.sclera.sclerago.domain.usecase.config.checksProblem
import com.sclera.sclerago.domain.usecase.config.nameTaken
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.BottomTabBar
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.IconMenu
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.components.NavItem
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.components.OnOffSwitch
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.ReportUnsaved
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.Segmented
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.CfgPage
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive

private class CfgTabSpec(val page: CfgPage, @StringRes val label: Int, val icon: Ico)

private val CFG_TABS = listOf(
    CfgTabSpec(CfgPage.FIELDS, R.string.cfgt_tab_asset_fields, Icons.Box),
    CfgTabSpec(CfgPage.BUILDING, R.string.cfgt_tab_building_info, Icons.Building),
    CfgTabSpec(CfgPage.GEO, R.string.cfgt_tab_geofencing, Icons.Pin),
    CfgTabSpec(CfgPage.SETTINGS, R.string.cfgt_tab_settings, Icons.Toggles),
)

private val PageEasing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * The Configuration page (side menu → Configuration), as four bottom tabs that work like the main
 * screen's (tap one, or swipe the page; the highlight follows your finger): Building info (buildings, floors, locations), Asset fields (which fields show / are
 * required, formats, photo counts), Geofencing (map) and Property Settings (Bluetooth / Wi-Fi
 * onboarding). System back closes Configuration and returns to the app.
 */
@Composable
fun ConfigScreen(
    vm: AppViewModel,
    cfg: ConfigViewModel = hiltViewModel(),
    fields: FieldsConfigViewModel = hiltViewModel(),
    places: PlacesConfigViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    val s = vm.state
    val online by connectivity.online.collectAsStateWithLifecycle()
    var addingField by remember { mutableStateOf(false) }
    // Configuration reads and writes the server (fields, places, settings, geofence): read again each time it opens.
    LaunchedEffect(vm.currentProp) {
        cfg.open(vm.currentProp)
        fields.open(vm.currentProp)
        places.open(vm.currentProp)
    }
    LaunchedEffect(cfg) { cfg.messages.collect { vm.flash(it) } }
    LaunchedEffect(fields) { fields.messages.collect { vm.flash(it) } }
    LaunchedEffect(places) { places.messages.collect { vm.flash(it) } }
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
    // Leaving with fences drawn or changed but not saved asks first (as the web's unsaved-changes guard).
    var askLeave by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (cfg.geoUnsaved) askLeave = true else vm.closeConfig()
    }
    BackHandler(onBack = close)
    // Fences not saved count as unsaved changes: a side-menu page asks before leaving them.
    ReportUnsaved(cfg.geoUnsaved)
    SecureWindow() // building access codes and contacts are shown here
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.menu_configuration), vm.propName, vm::openMenu)
            // Every tab stays composed (as the web keeps them mounted), so unsaved work on one, such as fences being
            // drawn on the map, survives switching to another.
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = CFG_TABS.size - 1, key = { it }) { i ->
                val page = CFG_TABS[i].page
                Column(Modifier.fillMaxSize()) {
                    // Asset fields, geofences and property settings are kept on the server; Building info works offline.
                    if (!online) page.onlineOnly()?.let { OfflineBanner(it, Modifier.padding(horizontal = pageGutter() + 12.dp).padding(top = 10.dp)) }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (page) {
                            CfgPage.BUILDING -> BuildingInfo(places, vm.propName)
                            CfgPage.FIELDS -> AssetFields(fields, cfg, vm.propName, addingField) { addingField = it }
                            CfgPage.GEO -> Geofencing(cfg)
                            CfgPage.SETTINGS -> PropertySettingsPage(vm, cfg)
                        }
                    }
                }
            }
            // The highlight follows the pager's live position, so it slides with your finger.
            val pos = pager.currentPage + pager.currentPageOffsetFraction
            BottomTabBar {
                CFG_TABS.forEachIndexed { i, t ->
                    val on = (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)
                    NavItem(stringResource(t.label), t.icon, 21.dp, on, first = i == 0, last = i == CFG_TABS.size - 1) { vm.cfgGo(t.page) }
                }
            }
        }
        if (askLeave) {
            DiscardChangesDialog(
                onKeep = { askLeave = false },
                onDiscard = {
                    askLeave = false
                    cfg.markGeoUnsaved(false)
                    vm.closeConfig()
                },
            )
        }
    }
}

// ---------------------------------------------------------------- Tabs

/** Why a Configuration tab needs the server, for its offline banner; null for one that works offline. */
private fun CfgPage.onlineOnly(): OnlineOnly? = when (this) {
    CfgPage.BUILDING -> null
    CfgPage.FIELDS -> OnlineOnly.ASSET_FIELDS
    CfgPage.GEO -> OnlineOnly.GEOFENCING
    CfgPage.SETTINGS -> OnlineOnly.PROPERTY_SETTINGS
}

// ---------------------------------------------------------------- Building info

private class NameAsk(val title: String, val initial: String, val placeholder: String, val onSave: (String) -> Unit)
private class DeleteAsk(val title: String, val body: String, val onYes: () -> Unit)

/**
 * [body] with the web's note when one of the places being deleted is also in the linked VDMS: places don't sync, so
 * VDMS keeps its copy.
 */
private fun withVdmsNote(res: Resources, body: String, places: List<LocNode>): String =
    if (places.any { it.linkedToVdms }) res.getString(R.string.cfg_delete_vdms_note, body) else body

@Composable
private fun BuildingInfo(places: PlacesConfigViewModel, propName: String) {
    if (places.load != LoadState.READY) {
        OnlineNote(places.load, places.failure, places::retry)
        return
    }
    val tree = places.tree
    var bId by remember { mutableStateOf<String?>(null) }
    var fId by remember { mutableStateOf<String?>(null) }
    val building = tree.firstOrNull { it.id == bId } ?: tree.firstOrNull()
    val floors = building?.kids.orEmpty()
    val floor = floors.firstOrNull { it.id == fId } ?: floors.firstOrNull()
    val locs = floor?.kids.orEmpty()
    var q by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var dial by remember { mutableStateOf(false) }
    var sel by remember(floor?.id) { mutableStateOf(setOf<String>()) }
    var nameAsk by remember { mutableStateOf<NameAsk?>(null) }
    var deleteAsk by remember { mutableStateOf<DeleteAsk?>(null) }
    // Delete confirmations are built in click handlers, so they read the strings from here.
    val res = LocalContext.current.resources
    var openLoc by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Add floor sheet for this building (null: closed).
    var addingFloorTo by remember { mutableStateOf<LocNode?>(null) }
    // Add / edit building sheet: null = closed, "" = new building, else the building's id.
    var buildingSheet by remember { mutableStateOf<String?>(null) }
    var floorsSheet by remember { mutableStateOf(false) }

    fun closeSearch() { searching = false; q = "" }
    fun addFloor(b: LocNode) {
        addingFloorTo = b
    }
    BackHandler(enabled = searching, onBack = ::closeSearch)
    BackHandler(enabled = dial) { dial = false }

    val buildingMenu = buildList {
        tree.forEach { b -> add(MenuAct(Icons.Building, if (b.active) b.name else stringResource(R.string.cfgt_name_hidden, b.name), selected = b.id == building?.id) { bId = b.id; fId = null }) }
        add(MenuAct(Icons.PlusCircle, stringResource(R.string.cfgt_add_building), divider = tree.isNotEmpty()) { buildingSheet = "" })
        if (building != null) add(MenuAct(Icons.Pencil, stringResource(R.string.access_edit_group, building.name)) { buildingSheet = building.id })
        if (floors.isNotEmpty()) add(MenuAct(Icons.Floor, stringResource(R.string.cfgt_edit_floors)) { floorsSheet = true })
        add(MenuAct(Icons.Import, stringResource(R.string.cfgt_import_buildings)) { places.openImport() })
        if (building != null) add(MenuAct(Icons.Trash, stringResource(R.string.property_delete_label, building.name), danger = true, divider = true) {
            deleteAsk = DeleteAsk(
                res.getString(R.string.cfg_delete_place_title, building.name),
                withVdmsNote(res, res.getString(R.string.cfg_delete_building_body, building.name), listOf(building)),
            ) {
                places.delete(listOf(building)) { bId = null; fId = null }
            }
        })
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter())
                    .padding(horizontal = 9.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BuildingCard(building, buildingMenu)
                val query = q.trim()
                when {
                    searching && query.isNotEmpty() -> SearchHits(tree, query) { hit ->
                        bId = hit.building; fId = hit.floor
                        hit.location?.let { openLoc = it }
                        closeSearch()
                    }
                    building == null -> EmptyNote(stringResource(R.string.cfgt_buildings_empty))
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FloorRail(floors, floor?.id, onPick = { fId = it }, onAdd = { addFloor(building) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (floor == null) {
                                Txt(stringResource(R.string.cfgt_no_floors), 16, 700)
                                Txt(stringResource(R.string.cfgt_no_floors_hint), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
                            } else {
                                Column {
                                    Txt(floor.name, 16, 700)
                                    Txt(pluralStringResource(R.plurals.pi_count_locations, locs.size, locs.size), 13, 400, C.Muted)
                                }
                                if (locs.isEmpty()) {
                                    Txt(stringResource(R.string.cfgt_no_locations), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
                                } else {
                                    Column(Modifier.fillMaxWidth().clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16))) {
                                        locs.forEachIndexed { i, l ->
                                            LocationRow(l, l.id in sel, first = i == 0,
                                                onOpen = { openLoc = l.id },
                                                onTick = { sel = if (l.id in sel) sel - l.id else sel + l.id })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(150.dp)) // room for the floating buttons
            }

            val sheetOpen = adding || openLoc != null || buildingSheet != null || floorsSheet || nameAsk != null || deleteAsk != null ||
                places.importing != null
            if (!sheetOpen && sel.isEmpty()) {
                BuildingFabs(
                    dial = dial,
                    searching = searching,
                    query = q,
                    onQuery = { q = it },
                    onToggleDial = { if (searching) closeSearch(); dial = !dial },
                    onSearch = { dial = false; searching = true },
                    onCloseSearch = ::closeSearch,
                    options = buildList {
                        if (floor != null) add(DialAct(stringResource(R.string.pi_kind_location_label), stringResource(R.string.cfgt_on_place, floor.name), Icons.Pin, C.Yellow) { dial = false; adding = true })
                        if (building != null) add(DialAct(stringResource(R.string.pi_kind_floor_label), stringResource(R.string.cfgt_in_place, building.name), Icons.Floor, C.Text) { dial = false; addFloor(building) })
                        add(DialAct(stringResource(R.string.pi_kind_building_label), stringResource(R.string.cfgt_in_place, propName), Icons.Building, C.Text) { dial = false; buildingSheet = "" })
                    },
                )
            }
        }
        if (sel.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(pluralStringResource(R.plurals.cfgt_locations_selected, sel.size, sel.size), 12, 700, modifier = Modifier.weight(1f))
                Pill(stringResource(R.string.assign_clear), { sel = emptySet() }, height = 40.dp, hPad = 14.dp)
                Pill(stringResource(R.string.action_delete), {
                    val ids = sel
                    deleteAsk = DeleteAsk(
                        res.getQuantityString(R.plurals.cfg_delete_locations_title, ids.size, ids.size),
                        withVdmsNote(res, res.getQuantityString(R.plurals.cfg_delete_locations_body, ids.size), locs.filter { it.id in ids }),
                    ) {
                        places.delete(locs.filter { it.id in ids }) { sel = emptySet() }
                    }
                }, bg = Color(0xFF2A1919), fg = C.Red, height = 40.dp, hPad = 16.dp, weight = 700)
            }
        }
    }

    if (adding && floor != null) AddPlacesSheet(PlaceSheetKind.LOCATION, onClose = { adding = false }) { rows ->
        places.addLocations(floor, rows); adding = false
    }
    addingFloorTo?.let { b ->
        AddPlacesSheet(PlaceSheetKind.FLOOR, onClose = { addingFloorTo = null }) { rows ->
            places.addFloors(b, rows) { fId = it }; addingFloorTo = null
        }
    }
    openLoc?.let { id -> tree.findNode(id) }?.let { l ->
        LocationSheet(l, onClose = { openLoc = null },
            onSave = { n, t, c -> floorOf(tree, l.id)?.let { places.updateLocation(it, l, n, t, c) } },
            onDelete = {
                deleteAsk = DeleteAsk(
                    res.getString(R.string.cfg_delete_place_title, l.name),
                    withVdmsNote(res, res.getString(R.string.cfg_delete_location_body, l.name), listOf(l)),
                ) { places.delete(listOf(l)) { openLoc = null } }
            })
    }
    buildingSheet?.let { id ->
        val b = if (id.isEmpty()) null else tree.firstOrNull { it.id == id }
        EditBuildingSheet(b, onClose = { buildingSheet = null }) { n, info, visible ->
            if (b == null) places.addBuilding(n, info) { bId = it; fId = null } else places.saveBuilding(b, n, info, visible)
            buildingSheet = null
        }
    }
    if (floorsSheet && building != null) EditFloorsSheet(building, onClose = { floorsSheet = false }) { list ->
        places.saveFloors(building, list); floorsSheet = false
    }
    places.importing?.let { state ->
        ConfigImportSheet(state, places::pickImport, places::saveImportSample, places::runImport, places::closeImport)
    }
    nameAsk?.let { a -> NameDialog(a) { nameAsk = null } }
    deleteAsk?.let { d ->
        ConfirmDialog(
            icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red, title = d.title, body = d.body,
            no = stringResource(R.string.cfg_cancel), yes = stringResource(R.string.action_delete), onNo = { deleteAsk = null }, onYes = { d.onYes(); deleteAsk = null },
        )
    }
}

/** The chosen building; tapping it lists the other buildings and what you can do with them. */
@Composable
private fun BuildingCard(building: LocNode?, menu: List<MenuAct>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16))
                .clickable { open = true }.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(r(11)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Building, 20.dp, C.Yellow, 1.9f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                if (building == null) {
                    Txt(stringResource(R.string.cfgt_no_buildings), 16, 700)
                    Txt(stringResource(R.string.cfgt_no_buildings_hint), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
                } else {
                    val floors = building.kids.orEmpty()
                    val locations = floors.sumOf { it.kids.orEmpty().size }
                    Txt(building.name, 16, 700)
                    Txt(
                        stringResource(
                            if (building.active) R.string.cfgt_building_summary else R.string.cfgt_building_summary_hidden,
                            pluralStringResource(R.plurals.pi_count_floors, floors.size, floors.size),
                            pluralStringResource(R.plurals.pi_count_locations, locations, locations),
                        ),
                        13, 400, C.Muted,
                    )
                }
            }
            LineIcon(Icons.ChevronDown, 18.dp, C.Muted, 2.4f)
        }
        IconMenu(open, { open = false }, menu)
    }
}

/** The building's floors as tiles down the left; the chosen one is yellow. The dashed tile adds a floor. */
@Composable
private fun FloorRail(floors: List<LocNode>, current: String?, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.width(56.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        floors.forEachIndexed { i, f ->
            val on = f.id == current
            Column(
                Modifier.fillMaxWidth().height(54.dp).clip(r(14))
                    .background(if (on) C.Yellow else C.Surface).alpha(if (f.active) 1f else 0.5f)
                    .then(if (on) Modifier else Modifier.border(1.dp, C.Border, r(14)))
                    .clickable { onPick(f.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Txt(floorTag(f, i), 15, 800, if (on) C.OnYellow else C.Text)
                Txt(f.kids.orEmpty().size.toString(), 10, 600, if (on) C.OnYellow.copy(alpha = 0.75f) else C.Muted)
            }
        }
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(r(14)).dashedBorder(C.Border2, 1.dp, 14.dp).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 18.dp, C.Soft, 2.2f) }
    }
}

/** A floor's short label: its code when that is short (e.g. "L2"), else F1, F2… in list order. */
@Composable
private fun floorTag(f: LocNode, i: Int) = f.code.trim().takeIf { it.isNotEmpty() && it.length <= 3 } ?: stringResource(R.string.cfgt_floor_tag, i + 1)

/** One location on the chosen floor; the line between rows starts at the text, after the icon. */
@Composable
private fun LocationRow(l: LocNode, picked: Boolean, first: Boolean, onOpen: () -> Unit, onTick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (picked) C.PickedRow else Color.Transparent).clickable(onClick = onOpen).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
            LineIcon(Icons.Pin, 19.dp, C.Yellow, 2f)
        }
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).then(if (first) Modifier else Modifier.topBorder(C.Border)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(l.name, 14, 600, maxLines = 2)
                Txt(listOf(l.type, l.code).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { stringResource(R.string.cfgt_no_type) }, 12, 400, C.Muted)
            }
            Box(Modifier.clip(r(8)).clickable(onClick = onTick).padding(10.dp)) { CheckBox(picked) }
        }
    }
}

/** Where a search result lives, so tapping it can show it. */
private class SearchHit(val icon: Ico, val title: String, val sub: String, val building: String, val floor: String?, val location: String?)

/** Buildings, floors and locations whose name (or a location's type or code) contains [query]. */
@Composable
private fun SearchHits(tree: List<LocNode>, query: String, onPick: (SearchHit) -> Unit) {
    // The hits are worked out once per query, outside composition, so they read the strings from here.
    val res = LocalContext.current.resources
    val hits = remember(tree, query) {
        fun has(vararg s: String) = s.any { it.contains(query, ignoreCase = true) }
        buildList {
            tree.forEach { b ->
                val floors = b.kids.orEmpty().size
                if (has(b.name)) add(SearchHit(Icons.Building, b.name, res.getQuantityString(R.plurals.pi_count_floors, floors, floors), b.id, null, null))
                b.kids.orEmpty().forEach { f ->
                    if (has(f.name, f.code)) add(SearchHit(Icons.Floor, f.name, res.getString(R.string.cfgt_in_place, b.name), b.id, f.id, null))
                    f.kids.orEmpty().forEach { l ->
                        if (has(l.name, l.type, l.code)) add(SearchHit(Icons.Pin, l.name, "${f.name} · ${b.name}", b.id, f.id, l.id))
                    }
                }
            }
        }
    }
    if (hits.isEmpty()) {
        EmptyNote(stringResource(R.string.pi_no_match, query))
        return
    }
    Column(Modifier.fillMaxWidth().clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16))) {
        hits.forEachIndexed { i, h ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(h) }.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
                    LineIcon(h.icon, 19.dp, C.Yellow, 2f)
                }
                Row(
                    Modifier.weight(1f).heightIn(min = 56.dp).then(if (i == 0) Modifier else Modifier.topBorder(C.Border)).padding(end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Txt(h.title, 14, 600, maxLines = 2)
                        Txt(h.sub, 12, 400, C.Muted)
                    }
                    LineIcon(Icons.ChevronRight, 14.dp, C.Dim, 2.6f)
                }
            }
        }
    }
}

/** One choice in the floating +'s list. */
private class DialAct(val title: String, val sub: String, val icon: Ico, val tint: Color, val go: () -> Unit)

/**
 * The floating + and search buttons. + opens Location / Floor / Building (and turns into ×, moving into the
 * search button's place); search turns into a bar along the bottom that stays just above the keyboard.
 */
@Composable
private fun BuildingFabs(
    dial: Boolean,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleDial: () -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    options: List<DialAct>,
) {
    // This area ends above the tab bar, so lift the search bar only by the part of the keyboard that covers it.
    val density = LocalDensity.current
    var below by remember { mutableIntStateOf(0) }
    val ime = WindowInsets.ime.getBottom(density)
    val lift = with(density) { (ime - below).coerceAtLeast(0).toDp() }
    val turn by animateFloatAsState(if (dial) 45f else 0f, label = "dial")
    val fabBottom by animateDpAsState(if (dial) 14.dp else 82.dp, label = "fab")

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { c ->
            below = (c.findRootCoordinates().size.height - c.boundsInRoot().bottom).toInt().coerceAtLeast(0)
        },
    ) {
        if (dial) Box(Modifier.fillMaxSize().background(Color(0xA6000000)).noRippleClick(onToggleDial).blockSwipe())

        AnimatedVisibility(
            dial,
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 82.dp),
            enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { o -> DialOption(o.title, o.sub, o.icon, o.tint, o.go) }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = fabBottom + if (searching) lift else 0.dp)
                .size(54.dp).fabShadow().clip(CircleShape).background(C.Yellow).clickable(onClick = onToggleDial),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 26.dp, C.OnYellow, 2.6f, Modifier.rotate(turn)) }

        when {
            searching -> {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp + lift)
                        .fillMaxWidth().height(54.dp).fabShadow(Capsule).clip(Capsule).background(C.Surface)
                        .border(1.5.dp, C.Yellow, Capsule).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineIcon(Icons.Search, 20.dp, C.Yellow, 2.2f)
                    PlainField(query, onQuery, stringResource(R.string.cfgt_search_places), Modifier.weight(1f).focusRequester(focus))
                    RoundButton(42.dp, C.Btn, onCloseSearch) { LineIcon(Icons.Close, 16.dp, C.Text, 2.6f) }
                }
            }
            !dial -> Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(54.dp).fabShadow()
                    .clip(CircleShape).background(C.Btn).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Search, 22.dp, C.Text, 2.2f) }
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
                Txt(value.ifEmpty { placeholder }, 13, 400, if (value.isEmpty()) C.Muted else C.Text, Modifier.weight(1f))
                LineIcon(Icons.ChevronDown, 18.dp, C.Soft, 2.4f)
            }
            DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp), containerColor = C.Surface) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Txt(o, 14, if (o == value) 700 else 500, if (o == value) C.Yellow else C.Text) },
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
                    android.app.TimePickerDialog(context, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, android.text.format.DateFormat.is24HourFormat(context)).show()
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shown on the phone's 12/24-hour clock; kept as HH:mm.
            Txt(if (value.isEmpty()) placeholder else storedTimeText(value), 13, 400, if (value.isEmpty()) C.Muted else C.Text, Modifier.weight(1f))
            LineIcon(Icons.Clock, 20.dp, C.Soft, 2f)
        }
    }
}

/** A field label; a trailing * is drawn red (mandatory). */
@Composable
private fun FieldLabel(label: String) {
    val req = label.endsWith("*")
    Row {
        Txt(label.removeSuffix("*").trimEnd(), 11, 600, C.Soft)
        if (req) Txt(" *", 11, 700, C.Red)
    }
}

private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private fun phoneOk(p: String) = Regex("^[+0-9 ()-]+$").matches(p) && p.count(Char::isDigit) >= 6

/** Add or edit a building: details, address, primary contact, notes and site access. */
@Composable
private fun EditBuildingSheet(b: LocNode?, onClose: () -> Unit, onSave: (String, BuildingInfo, Boolean) -> Unit) {
    val i0 = b?.info ?: BuildingInfo()
    var name by remember { mutableStateOf(b?.name.orEmpty()) }
    var info by remember { mutableStateOf(i0) }
    var visible by remember { mutableStateOf(b?.active ?: true) }
    val emailBad = info.email.isNotBlank() && !EMAIL_RE.matches(info.email.trim())
    val phoneBad = info.phone.isNotBlank() && !phoneOk(info.phone.trim())
    val ok = name.isNotBlank() && info.type.isNotBlank() && !emailBad && !phoneBad
    GuardedClose(name.trim() != b?.name.orEmpty().trim() || info != i0 || visible != (b?.active ?: true), onClose) { onClose ->
    BottomSheet(onClose) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (b == null) stringResource(R.string.cfgt_add_building) else stringResource(R.string.access_edit_group, b.name), 16, 700, modifier = Modifier.weight(1f), maxLines = 2)
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetSection(stringResource(R.string.cfgt_building_details))
            LabeledInput(stringResource(R.string.cfgt_building_name), name, { name = it.take(MAX_PLACE_NAME) }, stringResource(R.string.cfgt_building_name_hint))
            PickField(stringResource(R.string.cfgt_building_type), info.type, stringResource(R.string.property_select_type), BUILDING_TYPES) { info = info.copy(type = it) }
            LabeledInput(stringResource(R.string.field_address_line1), info.address1, { info = info.copy(address1 = it) }, stringResource(R.string.field_address_line1_hint))
            LabeledInput(stringResource(R.string.field_address_line2), info.address2, { info = info.copy(address2 = it) }, stringResource(R.string.field_address_line2_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickField(stringResource(R.string.field_country), info.country, stringResource(R.string.cfgt_select_country), COUNTRIES, Modifier.weight(1f)) { info = info.copy(country = it) }
                Box(Modifier.weight(1f)) { LabeledInput(stringResource(R.string.field_state), info.state, { info = info.copy(state = it) }, stringResource(R.string.property_enter_state)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickField(stringResource(R.string.field_region), info.region, stringResource(R.string.property_select_region), REGIONS, Modifier.weight(1f)) { info = info.copy(region = it) }
                Box(Modifier.weight(1f)) { LabeledInput(stringResource(R.string.field_city), info.city, { info = info.copy(city = it) }, stringResource(R.string.property_enter_city)) }
            }

            Spacer(Modifier.height(4.dp))
            SheetSection(stringResource(R.string.property_group_contact))
            LabeledInput(stringResource(R.string.field_name), info.contactName, { info = info.copy(contactName = it) }, stringResource(R.string.field_contact_name_hint))
            LabeledInput(stringResource(R.string.field_email), info.email, { info = info.copy(email = it) }, stringResource(R.string.field_email_hint))
            if (emailBad) Txt(stringResource(R.string.cfgt_email_invalid), 11, 500, C.Red, maxLines = Int.MAX_VALUE)
            LabeledInput(stringResource(R.string.property_field_phone), info.phone, { info = info.copy(phone = it) }, stringResource(R.string.field_phone_hint))
            if (phoneBad) Txt(stringResource(R.string.cfgt_phone_invalid), 11, 500, C.Red, maxLines = Int.MAX_VALUE)

            Spacer(Modifier.height(4.dp))
            SheetSection(stringResource(R.string.property_group_notes))
            LabeledInput(stringResource(R.string.property_group_notes), info.notes, { info = info.copy(notes = it) }, stringResource(R.string.property_notes_hint), singleLine = false, minLines = 3)

            Spacer(Modifier.height(4.dp))
            SheetSection(stringResource(R.string.property_group_availability))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(stringResource(R.string.field_start_time), info.startTime, stringResource(R.string.cfgt_select_start_time), Modifier.weight(1f)) { info = info.copy(startTime = it) }
                TimeField(stringResource(R.string.field_end_time), info.endTime, stringResource(R.string.cfgt_select_end_time), Modifier.weight(1f)) { info = info.copy(endTime = it) }
            }
            LabeledInput(stringResource(R.string.field_access_code), info.accessCode, { info = info.copy(accessCode = it) }, stringResource(R.string.field_access_code_hint))
            if (b != null) {
                Spacer(Modifier.height(4.dp))
                SheetSection(stringResource(R.string.cfg_place_visibility))
                CfgCard { ToggleRow(stringResource(R.string.cfgt_visible), stringResource(R.string.cfg_building_visible_hint), visible, enabled = true) { visible = it } }
            }
        }
        SheetButtons(if (b == null) stringResource(R.string.cfgt_add_building) else stringResource(R.string.cfg_save), ok, onClose) { onSave(name, info, visible) }
    }
    }
}

/** A building's floors: type and code for each, their order (top first), and removing floors. */
@Composable
private fun EditFloorsSheet(b: LocNode, onClose: () -> Unit, onSave: (List<LocNode>) -> Unit) {
    var floors by remember(b.id) { mutableStateOf(b.kids.orEmpty()) }
    val removed = b.kids.orEmpty().filter { o -> floors.none { it.id == o.id } }
    fun edit(id: String, f: (LocNode) -> LocNode) { floors = floors.map { if (it.id == id) f(it) else it } }
    GuardedClose(floors != b.kids.orEmpty(), onClose) { onClose ->
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.cfgt_edit_floors), stringResource(R.string.cfgt_edit_floors_sub, b.name), onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (floors.isEmpty()) EmptyNote(stringResource(R.string.cfgt_no_floors_left))
            floors.forEachIndexed { i, f ->
                var menu by remember(f.id) { mutableStateOf(false) }
                CfgCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LineIcon(Icons.Floor, 19.dp, C.Yellow, 2.1f)
                            PlainField(f.name, { v -> edit(f.id) { it.copy(name = v) } }, stringResource(R.string.cfgt_floor_name), Modifier.weight(1f), size = 13, weight = 700)
                            val locations = f.kids.orEmpty().size
                            Txt(pluralStringResource(R.plurals.pi_count_locations, locations, locations), 11, 400, C.Muted)
                            Box {
                                RoundButton(36.dp, Color.Transparent, { menu = true }) { LineIcon(Icons.Dots, 18.dp, C.Soft) }
                                IconMenu(menu, { menu = false }, listOf(
                                    MenuAct(Icons.ArrowUp, stringResource(R.string.cfgt_move_up), enabled = i > 0) {
                                        floors = floors.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                                    },
                                    MenuAct(Icons.ArrowDown, stringResource(R.string.cfgt_move_down), enabled = i < floors.size - 1) {
                                        floors = floors.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                                    },
                                    MenuAct(Icons.Trash, stringResource(R.string.media_remove), danger = true) { floors = floors.filter { it.id != f.id } },
                                ))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallInput(f.type, { v -> edit(f.id) { it.copy(type = v.take(MAX_PLACE_TYPE)) } }, stringResource(R.string.cfg_add_type), Modifier.weight(1f))
                            SmallInput(f.code, { v -> edit(f.id) { it.copy(code = v.take(MAX_PLACE_CODE)) } }, stringResource(R.string.cfg_add_code), Modifier.weight(1f))
                        }
                    }
                    // Hidden floors can't be picked by field workers (the server's isActive).
                    ToggleRow(stringResource(R.string.cfgt_visible), stringResource(R.string.cfg_floor_visible_hint), f.active, enabled = true, divider = true) { on ->
                        edit(f.id) { it.copy(active = on) }
                    }
                }
            }
            if (removed.isNotEmpty()) {
                // The server deletes a floor's locations with it; assets there stay, without a floor or location.
                val note = stringResource(R.string.cfg_floors_removed_note, removed.joinToString { it.name })
                Txt(
                    if (removed.any { it.linkedToVdms }) stringResource(R.string.cfg_delete_vdms_note, note) else note,
                    12, 500, C.Orange, maxLines = Int.MAX_VALUE,
                )
            }
        }
        SheetButtons(stringResource(R.string.cfg_save), floors.all { it.name.isNotBlank() }, onClose) { onSave(floors) }
    }
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

/** What the add sheet adds: floors (to a building) or locations (to a floor). */
private enum class PlaceSheetKind { FLOOR, LOCATION }

/** One row of the add sheet: a name with its own type and code. */
private class PlaceDraft(name: String = "", type: String = "", code: String = "") {
    var name by mutableStateOf(name)
    var type by mutableStateOf(type)
    var code by mutableStateOf(code)
    val blank get() = name.isBlank() && type.isBlank() && code.isBlank()
}

/**
 * Add floor / Add location, as the web's: a name, type and code; with Bulk addition, several rows, each with its own
 * name, type and code (+ Add row).
 */
@Composable
private fun AddPlacesSheet(kind: PlaceSheetKind, onClose: () -> Unit, onAdd: (List<NewLocation>) -> Unit) {
    var bulk by remember { mutableStateOf(false) }
    val rows = remember { androidx.compose.runtime.mutableStateListOf(PlaceDraft()) }
    val shown = if (bulk) rows else rows.take(1)
    val ready = shown.filter { it.name.isNotBlank() }.map { NewLocation(it.name.trim(), it.type.trim(), it.code.trim()) }
    val floor = kind == PlaceSheetKind.FLOOR
    GuardedClose(rows.any { !it.blank }, onClose) { onClose ->
    BottomSheet(onClose) {
        SheetHeader(
            stringResource(if (floor) R.string.cfg_add_floor_title else R.string.cfg_add_location_title),
            if (bulk) stringResource(R.string.cfg_add_bulk_hint) else null, onClose,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clip(r(8)).clickable { bulk = !bulk }.padding(vertical = 4.dp).testTag("place-bulk"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CheckBox(bulk)
                Txt(stringResource(R.string.cfg_add_bulk), 12, 600)
            }
            shown.forEachIndexed { i, row ->
                Column(
                    if (bulk) Modifier.fillMaxWidth().clip(r(10)).border(1.dp, C.Border, r(10)).padding(8.dp) else Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (bulk) Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt(stringResource(R.string.cfg_add_row_n, i + 1), 12, 700, C.Muted, Modifier.weight(1f))
                        if (rows.size > 1) {
                            val cd = stringResource(R.string.cfg_add_row_remove, i + 1)
                            Box(
                                Modifier.size(40.dp).clip(r(10)).clickable { rows.removeAt(i) }.semantics { contentDescription = cd },
                                contentAlignment = Alignment.Center,
                            ) { LineIcon(Icons.Trash, 16.dp, C.Red, 2.2f) }
                        }
                    }
                    LabeledInput(
                        stringResource(R.string.cfg_add_name), row.name, { row.name = it },
                        stringResource(if (floor) R.string.cfg_add_floor_name_hint else R.string.cfg_add_location_name_hint),
                    )
                    LabeledInput(stringResource(R.string.cfg_add_type), row.type, { row.type = it.take(MAX_PLACE_TYPE) },
                        stringResource(if (floor) R.string.cfg_add_floor_type_hint else R.string.cfg_add_location_type_hint))
                    LabeledInput(stringResource(R.string.cfg_add_code), row.code, { row.code = it.take(MAX_PLACE_CODE) }, stringResource(R.string.cfg_add_code_hint))
                }
            }
            if (bulk) Pill(stringResource(R.string.cfg_add_row), { rows.add(PlaceDraft()) }, Modifier.testTag("place-add-row"), height = 40.dp, hPad = 16.dp)
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 10.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(stringResource(R.string.cfg_cancel), onClose, height = 40.dp, hPad = 16.dp)
            Pill(
                when {
                    ready.size > 1 -> pluralStringResource(if (floor) R.plurals.cfg_add_floors_n else R.plurals.cfg_add_locations_n, ready.size, ready.size)
                    floor -> stringResource(R.string.cfg_add_floor_title)
                    else -> stringResource(R.string.cfg_add_location_title)
                },
                { if (ready.isNotEmpty()) onAdd(ready) },
                Modifier.weight(1f).alpha(if (ready.isNotEmpty()) 1f else 0.4f).testTag("place-add"),
                bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
            )
        }
    }
    }
}

@Composable
private fun LocationSheet(l: LocNode, onClose: () -> Unit, onSave: (String, String, String) -> Unit, onDelete: () -> Unit) {
    var editing by remember(l.id) { mutableStateOf(false) }
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
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(l.name, 14, 700, modifier = Modifier.weight(1f))
            if (!editing) RoundButton(36.dp, C.Btn, { editing = true }) { LineIcon(Icons.Pencil, 16.dp, C.Yellow, 2.2f) }
            Spacer(Modifier.width(8.dp))
            CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
        }
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                LabeledInput(stringResource(R.string.cfg_add_name), name, { name = it.take(MAX_PLACE_NAME) }, stringResource(R.string.cfgt_location_name))
                LabeledInput(stringResource(R.string.cfg_add_type), type, { type = it.take(MAX_PLACE_TYPE) }, stringResource(R.string.cfgt_location_type_hint))
                LabeledInput(stringResource(R.string.cfg_add_code), code, { code = it.take(MAX_PLACE_CODE) }, stringResource(R.string.cfg_add_code_hint))
            } else {
                listOf(
                    stringResource(R.string.field_name) to l.name, stringResource(R.string.cfg_add_type) to l.type, stringResource(R.string.cfg_add_code) to l.code,
                ).forEach { (k, v) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(k, 11, 600, C.Muted)
                        Txt(v.ifBlank { "–" }, 12, 600, maxLines = Int.MAX_VALUE)
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
                Pill(stringResource(R.string.cfg_cancel), { if (changed) askCancel = true else stopEditing() }, height = 40.dp, hPad = 16.dp)
                Pill(
                    stringResource(R.string.cfg_save), { if (name.isNotBlank()) { onSave(name, type, code); editing = false } },
                    Modifier.weight(1f).alpha(if (name.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
                )
            } else {
                RoundButton(40.dp, Color(0xFF2A1919), onDelete) { LineIcon(Icons.Trash, 18.dp, C.Red, 2.2f) }
                Spacer(Modifier.weight(1f))
                Pill(stringResource(R.string.action_done), onClose, height = 40.dp, hPad = 20.dp)
            }
        }
    }
    if (askCancel) {
        DiscardChangesDialog(onKeep = { askCancel = false }, onDiscard = { askCancel = false; stopEditing() })
    }
    }
}

@Composable
private fun NameDialog(a: NameAsk, onClose: () -> Unit) {
    var v by remember(a) { mutableStateOf(a.initial) }
    Box(Modifier.fillMaxSize()) { GuardedClose(v.trim() != a.initial.trim(), onClose) { close -> NameDialogBody(a, v, { v = it }, close, onClose) } }
}

@Composable
private fun NameDialogBody(a: NameAsk, v: String, setV: (String) -> Unit, close: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(close).blockSwipe().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(r(18)).background(C.Surface).border(1.dp, C.Border, r(18)).noRippleClick { }.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(a.title, 14, 700)
            PlainField(
                v, setV, a.placeholder,
                Modifier.fillMaxWidth().clip(r(10)).background(C.Bg).border(1.5.dp, if (v.isNotBlank()) C.Yellow else C.Border, r(10)).padding(12.dp),
                size = 13,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.cfg_cancel), close, Modifier.weight(1f), height = 40.dp)
                Pill(
                    stringResource(R.string.cfg_save), { if (v.isNotBlank()) { a.onSave(v.trim()); onSaved() } },
                    Modifier.weight(1f).alpha(if (v.isNotBlank()) 1f else 0.4f),
                    bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Asset fields

@Composable
private fun AssetFields(fv: FieldsConfigViewModel, cfg: ConfigViewModel, propName: String, adding: Boolean, setAdding: (Boolean) -> Unit) {
    var editFor by remember { mutableStateOf<String?>(null) }
    var checksFor by remember { mutableStateOf<String?>(null) }
    var dial by remember { mutableStateOf(false) }
    // Fields read from a spreadsheet, waiting in the Add field sheet.
    var drafts by remember { mutableStateOf<List<FieldCfg>>(emptyList()) }
    val ready = fv.load == LoadState.READY
    val photos = cfg.photos
    val photosReady = photos.load == LoadState.READY
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(top = 10.dp)) {
        Txt(stringResource(R.string.cfg_fields_intro, propName), 12, 400, C.Muted, Modifier.padding(horizontal = 6.dp), maxLines = Int.MAX_VALUE)
        if (!ready) OnlineNote(fv.load, fv.failure, fv::retry)
        // The fields as one table, as the web's and the iOS app's: name, type, mandatory and checks.
        TableCard(Modifier.padding(top = 12.dp)) {
            TableHeader {
                HeaderText(stringResource(R.string.cfg_col_name), Modifier.weight(1f))
                HeaderText(stringResource(R.string.cfg_col_type), Modifier.width(TYPE_W))
                HeaderText(stringResource(R.string.cfg_col_mandatory), Modifier.width(MUST_W), TextAlign.Center)
                HeaderText(stringResource(R.string.cfg_col_checks), Modifier.width(CHECKS_W), TextAlign.Center)
            }
            fv.fields.forEach { f ->
                FieldCfgRow(f, f.options.size, onToggleRequired = { fv.toggleRequired(f) }, onEdit = { editFor = f.key }, onChecks = { checksFor = f.key })
            }
        }

        // Photos: the property's photo rules at the server (nameplate, asset, label ID), as the web's table.
        TableCard(Modifier.padding(top = 14.dp)) {
            TableHeader {
                HeaderText(stringResource(R.string.cfg_col_photos), Modifier.weight(1f))
                HeaderText(stringResource(R.string.cfg_col_minimum), Modifier.width(STEPPER_W), TextAlign.Center)
                HeaderText(stringResource(R.string.cfg_col_maximum), Modifier.width(STEPPER_W), TextAlign.Center)
            }
            photos.rules.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().topBorder(C.Border).heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(r(8)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Camera, 16.dp, C.Yellow, 2.2f)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(stringResource(p.kind.nameRes()), 13, 700)
                        Txt(stringResource(p.kind.hintRes()), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    }
                    Stepper(
                        p.min, Modifier.width(STEPPER_W), photosReady,
                        downBlocked = cfg.photoBlock(p.kind, PhotoBound.MIN, up = false) != null,
                        upBlocked = cfg.photoBlock(p.kind, PhotoBound.MIN, up = true) != null,
                    ) { up -> cfg.stepPhoto(p.kind, PhotoBound.MIN, up) }
                    Stepper(
                        p.max, Modifier.width(STEPPER_W), photosReady,
                        downBlocked = cfg.photoBlock(p.kind, PhotoBound.MAX, up = false) != null,
                        upBlocked = cfg.photoBlock(p.kind, PhotoBound.MAX, up = true) != null,
                    ) { up -> cfg.stepPhoto(p.kind, PhotoBound.MAX, up) }
                }
            }
            // How many photos all kinds together allow (a bar, as the web's), or why the rules aren't there.
            Row(
                Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (photos.load == LoadState.READY) {
                    val share = (photos.totalMax.toFloat() / MAX_PHOTOS_PER_ASSET).coerceIn(0f, 1f)
                    Box(Modifier.width(56.dp).height(4.dp).clip(Capsule).background(C.Raised)) {
                        Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(Capsule).background(C.Yellow))
                    }
                }
                Txt(
                    when (photos.load) {
                        LoadState.LOADING -> stringResource(R.string.cfg_photos_loading)
                        LoadState.FAILED -> stringResource(R.string.cfg_photos_load_failed)
                        LoadState.READY -> stringResource(R.string.cfg_photos_usage, photos.totalMax, MAX_PHOTOS_PER_ASSET)
                    },
                    11, 400, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE,
                )
            }
        }
        Spacer(Modifier.height(160.dp)) // room for the floating +, as on the Assets list
    }
    // The floating + opens two ways to add fields. Drawn under the sheets and hidden while one is open.
    if (ready && !adding && editFor == null && checksFor == null && fv.importing == null) {
        AddDial(
            dial, onToggle = { dial = !dial },
            onExcel = { dial = false; fv.openImport() },
            onNew = { dial = false; drafts = emptyList(); setAdding(true) },
        )
    }
    fv.importing?.let { state ->
        ConfigImportSheet(
            state, fv::pickImport, fv::saveImportSample,
            onImport = { fv.runImport { list -> drafts = list; setAdding(true) } },
            onClose = fv::closeImport,
        )
    }
    if (adding) AddFieldsSheet(fv.fields, drafts, onClose = { setAdding(false); drafts = emptyList() }) { list ->
        fv.add(list) { setAdding(false); drafts = emptyList() }
    }
    editFor?.let { k -> fv.fields.firstOrNull { it.key == k } }?.let { f ->
        FieldSheet(
            f, draft = false, options = f.options.size, editableOptions = f.key !in FIXED_OPTIONS, taken = fv.fields,
            onClose = { editFor = null },
            onSubmit = { edited -> fv.save(f, edited) { editFor = null } },
            onDelete = if (f.custom) ({ fv.delete(f) { editFor = null } }) else null,
        )
    }
    checksFor?.let { k -> fv.fields.firstOrNull { it.key == k } }?.let { f ->
        FieldChecksSheet(f, f.options.size, onClose = { checksFor = null }, onRemove = { fv.removeChecks(f) { checksFor = null } }) { c ->
            fv.setChecks(f, c) { checksFor = null }
        }
    }
}

/** Name and hint of each kind of photo, as the web shows them. */
private fun PhotoKind.nameRes() = when (this) {
    PhotoKind.NAMEPLATE -> R.string.cfg_photo_nameplate
    PhotoKind.ASSET -> R.string.cfg_photo_asset
    PhotoKind.LABEL_ID -> R.string.cfg_photo_label_id
}

private fun PhotoKind.hintRes() = when (this) {
    PhotoKind.NAMEPLATE -> R.string.cfg_photo_nameplate_hint
    PhotoKind.ASSET -> R.string.cfg_photo_asset_hint
    PhotoKind.LABEL_ID -> R.string.cfg_photo_label_id_hint
}

private val TYPE_W = 92.dp
private val MUST_W = 78.dp
private val CHECKS_W = 66.dp
private val STEPPER_W = 92.dp

/** A bordered table card of the Asset fields tab: a header row, then rows split by lines. */
@Composable
private fun TableCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)), content = content)
}

@Composable
private fun TableHeader(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** A column heading: small capitals, muted. */
@Composable
private fun HeaderText(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) =
    Txt(text.uppercase(Locale.getDefault()), 10, 700, C.Muted, modifier, spacing = 0.8f, align = align)

/** Dropdowns whose choices are fixed lists (classification, JACS), so they have no options to edit. */
private val FIXED_OPTIONS = setOf("sys", "typ", "sub", "jacs")

/** One line on what validation checks in [f], for the Edit sheet. */
@Composable
private fun checksSummary(f: FieldCfg): String {
    val parts = mutableListOf<String>()
    if (f.required || f.flagEmpty) parts += stringResource(R.string.cfgt_flagged_when_empty)
    if (f.rule != ValueRule.NONE) parts += stringResource(f.rule.labelRes(f.kind))
    if (f.kind == FieldKind.NUMBER && f.wholeNumber) parts += stringResource(R.string.cfg_checks_whole)
    // Numbers are a range of values; anything else is a range of lengths, in characters.
    val number = f.kind == FieldKind.NUMBER
    val lo = f.min.takeIf { it.isNotBlank() }
    val hi = f.max.takeIf { it.isNotBlank() }
    when {
        lo != null && hi != null -> parts += stringResource(if (number) R.string.cfgt_checks_between else R.string.cfgt_checks_between_chars, lo, hi)
        lo != null -> parts += stringResource(if (number) R.string.cfgt_checks_at_least else R.string.cfgt_checks_at_least_chars, lo)
        hi != null -> parts += stringResource(if (number) R.string.cfgt_checks_at_most else R.string.cfgt_checks_at_most_chars, hi)
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.cfgt_checks_nothing) }
}

/** What a rule is called for a field of [kind] (the server's text formats, and past / future for dates). */
private fun ValueRule.labelRes(kind: FieldKind): Int = when (this) {
    ValueRule.NONE -> if (kind == FieldKind.DATE) R.string.cfg_rule_any_date else R.string.cfg_rule_any_text
    ValueRule.LETTERS -> R.string.cfg_rule_letters
    ValueRule.DIGITS -> R.string.cfg_rule_digits
    ValueRule.ALNUM -> R.string.cfg_rule_alnum
    ValueRule.EMAIL -> R.string.cfg_rule_email
    ValueRule.PHONE -> R.string.cfg_rule_phone
    ValueRule.UPPERCASE -> R.string.cfg_rule_uppercase
    ValueRule.PATTERN -> R.string.cfg_rule_pattern
    ValueRule.PAST -> R.string.cfg_rule_past
    ValueRule.FUTURE -> R.string.cfg_rule_future
}

@Composable
private fun FieldCfgRow(
    f: FieldCfg,
    options: Int,
    onToggleRequired: () -> Unit,
    onEdit: () -> Unit,
    onChecks: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).clickable(onClick = onEdit).heightIn(min = 56.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name (a lock on built-in fields), and how many options a dropdown has.
        Column(Modifier.weight(1f).padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Txt(f.label, 13, 700, modifier = Modifier.weight(1f, fill = false), maxLines = 2)
                if (f.locked) LineIcon(Icons.Lock, 12.dp, C.Dim, 2.4f)
            }
            if (f.kind == FieldKind.DROPDOWN) Txt(pluralStringResource(R.plurals.cfg_field_options, options, options), 11, 400, C.Muted, maxLines = 1)
        }
        // Type: its icon in yellow and its name.
        Row(Modifier.width(TYPE_W), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LineIcon(f.kind.icon(), 13.dp, C.Yellow, 2.4f)
            Txt(f.kind.shown, 12, 600, C.Soft, maxLines = 1)
        }
        // Mandatory: the tick.
        Box(
            Modifier.size(MUST_W, 44.dp).clip(r(8)).clickable(enabled = !f.locked, onClick = onToggleRequired),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.alpha(if (f.locked) 0.4f else 1f)) { CheckBox(f.required) } }
        // Checks: what validation looks for beyond the kind; a yellow pill when set, an outlined Add when not.
        Box(Modifier.width(CHECKS_W).heightIn(min = 48.dp).clickable(onClick = onChecks), contentAlignment = Alignment.Center) {
            val tag = when {
                f.hasFormat -> R.string.cfg_checks_format
                f.flagEmpty && !f.required -> R.string.cfg_checks_empty
                else -> null
            }
            Box(
                Modifier.clip(Capsule)
                    .then(if (tag != null) Modifier.background(C.Yellow) else Modifier.border(1.dp, C.Border2, Capsule))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) { Txt(stringResource(tag ?: R.string.cfg_checks_add), 11, 700, if (tag != null) C.OnYellow else C.Soft, maxLines = 1) }
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
                DialOption(stringResource(R.string.cfgt_import_excel), stringResource(R.string.cfgt_import_excel_hint), Icons.Table, C.Green, onExcel)
                DialOption(stringResource(R.string.cfgt_new_field), stringResource(R.string.cfgt_new_field_hint), Icons.FileDoc, C.Text, onNew)
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
            Txt(title, 13, 700)
            Txt(sub, 11, 400, C.Muted)
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
            Txt(title, 15, 700)
            sub?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
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
    GuardedClose(drafts.isNotEmpty(), onClose) { onClose ->
    BottomSheet(onClose) {
        TightSheetHeader(stringResource(R.string.own_field_title), stringResource(R.string.cfgt_add_field_hint), onClose)
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
                SheetSection(stringResource(R.string.cfgt_selected_fields))
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
                                Txt(d.label, 12, 700)
                                Txt(if (d.required) stringResource(R.string.cfgt_kind_mandatory, d.kind.shown) else d.kind.shown, 11, 400, C.Muted)
                            }
                            LineIcon(Icons.ChevronRight, 15.dp, C.Muted, 2.4f)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp)) {
            Pill(
                if (drafts.isEmpty()) stringResource(R.string.cfg_save) else pluralStringResource(R.plurals.cfgt_save_fields, drafts.size, drafts.size), { if (drafts.isNotEmpty()) onSave(drafts) },
                Modifier.fillMaxWidth().alpha(if (drafts.isNotEmpty()) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
            )
        }
    }
    }
    editing?.let { d ->
        val isNew = drafts.none { it.key == d.key }
        FieldSheet(
            d, draft = true, options = d.options.size, editableOptions = true, taken = existing + drafts,
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
        Txt(k.shown, 12, 600, modifier = Modifier.weight(1f))
        LineIcon(Icons.PlusSmall, 16.dp, C.Yellow, 2.6f)
    }
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
    /** Fields whose names this one can't reuse (compared without case or spaces, as the web does). */
    taken: List<FieldCfg> = emptyList(),
) {
    var cur by remember(f.key) { mutableStateOf(f) }
    var opt by remember(f.key) { mutableStateOf("") }
    var optProblem by remember(f.key) { mutableStateOf<OptionProblem?>(null) }
    var checks by remember(f.key) { mutableStateOf(false) }
    var confirmDelete by remember(f.key) { mutableStateOf(false) }
    // The option whose colour is being picked (null: none).
    var colorFor by remember(f.key) { mutableStateOf<String?>(null) }
    val ownOptions = f.kind in OPTION_KINDS && editableOptions
    val duplicate = nameTaken(taken, cur.label, f.key)
    val ok = cur.label.isNotBlank() && !duplicate && (!ownOptions || cur.options.isNotEmpty())
    fun addOption() {
        // Option names are trimmed, can't be blank or hold / , { }, and can't repeat (the web's rules).
        optProblem = optionProblem(opt, cur.options)
        if (optProblem == null) {
            cur = cur.copy(options = cur.options + opt.trim())
            opt = ""
        }
    }
    fun removeOption(o: String) {
        val left = cur.options - o
        // Removing the default's option clears the default (multiple checkboxes: just that name).
        val default = if (cur.kind == FieldKind.MULTI) cur.defaultValue.split(',').filter { it.isNotBlank() && it != o }.joinToString(",")
        else if (cur.defaultValue == o || left.isEmpty()) "" else cur.defaultValue
        cur = cur.copy(options = left, defaultValue = default)
    }
    GuardedClose(cur != f || opt.isNotBlank(), onClose) { onClose ->
    BottomSheet(onClose) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(34.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                LineIcon(f.kind.icon(), 19.dp, C.Yellow, 2.2f)
            }
            Txt(if (draft) f.kind.shown else stringResource(R.string.access_edit_group, f.label), 16, 700, modifier = Modifier.weight(1f), maxLines = 2)
            CloseButton(onClose, size = 40.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledInput(stringResource(R.string.cfg_add_name), cur.label, { cur = cur.copy(label = it) }, stringResource(R.string.own_field_name_hint))
            if (duplicate) Txt(stringResource(R.string.cfg_field_exists), 11, 500, C.Red, maxLines = Int.MAX_VALUE)
            if (ownOptions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(stringResource(R.string.cfgt_options_label))
                if (cur.options.isNotEmpty()) CfgCard {
                    cur.options.forEachIndexed { i, o ->
                        Row(
                            Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                                .padding(start = 10.dp, end = 2.dp, top = 0.dp, bottom = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The option's colour (as the web's picker); tap to change it.
                            val colour = cur.optionColors[o] ?: DEFAULT_OPTION_COLOR
                            val colourCd = stringResource(R.string.cfg_option_color_cd, o)
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { colorFor = o }.semantics { contentDescription = colourCd }.testTag("option-color-$i"),
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(18.dp).clip(CircleShape).background(hexColor(colour)).border(1.dp, C.Border2, CircleShape)) }
                            Txt(o, 12, 600, modifier = Modifier.weight(1f))
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { removeOption(o) }, contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Close, 14.dp, C.Muted, 2.4f)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12)).padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlainField(opt, { opt = it; optProblem = null }, stringResource(R.string.cfgt_add_option), Modifier.weight(1f), size = 13)
                    Box(Modifier.size(34.dp).clip(CircleShape).background(C.Yellow).clickable(onClick = ::addOption), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.PlusSmall, 18.dp, C.OnYellow, 2.8f)
                    }
                }
                optProblem?.let {
                    Txt(stringResource(if (it == OptionProblem.DUPLICATE) R.string.cfg_option_duplicate else R.string.cfg_option_invalid), 11, 500, C.Red, maxLines = Int.MAX_VALUE)
                }
            }
            DefaultValueInput(cur, if (ownOptions) cur.options else f.options) { cur = cur.copy(defaultValue = it) }
            LabeledInput(stringResource(R.string.cfgt_tooltip), cur.tooltip, { cur = cur.copy(tooltip = it.take(MAX_TOOLTIP_LENGTH)) }, stringResource(R.string.cfgt_tooltip_hint), singleLine = false, minLines = 3)
            CfgCard {
                ToggleRow(stringResource(R.string.cfg_col_mandatory), stringResource(R.string.cfgt_mandatory_hint), cur.required, enabled = !f.locked) {
                    cur = cur.copy(required = it, visible = cur.visible || it)
                }
            }
            CfgCard { LinkRow(Icons.ShieldCheck, stringResource(R.string.cfg_col_checks), checksSummary(cur), { checks = true }) }
            if (onDelete != null) Row(
                Modifier.fillMaxWidth().height(42.dp).clip(r(14)).border(1.dp, C.Red.copy(alpha = 0.7f), r(14))
                    .clickable { confirmDelete = !draft; if (draft) onDelete() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Icons.Trash, 17.dp, C.Red, 2.2f)
                Txt(stringResource(R.string.cfgt_delete_field), 12, 700, C.Red)
            }
        }
        SheetButtons(stringResource(if (draft) R.string.action_submit else R.string.cfg_save), ok, onClose) { onSubmit(cur) }
    }
    }
    if (checks) {
        FieldChecksSheet(
            cur, if (ownOptions) cur.options.size else options, onClose = { checks = false },
            onRemove = { cur = cur.withChecks(FieldChecks()); checks = false },
        ) { c -> cur = cur.withChecks(c); checks = false }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red,
            title = stringResource(R.string.cfg_field_delete_title, f.label), body = stringResource(R.string.cfg_field_delete_body, f.label),
            no = stringResource(R.string.cfg_cancel), yes = stringResource(R.string.action_delete), onNo = { confirmDelete = false }, onYes = { confirmDelete = false; onDelete() },
        )
    }
    colorFor?.let { o ->
        OptionColorSheet(o, cur.optionColors[o] ?: DEFAULT_OPTION_COLOR, onClose = { colorFor = null }) { c ->
            cur = cur.copy(optionColors = cur.optionColors + (o to c))
            colorFor = null
        }
    }
}

/** Picks the colour of option [option] from [OPTION_COLORS]; [current] is ticked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionColorSheet(option: String, current: String, onClose: () -> Unit, onPick: (String) -> Unit) {
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.cfg_option_color_title), option, onClose)
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OPTION_COLORS.forEach { c ->
                val on = c.equals(current, ignoreCase = true)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(hexColor(c)).border(if (on) 3.dp else 1.dp, if (on) C.White else C.Border2, CircleShape)
                        .clickable { onPick(c) }.semantics { contentDescription = c }.testTag("option-color-pick-$c"),
                    contentAlignment = Alignment.Center,
                ) { if (on) LineIcon(Icons.Check, 18.dp, C.White, 3f) }
            }
        }
    }
}

/** A "#rrggbb" colour as a Compose colour (anything else: the default option colour). */
private fun hexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }
    .getOrDefault(Color(android.graphics.Color.parseColor(DEFAULT_OPTION_COLOR)))

/**
 * The default value input for [f]'s kind; [choices] are its options when it has some. Values are stored as the web
 * stores them: checkbox true / false, date datepicker / today, time timepicker / now, links none.
 */
@Composable
private fun DefaultValueInput(f: FieldCfg, choices: List<String>, onChange: (String) -> Unit) {
    val v = f.defaultValue
    val none = stringResource(R.string.import_none)
    val label = stringResource(R.string.cfgt_default_value)
    when (f.kind) {
        FieldKind.CHECKBOX -> Row(
            Modifier.fillMaxWidth().clip(r(10)).clickable { onChange(if (v == CHECKED || v == ANSWER_YES) UNCHECKED else CHECKED) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CheckBox(v == CHECKED || v == ANSWER_YES)
            Column {
                Txt(stringResource(R.string.cfgt_default_status), 12, 700)
                Txt(stringResource(R.string.cfgt_default_status_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
            }
        }
        FieldKind.YESNO -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(label)
            // The stored values (the web's), shown as None / Yes / No in the phone's language.
            val vals = listOf("", "Yes", "No")
            Segmented(listOf(none, stringResource(R.string.detail_yes), stringResource(R.string.detail_no)), vals.indexOf(v).coerceAtLeast(0), { onChange(vals[it]) })
        }
        FieldKind.DATE -> DefaultChoice(
            v, listOf("" to none, DEFAULT_DATE_PICKER to stringResource(R.string.cfgt_default_date_picker), DEFAULT_TODAY to stringResource(R.string.cfgt_default_today)), onChange,
        )
        FieldKind.TIME -> DefaultChoice(
            v, listOf("" to none, DEFAULT_TIME_PICKER to stringResource(R.string.cfgt_default_time_picker), DEFAULT_NOW to stringResource(R.string.cfgt_default_now)), onChange,
        )
        FieldKind.DROPDOWN, FieldKind.MULTI ->
            PickField(
                label, v, stringResource(if (choices.isEmpty()) R.string.cfgt_add_options_first else R.string.cfgt_select),
                if (choices.isEmpty()) emptyList() else listOf(none) + choices,
            ) {
                onChange(if (it == none) "" else it)
            }
        FieldKind.NUMBER -> LabeledInput(label, v, { s -> onChange(s.filter { it.isDigit() || it == '.' || it == '-' }) }, stringResource(R.string.cfgt_prefilled_number))
        FieldKind.URL -> Unit // the web keeps no default for links
        // Image, video, voice memo and key value: nothing is typed in, so no default.
        FieldKind.IMAGE, FieldKind.VIDEO, FieldKind.VOICE_MEMO, FieldKind.KEY_VALUE -> Unit
        else -> LabeledInput(label, v, onChange, stringResource(R.string.cfgt_prefilled_text))
    }
}

/** A default picked from a short fixed list (stored value to its name); a stored value in another case still shows. */
@Composable
private fun DefaultChoice(value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    val shown = choices.firstOrNull { it.first.isNotEmpty() && it.first.equals(value, ignoreCase = true) }?.second ?: value
    PickField(stringResource(R.string.cfgt_default_value), shown, stringResource(R.string.cfgt_select), choices.map { it.second }) { name ->
        onChange(choices.firstOrNull { it.second == name }?.first.orEmpty())
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
            Txt(title, 13, 700)
            Txt(sub, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
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
            Txt(title, 13, 700)
            Txt(sub, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        LineIcon(Icons.ChevronRight, 16.dp, C.Muted, 2.4f)
    }
}

/** Yellow small-caps section title inside a sheet. */
@Composable
private fun SheetSection(text: String) = Txt(text.uppercase(), 11, 700, C.Yellow, spacing = 0.6f)

@Composable
private fun InfoBox(text: String) {
    Txt(text, 12, 400, C.Soft, Modifier.fillMaxWidth().clip(r(12)).background(C.Raised).padding(10.dp), maxLines = Int.MAX_VALUE)
}

/** The rules a field of [kind] can pick, in the web’s order (text formats, or past / future for dates). */
private fun rulesFor(kind: FieldKind): List<ValueRule> = when (kind) {
    in TEXT_KINDS -> listOf(
        ValueRule.NONE, ValueRule.LETTERS, ValueRule.DIGITS, ValueRule.ALNUM, ValueRule.EMAIL, ValueRule.PHONE, ValueRule.UPPERCASE, ValueRule.PATTERN,
    )
    FieldKind.DATE -> listOf(ValueRule.NONE, ValueRule.PAST, ValueRule.FUTURE)
    else -> emptyList()
}

/**
 * What validation looks for in a field: flag it when empty, and what its values must be (the server’s checks: a text
 * format or your own pattern and lengths, a number range and whole numbers, past or future dates). [onRemove] clears
 * every check (offered when the field has some).
 */
@Composable
private fun FieldChecksSheet(f: FieldCfg, options: Int, onClose: () -> Unit, onRemove: (() -> Unit)? = null, onSave: (FieldChecks) -> Unit) {
    var flag by remember(f.key) { mutableStateOf(f.flagEmpty) }
    var rule by remember(f.key) { mutableStateOf(f.rule) }
    var pattern by remember(f.key) { mutableStateOf(f.pattern) }
    var lo by remember(f.key) { mutableStateOf(f.min) }
    var hi by remember(f.key) { mutableStateOf(f.max) }
    var whole by remember(f.key) { mutableStateOf(f.wholeNumber) }
    var tried by remember(f.key) { mutableStateOf(false) }
    var ruleOpen by remember { mutableStateOf(false) }
    val rules = rulesFor(f.kind)
    val now = FieldChecks(flag, rule, pattern, lo, hi, whole)
    val problem = checksProblem(f.kind, now)
    // A missing pattern is pointed out once Save was tried; contradictions show as soon as they are typed.
    val shown = problem?.takeIf { it != ChecksProblem.PATTERN_MISSING || tried }
    val numeric: (String) -> String = { v -> v.filter { it.isDigit() || it == '.' || it == '-' } }
    val digits: (String) -> String = { v -> v.filter(Char::isDigit) }
    GuardedClose(now != f.checks, onClose) { onClose ->
    BottomSheet(onClose) {
        TightSheetHeader(stringResource(R.string.cfgt_checks_title, f.label), stringResource(R.string.cfgt_checks_sub), onClose)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetSection(stringResource(R.string.cfgt_when_empty))
            if (f.required) InfoBox(stringResource(R.string.cfgt_checks_mandatory_info))
            else CfgCard { ToggleRow(stringResource(R.string.cfgt_flag_empty), stringResource(R.string.cfgt_flag_empty_hint), flag, enabled = true) { flag = it } }

            Spacer(Modifier.height(4.dp))
            SheetSection(stringResource(R.string.cfg_checks_format))
            val fixedNote = when (f.kind) {
                FieldKind.DROPDOWN, FieldKind.MULTI -> pluralStringResource(R.plurals.cfgt_checks_one_of, options, options)
                FieldKind.CHECKBOX -> stringResource(R.string.cfgt_checks_checkbox)
                FieldKind.YESNO -> stringResource(R.string.cfgt_checks_yesno)
                FieldKind.URL -> stringResource(R.string.cfgt_checks_url)
                FieldKind.TIME -> stringResource(R.string.cfgt_checks_time)
                FieldKind.IMAGE, FieldKind.VIDEO, FieldKind.VOICE_MEMO, FieldKind.KEY_VALUE -> stringResource(R.string.cfg_checks_no_format)
                else -> null
            }
            if (fixedNote != null) {
                InfoBox(fixedNote)
            } else {
                if (rules.isNotEmpty()) {
                    Txt(stringResource(R.string.cfgt_values_must_be), 12, 600, C.Soft)
                    Box {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp).clip(r(12)).background(C.Bg).border(1.dp, C.Border2, r(12))
                                .clickable { ruleOpen = true }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(stringResource(rule.labelRes(f.kind)), 13, 500, modifier = Modifier.weight(1f))
                            Column {
                                LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f, Modifier.rotate(180f))
                                LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.4f)
                            }
                        }
                        DropdownMenu(ruleOpen, { ruleOpen = false }, containerColor = C.Surface) {
                            rules.forEach { r ->
                                DropdownMenuItem(
                                    text = { Txt(stringResource(r.labelRes(f.kind)), 14, if (r == rule) 700 else 500, if (r == rule) C.Yellow else C.Text) },
                                    onClick = { rule = r; ruleOpen = false },
                                )
                            }
                        }
                    }
                }
                if (f.kind in TEXT_KINDS && rule == ValueRule.PATTERN) {
                    LabeledInput(
                        stringResource(R.string.cfg_checks_pattern_label), pattern, { pattern = it.take(MAX_PATTERN_LENGTH) },
                        stringResource(R.string.cfg_checks_pattern_placeholder),
                    )
                    Txt(stringResource(R.string.cfg_checks_pattern_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
                // Dates have no limits at the server: past or future only.
                if (f.kind != FieldKind.DATE) {
                    val (loLabel, hiLabel, loHint, hiHint) = when (f.kind) {
                        FieldKind.NUMBER -> listOf(R.string.cfgt_min_value, R.string.cfgt_max_value, R.string.cfgt_min_value_hint, R.string.cfgt_max_value_hint)
                        else -> listOf(R.string.cfgt_min_length, R.string.cfgt_max_length, R.string.cfgt_min_length_hint, R.string.cfgt_max_length_hint)
                    }.map { stringResource(it) }
                    val filter = if (f.kind == FieldKind.NUMBER) numeric else digits
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { LabeledInput(loLabel, lo, { lo = filter(it) }, loHint) }
                        Box(Modifier.weight(1f)) { LabeledInput(hiLabel, hi, { hi = filter(it) }, hiHint) }
                    }
                }
                if (f.kind == FieldKind.NUMBER) {
                    CfgCard { ToggleRow(stringResource(R.string.cfg_checks_whole), stringResource(R.string.cfg_checks_whole_hint), whole, enabled = true) { whole = it } }
                }
                shown?.let { Txt(stringResource(it.messageRes()), 11, 500, C.Red, maxLines = Int.MAX_VALUE) }
            }
            if (onRemove != null && f.checks.isSet) Row(
                Modifier.fillMaxWidth().height(42.dp).clip(r(14)).border(1.dp, C.Red.copy(alpha = 0.7f), r(14)).clickable(onClick = onRemove),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                LineIcon(Icons.Trash, 17.dp, C.Red, 2.2f)
                Txt(stringResource(R.string.cfg_checks_remove), 12, 700, C.Red)
            }
        }
        SheetButtons(stringResource(R.string.cfg_save), shown == null, onClose) {
            tried = true
            if (problem == null) onSave(FieldChecks(flag && !f.required, rule, pattern, lo, hi, whole))
        }
    }
    }
}

/** A [−] value [+] stepper; [enabled] false shows "–". A blocked button is dimmed, and its tap says why. */
@Composable
private fun Stepper(value: Int, modifier: Modifier, enabled: Boolean, downBlocked: Boolean, upBlocked: Boolean, onStep: (up: Boolean) -> Unit) {
    Row(
        modifier.height(36.dp).clip(r(9)).background(C.Bg).border(1.dp, C.Border2, r(9)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled) { onStep(false) }, contentAlignment = Alignment.Center) {
            LineIcon(Icons.Minus, 14.dp, if (enabled && !downBlocked) C.Yellow else C.Dim, 2.6f)
        }
        Txt(if (enabled) value.toString() else "–", 13, 700, modifier = Modifier.width(26.dp), align = TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled) { onStep(true) }, contentAlignment = Alignment.Center) {
            LineIcon(Icons.PlusSmall, 14.dp, if (enabled && !upBlocked) C.Yellow else C.Dim, 2.6f)
        }
    }
}

// ---------------------------------------------------------------- Geofencing

/**
 * Geofencing, as the design: the address search, the map (OpenStreetMap or satellite, via the bundled Leaflet; page in
 * GeofenceMap.kt) with a Satellite switch, then the enforce switch (it asks to confirm, and works once a fence is
 * saved), the fences with their corners (tap one to move its corners, the bin removes it), and Draw fence. While
 * drawing, the bar says how many corners there are, with Cancel, Undo and Finish. Save sends them all to the server.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Geofencing(cfg: ConfigViewModel) {
    val g = cfg.geofence
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf(MapState()) }
    var query by remember { mutableStateOf("") }
    var satellite by remember { mutableStateOf(false) }
    val ready = g.load == LoadState.READY
    val enforced = g.saved.enforced
    val canEnforce = ready && !g.saving && g.saved.fences.isNotEmpty()
    val on = ready && pageReady && !g.saving
    // Show the saved fences once both the page and the geofence are there, and again after each load or save.
    LaunchedEffect(pageReady, g.revision, g.load) {
        if (pageReady && ready) web?.evaluateJavascript("adcSetFences(${fencesToJs(g.saved.fences)})", null)
    }
    LaunchedEffect(pageReady, satellite) { if (pageReady) web?.evaluateJavascript("adcSetSatellite($satellite)", null) }
    // Fences changed, or a fence being drawn, that aren't saved: Configuration asks before closing.
    val unsaved = map.dirty || (map.drawing && map.points > 0)
    LaunchedEffect(unsaved) { cfg.markGeoUnsaved(unsaved) }
    fun js(call: String) = web?.evaluateJavascript(call, null)
    // Address suggestions as the web's: up to five, after a short pause, once two letters are typed.
    var hits by remember { mutableStateOf(emptyList<PlaceHit>()) }
    var picking by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(query, pageReady) {
        if (!pageReady || !picking) return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        js("adcSuggest(${JsonPrimitive(query.trim())})")
    }
    LaunchedEffect(map.suggestions) {
        if (pageReady && map.suggestions > 0) web?.evaluateJavascript("adcSuggestions()") { hits = if (picking) hitsFromJs(it) else emptyList() }
    }
    fun go(hit: PlaceHit) {
        picking = false
        hits = emptyList()
        query = hit.name
        focus.clearFocus()
        js("adcGoTo(${hit.lat}, ${hit.lon}, ${JsonPrimitive(hit.name)})")
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(
            query, { query = it; picking = true; if (it.trim().length < 2) hits = emptyList() }, stringResource(R.string.cfg_geo_search),
            Modifier.padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 10.dp).testTag("geo-search"),
            iconColor = C.Yellow,
            onSubmit = {
                picking = false
                hits = emptyList()
                focus.clearFocus()
                if (pageReady && query.isNotBlank()) js("adcSearch(${JsonPrimitive(query.trim())})")
            },
        )
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().weight(1f).clip(r(14)).border(1.dp, C.Border, r(14))) {
            // Serves leaflet.js / .css / images from assets/leaflet/ so no script is fetched from the internet.
            val context = androidx.compose.ui.platform.LocalContext.current
            val assets = remember { androidx.webkit.WebViewAssetLoader.Builder().addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context)).build() }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // JavaScript runs the map; nothing else is switched on, and only the bundled page and
                        // Leaflet (served from the app's assets) plus map tiles and address search are reachable.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        // Without MATCH_PARENT the WebView sizes its viewport to the (empty) page: a 0-high map.
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // Drags on the map pan the map instead of swiping to the next tab.
                        setOnTouchListener { v, e ->
                            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                                assets.shouldInterceptRequest(request.url)

                            // The map never navigates away; links stay inert.
                            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true

                            override fun onPageFinished(view: WebView, url: String?) {
                                pageReady = true
                            }
                        }
                        // The page reports what it is doing through its title (no JavaScript interface is added).
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView, title: String?) {
                                parseMapTitle(title)?.let { map = it }
                            }
                        }
                        loadDataWithBaseURL("https://appassets.androidplatform.net/", MAP_HTML, "text/html", "utf-8", null)
                        web = this
                    }
                },
                onRelease = {
                    web = null
                    pageReady = false
                    it.destroy()
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Street map or satellite imagery.
            Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp).clip(Capsule).background(Color(0xCC1C1D21))
                    .clickable { satellite = !satellite }.padding(horizontal = 12.dp, vertical = 7.dp).testTag("geo-satellite"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LineIcon(Icons.Globe, 14.dp, if (satellite) C.Yellow else C.White, 2.2f)
                Txt(stringResource(if (satellite) R.string.cfg_geo_map else R.string.cfg_geo_satellite), 12, 700, C.White)
            }
            // Address suggestions for what is typed, over the top of the map; tapping one goes there.
            if (picking && hits.isNotEmpty()) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(C.Surface).border(1.dp, C.Border).testTag("geo-suggestions")) {
                    hits.forEach { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { go(h) }.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LineIcon(Icons.Pin, 16.dp, C.Yellow, 2.2f)
                            Txt(h.name, 12, 500, modifier = Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                        }
                    }
                }
            }
        }
        if (map.drawing) {
            // Drawing: how many corners so far, then Cancel, Undo and Finish (3 corners or more).
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(pluralStringResource(R.plurals.cfg_geo_drawing, map.points, map.points), 13, 500, C.Soft, maxLines = Int.MAX_VALUE)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.cfg_cancel), { js("adcCancelDraw()") }, height = 40.dp, hPad = 16.dp, size = 13)
                    Pill(stringResource(R.string.cfg_geo_undo), { if (map.points > 0) js("adcUndo()") }, Modifier.alpha(if (map.points > 0) 1f else 0.4f), height = 40.dp, hPad = 16.dp, size = 13)
                    Spacer(Modifier.weight(1f))
                    Pill(
                        stringResource(R.string.cfg_geo_finish), { if (map.points >= 3) js("adcFinishDraw()") }, Modifier.alpha(if (map.points >= 3) 1f else 0.4f).testTag("geo-finish"),
                        bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 22.dp, size = 13, weight = 700,
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Enforce location restriction.
                Row(
                    Modifier.fillMaxWidth().clickable { if (canEnforce) cfg.askEnforce(!enforced) else if (ready) cfg.explainEnforce() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(stringResource(R.string.cfg_geo_enforce_title), 14, 700, maxLines = 2)
                        Txt(
                            when (g.load) {
                                LoadState.LOADING -> stringResource(R.string.cfg_geo_loading)
                                LoadState.FAILED -> stringResource(R.string.cfg_geo_load_failed)
                                LoadState.READY -> stringResource(R.string.cfg_geo_enforce_hint)
                            },
                            12, 400, C.Muted, maxLines = Int.MAX_VALUE,
                        )
                    }
                    Box(Modifier.alpha(if (canEnforce) 1f else 0.5f).testTag("geo-enforce")) {
                        OnOffSwitch(enforced) { if (canEnforce) cfg.askEnforce(it) else if (ready) cfg.explainEnforce() }
                    }
                }
                // The fences on the map now (saved or not): tap one to move its corners; the bin removes it.
                map.corners.forEachIndexed { i, n ->
                    val picked = map.selected == i
                    Row(
                        Modifier.fillMaxWidth().clip(r(10)).background(if (picked) C.YTintBg else Color.Transparent)
                            .clickable { if (on) js("adcSelectFence($i)") }.heightIn(min = 44.dp).padding(start = 4.dp).testTag("geo-fence-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LineIcon(Icons.Fence, 20.dp, C.Orange, 2.2f)
                        Txt(stringResource(R.string.cfg_geo_fence_name, i + 1), 14, 700)
                        Txt(pluralStringResource(R.plurals.cfg_geo_corners, n, n), 12, 400, C.Muted, Modifier.weight(1f))
                        val label = stringResource(R.string.cfg_geo_delete_fence_cd, i + 1)
                        Box(
                            Modifier.size(44.dp).clip(r(10)).clickable { if (on) js("adcDeleteFence($i)") }.semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Icons.Trash, 18.dp, C.Red, 2.2f) }
                    }
                }
                // A picked fence: how to change its corners, and Remove corner for the tapped one (a fence keeps 3).
                if (map.selected >= 0) Txt(stringResource(R.string.cfg_geo_corner_hint), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.cfg_geo_draw_fence), { if (on) js("adcStartDraw()") }, Modifier.alpha(if (on) 1f else 0.4f).testTag("geo-draw"), height = 40.dp, hPad = 18.dp, size = 13, weight = 700)
                    if (map.corner >= 0) {
                        val canRemove = on && (map.corners.getOrNull(map.selected) ?: 0) > 3
                        Pill(
                            stringResource(R.string.cfg_geo_remove_corner), { if (canRemove) js("adcRemoveCorner()") },
                            Modifier.alpha(if (canRemove) 1f else 0.4f).testTag("geo-remove-corner"), fg = C.Red, height = 40.dp, hPad = 14.dp, size = 13,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Save appears once something changed on the map.
                    if (map.dirty) {
                        Pill(
                            stringResource(if (g.saving) R.string.cfg_saving else R.string.cfg_save),
                            { if (on) web?.evaluateJavascript("adcGetFences()") { value -> cfg.saveFences(fencesFromJs(value)) } },
                            Modifier.alpha(if (on) 1f else 0.4f).testTag("geo-save"), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, hPad = 22.dp, size = 13, weight = 700,
                        )
                    }
                }
            }
        }
    }
    g.confirmEnforce?.let { turnOn ->
        ConfirmDialog(
            icon = Icons.Pin, iconBg = C.YTintBg, iconFg = C.Yellow,
            title = stringResource(R.string.cfg_geo_confirm_question),
            body = stringResource(if (turnOn) R.string.cfg_geo_confirm_enforce else R.string.cfg_geo_confirm_release),
            no = stringResource(R.string.cfg_cancel), yes = stringResource(R.string.cfg_geo_proceed),
            onNo = cfg::cancelEnforce, onYes = cfg::confirmEnforce, yesBg = C.Yellow,
        )
    }
}

/** The floor that holds location [locationId]. */
private fun floorOf(tree: List<LocNode>, locationId: String): LocNode? =
    tree.flatMap { it.kids.orEmpty() }.firstOrNull { f -> f.kids.orEmpty().any { it.id == locationId } }

/** Loading, or why the server's data isn't there, with Try again: the empty-state note these tabs already use. */
@Composable
private fun OnlineNote(load: LoadState, failure: com.sclera.sclerago.presentation.common.UiText?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = pageGutter()).padding(horizontal = 9.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (load == LoadState.LOADING) {
            EmptyNote(stringResource(R.string.cfg_loading))
        } else {
            EmptyNote(stringResource(R.string.cfg_needs_connection) + (failure?.let { "\n" + it.asString() } ?: ""))
            Pill(stringResource(R.string.cfg_try_again), onRetry, height = 40.dp, hPad = 16.dp)
        }
    }
}
