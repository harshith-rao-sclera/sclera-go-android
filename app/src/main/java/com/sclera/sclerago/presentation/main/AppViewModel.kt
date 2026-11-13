package com.sclera.sclerago.presentation.main

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocPick
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.model.captureName
import com.sclera.sclerago.domain.model.optionCount
import com.sclera.sclerago.domain.model.scanKey
import com.sclera.sclerago.domain.model.timeLabel
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.config.AssetConfigUseCases
import com.sclera.sclerago.domain.usecase.config.ImportFieldsUseCase
import com.sclera.sclerago.domain.usecase.config.ToggleFieldVisibleUseCase
import com.sclera.sclerago.domain.usecase.location.ImportBuildingsUseCase
import com.sclera.sclerago.domain.usecase.location.LocationUseCases
import com.sclera.sclerago.domain.usecase.media.FileUseCases
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.validation.ValidationUseCases
import com.sclera.sclerago.domain.usecase.validation.forProperty
import com.sclera.sclerago.domain.usecase.validation.lastRun
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.TEMPLATES
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.ValScope
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.services.media.formatSize
import com.sclera.sclerago.services.scan.ScanService
import com.sclera.sclerago.services.scan.ScanServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject

enum class Tab { BT, NET, ASSETS, CAMERA, FILE }
enum class LocFilter { ALL, SPEC, NONE }
enum class Sheet { NONE, LOC, PROP }
enum class SheetFor { FILTER, ASSET, BULK }
enum class NetMode { WIFI, AP }
enum class MenuItem { PROPERTY, USERS, ASSETS, VALIDATION, CONFIGURATION, AUDIT, SURVEY, INTEGRATION }
enum class CfgPage { BUILDING, FIELDS, GEO, SETTINGS }
enum class AdminPage { PROPERTIES, PEOPLE }

/** The "Run validation" sheet: what to check (a template or saved validation id) and which assets. */
data class RunSheet(val sourceId: String = "quick", val scope: ValScope = ValScope.ALL, val chosen: Set<Int> = emptySet())

/** Creating or editing a saved validation. [id] is null for a new one. */
data class SavedEditor(val id: String?, val name: String, val ruleIds: List<String>, val description: String = "")

data class Preview(
    val name: String,
    val sub: String,
    val bg: Color,
    val plate: Boolean,
    val video: Boolean,
    val media: Media? = null,
)

/** What the screens show and where the user is. The data itself is in the repositories (domain/repository). */
data class UiState(
    val tab: Tab = Tab.ASSETS,
    val loc: LocFilter = LocFilter.ALL,
    val camOpen: Boolean = true,
    val fileOpen: Boolean = true,
    val revCam: Boolean = false,
    val revFile: Boolean = false,
    val netMode: NetMode = NetMode.WIFI,
    /** Bluetooth / Network: true = "Scan" segment, false = "Assets" segment. */
    val btScan: Boolean = true,
    val netScan: Boolean = true,
    val statusFilter: Map<Tab, Status?> = emptyMap(),
    val q: String = "",
    val searchOpen: Boolean = false,
    val specLocs: List<String> = listOf("location1"),
    val specPath: List<String> = listOf("building 1", "floor 1", "location1"),
    val specWhere: String = "location1 · floor 1",
    val specSel: String = "l1",
    val sheet: Sheet = Sheet.NONE,
    val sheetFor: SheetFor = SheetFor.FILTER,
    val prop: String = "p1",
    val propQ: String = "",
    val pick: Set<Int> = emptySet(),
    /** Scanned devices ticked on the Bluetooth / Network scan list, to add as assets together. */
    val devPick: Set<String> = emptySet(),
    /**
     * Select all is on: every device that isn't an asset yet is ticked, including ones the scan finds
     * later, except those in [devSkip] (unticked by hand).
     */
    val devAll: Boolean = false,
    val devSkip: Set<String> = emptySet(),
    val pv: Preview? = null,
    val assetId: Int? = null,
    val newAsset: Boolean = false,
    /** Scanned device the new-asset form was opened from (null = added manually). */
    val newDevice: Device? = null,
    val newSrc: Source = Source.MANUAL,
    /** Photos/videos the new-asset form opens with (Camera tab: the shot just taken). */
    val newMedia: List<Media> = emptyList(),
    /** Photos/videos taken (or picked) in the Camera tab, newest first. */
    val captures: List<Media> = emptyList(),
    val camSel: Set<String> = emptySet(),
    val videoMode: Boolean = false,
    /** Documents chosen in the system file manager, waiting for review. */
    val importFiles: List<ImportedFile> = emptyList(),
    /** Set when the File tab should launch the system file manager; the UI clears it. */
    val pendingFilePicker: Boolean = false,
    val revOff: Set<String> = emptySet(),
    val toast: String = "",
    val toastOn: Boolean = false,
    /** Bulk delete waiting for confirmation. */
    val confirmDeleteSel: Boolean = false,
    /** Side menu (header menu button). */
    val menuOpen: Boolean = false,
    // ---- Validation page
    val valOpen: Boolean = false,
    val valSheet: RunSheet? = null,
    val valPicker: Boolean = false,
    /** Template / saved validation whose list of checks is shown. */
    val valChecksFor: String? = null,
    val valReview: Int? = null,
    val valEditor: SavedEditor? = null,
    val valConfirmDelete: String? = null,
    // ---- Configuration page
    val cfgOpen: Boolean = false,
    val cfgPage: CfgPage = CfgPage.FIELDS,
    // ---- Property list / People and access (side menu)
    val adminPage: AdminPage? = null,
    val propListQ: String = "",
    val addPropOpen: Boolean = false,
    val importOpen: Boolean = false,
)

data class StatusChip(val status: Status?, val label: String, val count: Int, val selected: Boolean)
data class AssetListData(val items: List<Asset>, val chips: List<StatusChip>, val emptyText: String)

data class ReviewItem(
    val id: String,
    val name: String,
    val sub: String,
    val ready: Boolean,
    val on: Boolean,
    val ext: String = "",
    val extColor: Color = Color.Transparent,
    val file: String = "",
    val media: Media? = null,
)

data class DeviceRow(val device: Device, val isNew: Boolean)

/** The Bluetooth / Network tab a Property Settings switch controls. */
private val OnboardingChannel.tab: Tab
    get() = when (this) {
        OnboardingChannel.BLUETOOTH -> Tab.BT
        OnboardingChannel.NETWORK -> Tab.NET
    }

private val Tab.channel: OnboardingChannel?
    get() = OnboardingChannel.entries.firstOrNull { it.tab == this }

/**
 * The asset onboarding screens (Assets, scans, camera, files), Validation, Configuration and the
 * side menu's Property list. Holds what is on screen; reads and changes the data through use cases.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    scanFactory: ScanServiceFactory,
    observeAuthState: ObserveAuthStateUseCase,
    private val assetUseCases: AssetUseCases,
    private val propertyUseCases: PropertyUseCases,
    private val locationUseCases: LocationUseCases,
    private val configUseCases: AssetConfigUseCases,
    private val validationUseCases: ValidationUseCases,
    private val fileUseCases: FileUseCases,
) : ViewModel() {
    var state by mutableStateOf(UiState())
        private set

    private fun set(f: UiState.() -> UiState) {
        state = state.f()
    }

    // The data, as the screens read it (recomposing when it changes).
    private val catalog = assetUseCases.observe().observedIn(viewModelScope)
    private val propertyFlow = propertyUseCases.observe().observedIn(viewModelScope)
    private val onboardingOff = propertyUseCases.observeOnboarding().observedIn(viewModelScope)
    private val trees = locationUseCases.observeTrees().observedIn(viewModelScope)
    private val assetConfig = configUseCases.observe().observedIn(viewModelScope)
    private val jobs = validationUseCases.observeJobs().observedIn(viewModelScope)
    private val saved = validationUseCases.observeSaved().observedIn(viewModelScope)

    private var toastJob: Job? = null
    fun flash(msg: String) {
        set { copy(toast = msg, toastOn = true) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2600)
            set { copy(toastOn = false) }
        }
    }

    // ---------- Derived data ----------

    /** The current property's assets, with every edit applied. */
    val assets: List<Asset> get() = catalog.value.assetsIn(state.prop)

    val properties: List<Property> get() = propertyFlow.value

    /** Values saved from an asset's details form, if any. */
    fun savedDetails(id: Int): Map<String, String>? = catalog.value.savedDetails[id]

    /** Code and reference fields an existing asset came with. */
    fun assetDetails(id: Int): AssetDetails? = assetUseCases.details(id)

    private fun inLoc(a: Asset): Boolean = when (state.loc) {
        LocFilter.ALL -> true
        LocFilter.SPEC -> a.loc != null && a.loc in state.specLocs
        LocFilter.NONE -> a.loc == null
    }

    private val tgtLoc: String?
        get() = when (state.loc) {
            LocFilter.SPEC -> if (state.specLocs.size == 1) state.specLocs[0] else state.specPath.last()
            LocFilter.NONE -> null
            LocFilter.ALL -> "location1"
        }
    private val tgtWhere: String
        get() = when (state.loc) {
            LocFilter.SPEC -> state.specWhere
            LocFilter.NONE -> ""
            LocFilter.ALL -> "location1 · floor 1"
        }

    fun listFor(tab: Tab): AssetListData {
        val s = state
        val ql = s.q.trim().lowercase()
        val srcs: Set<Source>? = when (tab) {
            Tab.BT -> setOf(Source.BT)
            Tab.NET -> setOf(Source.WIFI, Source.AP)
            Tab.CAMERA -> setOf(Source.CAMERA)
            Tab.FILE -> setOf(Source.FILE)
            Tab.ASSETS -> null
        }
        val base = assets
            .filter { srcs == null || it.src in srcs }
            .filter { inLoc(it) }
            .filter { ql.isEmpty() || "${it.name} ${it.detail}".lowercase().contains(ql) }
        val sf = s.statusFilter[tab]
        val items = base.filter { sf == null || it.st == sf }
        val chips = listOf(StatusChip(null, "All", base.size, sf == null)) +
            Status.entries.filter { it != Status.INPUT }.map { st ->
                StatusChip(st, st.label, base.count { it.st == st }, sf == st)
            }
        val emptyText = when {
            ql.isNotEmpty() -> "No asset matches “${s.q.trim()}”."
            sf != null -> "No assets with this status here."
            tab == Tab.ASSETS -> "Scan, photograph or import assets with the tabs below."
            else -> "Nothing onboarded this way here yet."
        }
        return AssetListData(items, chips, emptyText)
    }

    /** The current property's name; empty until the properties have loaded. */
    val propName: String get() = (properties.firstOrNull { it.id == state.prop } ?: properties.firstOrNull())?.name.orEmpty()
    val specPathShown: List<String> get() = if (state.prop != "p1") listOf("Choose location") else state.specPath

    val detailAsset: Asset?
        get() = if (state.newAsset) {
            val d = state.newDevice
            val cam = state.newMedia.firstOrNull()
            Asset(
                -1, d?.let { deviceName(it) } ?: cam?.let { captureName(it) } ?: "", d?.sub ?: "", tgtLoc, tgtWhere,
                state.newSrc, Status.DRAFT, d?.type ?: AssetType.GENERIC, state.prop,
                media = state.newMedia,
            )
        } else state.assetId?.let { id -> assets.firstOrNull { it.id == id } }

    /** Form values filled in from the scanned device the new asset comes from. */
    val newPrefill: Map<String, String>
        get() = state.newDevice?.let { prefillFor(it, state.newSrc) } ?: emptyMap()

    /** What an asset made from scanned device [d] starts with: a note saying where it came from, and its BLE tag / IP. */
    private fun prefillFor(d: Device, src: Source): Map<String, String> {
        val m = mutableMapOf("note" to "${src.label}: ${d.name} · ${d.sub}")
        when (src) {
            Source.BT -> m["ble"] = scanKey(d)
            Source.WIFI -> m["ip"] = scanKey(d)
            else -> {}
        }
        return m
    }

    val showDetail: Boolean get() = detailAsset != null
    val camSub: Boolean get() = !state.revCam && !state.camOpen
    val fileSub: Boolean get() = !state.revFile && !state.fileOpen
    val listy: Boolean
        get() = when (state.tab) {
            Tab.ASSETS, Tab.BT, Tab.NET -> true
            Tab.CAMERA -> camSub
            Tab.FILE -> fileSub
        }
    private val sheetOpen get() = state.sheet != Sheet.NONE
    val selOn: Boolean get() = pickedIds.isNotEmpty() && listy && !showDetail && !sheetOpen
    val showFabs: Boolean get() = listy && !showDetail && !sheetOpen && !selOn && !devSelOn

    /** Only ids that still exist in the current property count as picked. */
    val pickedIds: Set<Int> get() = state.pick.filter { id -> assets.any { it.id == id } }.toSet()

    private val curItems: List<Asset> get() = if (listy) listFor(state.tab).items else emptyList()
    val allPicked: Boolean get() = curItems.isNotEmpty() && curItems.all { it.id in pickedIds }
    val curCount: Int get() = curItems.size

    fun propRows(): List<Pair<Property, String>> {
        val ql = state.propQ.trim().lowercase()
        val c = catalog.value
        return properties.filter { ql.isEmpty() || it.name.lowercase().contains(ql) }.map { p ->
            val n = c.countIn(p.id)
            p to if (n > 0) plural(n, "asset", "assets") + (if (p.gps) " · 5 buildings" else "") else "No assets yet"
        }
    }

    // ---------- Scanning: the scans themselves live in services/scan/ScanService ----------

    private val scans = scanFactory.create(viewModelScope)

    override fun onCleared() {
        scans.shutdown()
        super.onCleared()
    }

    /** The Network tab's current mode as a scan. */
    val netTarget: ScanService.Target
        get() = if (state.netMode == NetMode.AP) ScanService.Target.ACCESS_POINTS else ScanService.Target.NETWORK

    /** A Bluetooth scan is running right now. */
    val btOn: Boolean get() = scans.isScanning(ScanService.Target.BLUETOOTH)

    /** The scan for the Network tab's current mode (Wi-Fi network / access points) is running right now. */
    val netOn: Boolean get() = scans.isScanning(netTarget)

    /** Runtime permissions the UI should ask for now (or null); the answer goes to [onScanPermission]. */
    val scanPermissionRequest: List<String>? get() = scans.permissionRequest

    fun onScanPermission(granted: Boolean) = scans.onPermissionResult(granted)

    /** The scan whose permission is about to be asked for (the UI explains it first), or null. */
    val scanPermissionAsk: ScanService.Target? get() = scans.permissionAsk

    /** [t]'s permission was refused; its page shows how to allow it. */
    fun scanBlocked(t: ScanService.Target): Boolean = scans.isBlocked(t)

    /** "Try again" on a blocked scan: asks for its permission again. */
    fun retryScan(t: ScanService.Target) = scans.start(t)

    val btCard: ScanService.Card
        get() = scans.card(ScanService.Target.BLUETOOTH, btDevices.size, btNew)

    val netCard: ScanService.Card
        get() = scans.card(netTarget, netDevices.size, netNew)

    private fun deviceName(d: Device) = if (d.name in setOf(UNKNOWN_DEVICE, "Hidden network")) "" else d.name

    private val scanSource: Source
        get() = when {
            state.tab == Tab.BT -> Source.BT
            state.netMode == NetMode.AP -> Source.AP
            else -> Source.WIFI
        }

    /** Scan rows, built once per refresh: a network sweep can return hundreds of hosts. */
    private fun rows(devices: List<Device>, src: Source): List<DeviceRow> {
        val matches = assetUseCases.matchDevices(devices, src, assets)
        val added = catalog.value.addedDevices
        return devices.map { DeviceRow(it, it.id !in added && matches[it.id] == null) }
    }

    // Cached: rebuilt only when the scan's rows, the assets or the Network mode change, not on every
    // read (the scan card and the list both read them, and the card's progress ticks several times a second).
    private val btRowsState = derivedStateOf { rows(scans.rows(ScanService.Target.BLUETOOTH), Source.BT) }
    private val netRowsState = derivedStateOf {
        rows(scans.rows(netTarget), if (state.netMode == NetMode.AP) Source.AP else Source.WIFI)
    }
    private val btNewState = derivedStateOf { btRowsState.value.count { it.isNew } }
    private val netNewState = derivedStateOf { netRowsState.value.count { it.isNew } }

    val btDevices: List<DeviceRow> get() = btRowsState.value
    val netDevices: List<DeviceRow> get() = netRowsState.value
    private val btNew: Int get() = btNewState.value
    private val netNew: Int get() = netNewState.value

    // Camera: every selected capture becomes its own draft asset.
    val camReview: List<ReviewItem>
        get() = state.captures.filter { it.id in state.camSel }.map { m ->
            ReviewItem(
                id = m.id, name = captureName(m),
                sub = if (m.video) "Video ${m.durationText} · name and details to add" else "Photo · name and details to add",
                ready = false, on = m.id !in state.revOff, media = m,
            )
        }

    // Files
    val fileReview: List<ReviewItem>
        get() = state.importFiles.map { f ->
            ReviewItem(
                id = f.id, name = f.baseName, sub = "${f.name} · ${formatSize(f.size)}", ready = false,
                on = f.id !in state.revOff, ext = f.ext, extColor = extColor(f), file = f.name,
            )
        }

    private fun extColor(f: ImportedFile): Color {
        val mime = f.mime ?: ""
        return when {
            f.ext in setOf("XLS", "XLSX", "CSV") || mime.contains("sheet") || mime.contains("excel") -> C.Green
            f.ext == "PDF" || mime == "application/pdf" -> C.Red
            mime.startsWith("image/") -> C.Blue
            mime.startsWith("video/") -> C.Purple
            else -> C.Soft
        }
    }

    // ---------- Actions ----------

    fun goTab(t: Tab) {
        val from = state.tab
        set {
            var o = copy(tab = t, assetId = null, newAsset = false, newDevice = null, newMedia = emptyList(), pick = emptySet(), devPick = emptySet(), devAll = false, devSkip = emptySet())
            if (t == Tab.CAMERA) o = o.copy(camOpen = true, revCam = false)
            if (t == Tab.FILE) o = o.copy(fileOpen = true, revFile = false, pendingFilePicker = true)
            // Scanning tabs always open on their Scan segment, not their asset list.
            if (t == Tab.BT) o = o.copy(btScan = true)
            if (t == Tab.NET) o = o.copy(netScan = true)
            o
        }
        // Opening a scanning tab starts its scan. Leaving one stops the radio scans (they cost battery);
        // a network sweep already under way is left to finish.
        when (t) {
            Tab.BT -> scans.start(ScanService.Target.BLUETOOTH, auto = true)
            Tab.NET -> scans.start(netTarget, auto = true)
            else -> {}
        }
        if (from == Tab.BT && t != Tab.BT) scans.stop(ScanService.Target.BLUETOOTH)
        if (from == Tab.NET && t != Tab.NET) scans.stop(ScanService.Target.ACCESS_POINTS)
    }

    fun onPageSettled(page: Int) {
        val t = navTabs.getOrNull(page) ?: return
        if (t != state.tab) goTab(t)
    }

    /** The bottom tabs this property shows: Bluetooth / Network are hidden when their onboarding is off. */
    val navTabs: List<Tab> get() = Tab.entries - onboardingOff.value[state.prop].orEmpty().map { it.tab }.toSet()

    /** Shake gesture: jump straight to the live camera from anywhere. */
    fun openCameraByShake() {
        val s = state
        if (s.tab == Tab.CAMERA && s.camOpen && !s.revCam && s.sheet == Sheet.NONE && s.pv == null) return
        goTab(Tab.CAMERA)
        set { copy(sheet = Sheet.NONE, pv = null, searchOpen = false) }
    }

    fun tapLocTab(f: LocFilter) {
        val cur = state.loc
        when (f) {
            LocFilter.ALL -> if (cur == LocFilter.ALL) openProp() else set { copy(loc = LocFilter.ALL, assetId = null) }
            LocFilter.SPEC -> if (cur == LocFilter.SPEC) openLocFilter() else set { copy(loc = LocFilter.SPEC, assetId = null) }
            LocFilter.NONE -> set { copy(loc = LocFilter.NONE, assetId = null) }
        }
    }

    fun openProp() = set { copy(sheet = Sheet.PROP) }

    // Side menu
    fun openMenu() = set { copy(menuOpen = true, searchOpen = false) }
    fun closeMenu() = set { copy(menuOpen = false) }
    fun switchPropertyFromMenu() = set { copy(menuOpen = false, sheet = Sheet.PROP) }
    fun pickMenu(item: MenuItem) {
        closeMenu()
        when (item) {
            MenuItem.PROPERTY -> openAdmin(AdminPage.PROPERTIES)
            MenuItem.USERS -> openAdmin(AdminPage.PEOPLE)
            MenuItem.ASSETS -> { closeAdmin(); goTab(Tab.ASSETS) }
            MenuItem.VALIDATION -> { closeAdmin(); openValidation() }
            MenuItem.CONFIGURATION -> { closeAdmin(); openConfig() }
            else -> flash(item.name.lowercase().replaceFirstChar { it.uppercase() } + " is coming soon")
        }
    }

    // Property list / People and access
    fun openAdmin(page: AdminPage) = set {
        copy(adminPage = page, menuOpen = false, valOpen = false, cfgOpen = false, assetId = null, newAsset = false, searchOpen = false, propListQ = "")
    }
    fun closeAdmin() = set { copy(adminPage = null, addPropOpen = false, importOpen = false) }
    fun setPropListQ(v: String) = set { copy(propListQ = v) }
    val propertyList: List<Property>
        get() = state.propListQ.trim().lowercase().let { q -> properties.filter { q.isEmpty() || q in it.name.lowercase() } }

    /** Opening a property from the list makes it the current one and shows its assets. */
    fun openPropertyFromList(id: String) {
        closeAdmin()
        pickProp(id)
        goTab(Tab.ASSETS)
    }
    fun openAddProperty() = set { copy(addPropOpen = true) }
    fun closeAddProperty() = set { copy(addPropOpen = false) }

    /** Adds [draft] as a new property; returns an error message instead when it can't be added. */
    fun addProperty(draft: Property): String? = when (val r = propertyUseCases.add(draft)) {
        is AddPropertyUseCase.Result.Invalid -> r.message
        is AddPropertyUseCase.Result.Added -> {
            set { copy(addPropOpen = false) }
            flash("${r.property.name} added")
            null
        }
    }

    /** Assets in property [id] (any status). */
    fun assetCount(id: String): Int = catalog.value.countIn(id)
    fun openImport() = set { copy(importOpen = true) }
    fun closeImport() = set { copy(importOpen = false) }
    fun importProperties(fileName: String) {
        closeImport()
        flash("“$fileName” uploaded · importing isn’t connected to the server yet")
    }

    /** Writes the sample spreadsheet for importing properties. */
    fun writePropertyTemplate(out: OutputStream) = fileUseCases.writePropertyTemplate(out)

    fun closeProp() = set { copy(sheet = Sheet.NONE, propQ = "") }
    fun setPropQ(v: String) = set { copy(propQ = v) }
    fun pickProp(id: String) {
        val changed = id != state.prop
        set { copy(prop = id, sheet = Sheet.NONE, loc = LocFilter.ALL, assetId = null, pick = emptySet(), propQ = "") }
        // The new property may not onboard over the tab that's open.
        if (state.tab !in navTabs) goTab(Tab.ASSETS)
        if (changed) flash("Showing $propName")
    }

    fun openLocFilter() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.FILTER) }
    fun openLocAsset() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.ASSET) }
    fun assignSel() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.BULK) }
    fun closeLoc() = set { copy(sheet = Sheet.NONE) }

    fun pickLoc(r: LocPick) {
        val v = LocValue(if (r.locs.size == 1) r.locs[0] else r.label, r.where)
        val detail = detailAsset
        when {
            state.sheetFor == SheetFor.ASSET && detail != null && detail.id > 0 -> {
                set { copy(sheet = Sheet.NONE) }
                assetUseCases.assignLocation(listOf(detail.id), v)
            }
            state.sheetFor == SheetFor.BULK -> {
                val ids = pickedIds
                set { copy(sheet = Sheet.NONE, pick = emptySet()) }
                assetUseCases.assignLocation(ids, v)
                flash(plural(ids.size, "asset", "assets") + " assigned to " + r.label)
            }
            else -> set {
                copy(
                    sheet = Sheet.NONE, loc = LocFilter.SPEC, specLocs = r.locs, specPath = r.path.ifEmpty { listOf(r.label) },
                    specWhere = r.where, specSel = r.id, assetId = null,
                )
            }
        }
    }

    fun setBtScan(scan: Boolean) {
        set { copy(btScan = scan, devPick = emptySet(), devAll = false, devSkip = emptySet()) }
        if (scan) scans.start(ScanService.Target.BLUETOOTH, auto = true)
    }
    fun setNetScan(scan: Boolean) {
        set { copy(netScan = scan, devPick = emptySet(), devAll = false, devSkip = emptySet()) }
        if (scan) scans.start(netTarget, auto = true)
    }
    fun setNetMode(m: NetMode) {
        set { copy(netMode = m, devPick = emptySet(), devAll = false, devSkip = emptySet()) }
        if (state.netScan) scans.start(netTarget, auto = true)
    }
    /** Tap on the radar card: pause a running scan, or start a new one. */
    fun toggleBt() = scans.toggle(ScanService.Target.BLUETOOTH)
    fun toggleNet() = scans.toggle(netTarget)
    // ---------- Adding several scanned devices at once ----------

    /** The scan list on screen (Bluetooth, or the Network tab's current mode), or empty off the scan pages. */
    private val shownDevices: List<DeviceRow>
        get() = when {
            state.tab == Tab.BT && state.btScan -> btDevices
            state.tab == Tab.NET && state.netScan -> netDevices
            else -> emptyList()
        }

    /** Devices on the list that aren't assets yet: the ones that can be ticked. */
    private val addableDevices: List<Device> get() = shownDevices.filter { it.isNew }.map { it.device }

    /** Ticked devices that are still on the list and still not assets (with Select all on, new finds are ticked too). */
    val pickedDevices: List<Device>
        get() = state.let { s ->
            if (s.devAll) addableDevices.filter { it.id !in s.devSkip } else addableDevices.filter { it.id in s.devPick }
        }

    /** Select all is on, so devices the scan finds from now on are ticked as they appear. */
    val devAllOn: Boolean get() = state.devAll

    /** Ticking is under way: rows show tick boxes and the add bar shows. */
    val devSelOn: Boolean get() = pickedDevices.isNotEmpty() && !showDetail && !sheetOpen
    val allDevicesPicked: Boolean get() = addableDevices.let { a -> a.isNotEmpty() && pickedDevices.size == a.size }
    val addableCount: Int get() = addableDevices.size

    /** Tick / untick one device. With Select all on, unticking keeps that device out as more are found. */
    fun toggleDevicePick(id: String) = set {
        if (devAll) copy(devSkip = if (id in devSkip) devSkip - id else devSkip + id)
        else copy(devPick = if (id in devPick) devPick - id else devPick + id)
    }

    /**
     * Select all on the scan list: ticks every device that isn't an asset yet and keeps ticking new
     * ones as the scan finds them; if everything is already ticked, clears instead.
     */
    fun selectAllDevices() {
        if (allDevicesPicked) clearDevicePick() else set { copy(devAll = true, devSkip = emptySet(), devPick = emptySet()) }
    }

    fun clearDevicePick() = set { copy(devPick = emptySet(), devAll = false, devSkip = emptySet()) }

    /**
     * Adds every ticked device as a draft asset, with what the add form would have filled in from it
     * (name, its scan line, type, BLE tag / IP and a note). They're then marked as added on the list.
     */
    fun addPickedDevices() {
        val picked = pickedDevices
        if (picked.isEmpty()) return
        val src = scanSource
        val place = AssetPlace(state.prop, tgtLoc, tgtWhere)
        picked.forEach { d ->
            assetUseCases.create(
                deviceName(d), "", emptyList(), prefillFor(d, src), false,
                place = place, captures = emptyList(), device = d, deviceSource = src,
            )
        }
        clearDevicePick()
        flash(plural(picked.size, "asset", "assets") + " added as drafts · ${src.via}")
    }

    /** Tap on a scanned device: open its asset if it already has one, otherwise the add form prefilled from it. */
    fun openDevice(d: Device) {
        val src = scanSource
        val existing = assetUseCases.matchDevices(listOf(d), src, assets)[d.id]
        set {
            if (existing != null) copy(assetId = existing, newAsset = false, newDevice = null, searchOpen = false, pick = emptySet())
            else copy(newAsset = true, newDevice = d, newSrc = src, newMedia = emptyList(), assetId = null, searchOpen = false, pick = emptySet())
        }
    }

    fun setStatusFilter(tab: Tab, st: Status?) = set { copy(statusFilter = statusFilter + (tab to st)) }

    fun togglePick(id: Int) = set {
        val cur = pickedIds
        copy(pick = if (id in cur) cur - id else cur + id, searchOpen = false)
    }

    fun openAsset(id: Int) {
        if (pickedIds.isNotEmpty()) togglePick(id) else set { copy(assetId = id, newAsset = false, searchOpen = false) }
    }

    fun selAll() {
        if (allPicked) set { copy(pick = emptySet()) } else {
            val ids = curItems.map { it.id }
            set { copy(pick = pickedIds + ids) }
        }
    }

    fun clearPick() = set { copy(pick = emptySet()) }
    fun validateSel() {
        val ids = pickedIds
        assetUseCases.setStatus(ids, Status.VALIDATED)
        set { copy(pick = emptySet()) }
        flash(plural(ids.size, "asset", "assets") + " validated")
    }

    /** Status change for one asset (from its detail screen). */
    fun setAssetStatus(id: Int, st: Status) {
        assetUseCases.setStatus(listOf(id), st)
        flash("Asset " + st.label.lowercase())
    }

    fun submitSel() {
        val ids = pickedIds
        assetUseCases.setStatus(ids, Status.SUBMITTED)
        set { copy(pick = emptySet()) }
        flash(plural(ids.size, "asset", "assets") + " submitted")
    }

    fun askDeleteSel() = set { copy(confirmDeleteSel = true) }
    fun cancelDeleteSel() = set { copy(confirmDeleteSel = false) }
    fun deleteSel() {
        val ids = pickedIds
        assetUseCases.delete(ids)
        set { copy(pick = emptySet(), confirmDeleteSel = false) }
        flash(plural(ids.size, "asset", "assets") + " deleted")
    }

    fun openSearch() = set { copy(searchOpen = true) }
    fun closeSearch() = set { copy(searchOpen = false, q = "") }
    fun setQ(v: String) = set { copy(q = v) }

    fun openNew() = set { copy(newAsset = true, newDevice = null, newSrc = Source.MANUAL, newMedia = emptyList(), assetId = null, searchOpen = false, pick = emptySet()) }
    fun closeDetail() {
        // A camera shot that never became an asset is thrown away with the form.
        if (state.newAsset) fileUseCases.deleteOwned(state.newMedia)
        set { copy(assetId = null, newAsset = false, newDevice = null, newMedia = emptyList()) }
    }

    /** Camera tab: open the new-asset details with these photos/videos already attached. */
    private fun openNewWithMedia(media: List<Media>) {
        if (media.isEmpty()) return
        set {
            copy(
                newAsset = true, newDevice = null, newSrc = Source.CAMERA, newMedia = media, assetId = null,
                searchOpen = false, pick = emptySet(),
            )
        }
    }

    /** Save on an asset's details: keeps the values and moves it (back) to Draft. */
    fun saveDraft(id: Int, values: Map<String, String>) {
        assetUseCases.saveDetails(id, values, Status.DRAFT)
        flash("Saved as draft")
    }

    /** Submit on an asset's details: keeps the values and locks it as Submitted. */
    fun submitAsset(id: Int, values: Map<String, String>) {
        assetUseCases.saveDetails(id, values, Status.SUBMITTED)
        flash("Asset submitted · it can’t be edited now")
    }

    fun createAsset(
        name: String,
        detail: String,
        media: List<Media> = emptyList(),
        values: Map<String, String> = emptyMap(),
        submit: Boolean = false,
    ) {
        val s = state
        val created = assetUseCases.create(
            name, detail, media, values, submit,
            place = AssetPlace(s.prop, tgtLoc, tgtWhere),
            captures = s.newMedia, device = s.newDevice, deviceSource = s.newSrc,
        )
        val savedAs = if (submit) "Asset submitted" else "Asset added as draft"
        when (created.via) {
            // From the Camera tab: stay on the camera for the next one.
            CreateAssetUseCase.Via.CAMERA -> {
                set { copy(newAsset = false, newMedia = emptyList()) }
                flash("$savedAs · via Camera")
            }
            // From a scan: stay on the scan.
            CreateAssetUseCase.Via.SCAN -> {
                set { copy(newAsset = false, newDevice = null) }
                flash("$savedAs · ${created.source.via}")
            }
            CreateAssetUseCase.Via.MANUAL -> {
                set { copy(newAsset = false, tab = Tab.ASSETS, statusFilter = statusFilter + (Tab.ASSETS to null)) }
                flash(savedAs)
            }
        }
    }

    fun deleteAsset(id: Int) {
        assetUseCases.delete(listOf(id))
        set { copy(assetId = null) }
        flash("Asset deleted")
    }

    fun saveDetail(id: Int, values: Map<String, String>) = assetUseCases.saveDetails(id, values)

    /** Photos/videos from the asset's camera ("Add photo") attached to an existing asset. */
    fun attachMedia(assetId: Int, media: List<Media>) {
        if (!assetUseCases.attachMedia(assetId, media)) return
        flash(plural(media.size, "item", "items") + " added to asset")
    }

    fun showPreview(p: Preview) = set { copy(pv = p) }
    fun closePreview() = set { copy(pv = null) }

    // ---------- Camera ----------

    fun cancelCam() = set { copy(camOpen = false) }
    fun openCam() = set { copy(camOpen = true, revCam = false) }
    fun toggleStrip(id: String) = set { copy(camSel = if (id in camSel) camSel - id else camSel + id) }
    fun toCamReview() = set { copy(revCam = true, revOff = emptySet()) }
    fun setVideoMode(v: Boolean) = set { copy(videoMode = v) }
    fun backToCam() = set { copy(revCam = false, camOpen = true) }

    /** A photo/video from the in-app camera: straight to the new asset's details, with the shot attached. */
    fun addCapture(m: Media) = openNewWithMedia(listOf(m))

    /** Gallery button in the camera: the picked photos/videos open one new asset's details. */
    fun addPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            openNewWithMedia(fileUseCases.describeMedia(uris.map { it.toString() }))
        }
    }

    fun discardCam() {
        fileUseCases.deleteOwned(state.captures.filter { it.id in state.camSel })
        set {
            copy(
                revCam = false, camOpen = true, captures = captures.filter { it.id !in camSel },
                camSel = emptySet(), revOff = emptySet(),
            )
        }
    }

    fun toggleRev(id: String) = set { copy(revOff = if (id in revOff) revOff - id else revOff + id) }

    fun onboardCam() {
        val picked = camReview.filter { it.on }
        if (picked.isEmpty()) return
        val prop = state.prop
        val loc = tgtLoc
        val where = tgtWhere
        val made = assetUseCases.add(picked.size) { id, i ->
            val r = picked[i]
            val m = r.media!!
            Asset(
                id, r.name, (if (m.video) "Video ${m.durationText}" else "Photo") + " · captured " + timeLabel(m.createdAt).take(5),
                loc, where, Source.CAMERA, Status.INPUT, AssetType.GENERIC, prop, listOf(m),
            )
        }
        val used = picked.map { it.id }.toSet()
        set {
            copy(
                revOff = emptySet(), revCam = false, camOpen = false, camSel = camSel - used,
                captures = captures.filter { it.id !in used },
            )
        }
        flash(plural(made.size, "asset", "assets") + " added · details needed")
    }

    // ---------- Files ----------

    fun cancelFile() = set { copy(fileOpen = false) }
    fun openFile() = set { copy(fileOpen = true, revFile = false, pendingFilePicker = true) }
    fun filePickerLaunched() = set { copy(pendingFilePicker = false) }

    /** Result from the system file manager (empty list = cancelled). */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) {
            set { copy(fileOpen = false, revFile = importFiles.isNotEmpty()) }
            return
        }
        viewModelScope.launch {
            val files = fileUseCases.describeFiles(uris.map { it.toString() })
            set {
                val fresh = files.filter { f -> importFiles.none { it.uri == f.uri } }
                copy(importFiles = importFiles + fresh, fileOpen = false, revFile = true)
            }
        }
    }

    fun backToFiles() = set { copy(pendingFilePicker = true) }
    fun discardFile() = set { copy(revFile = false, fileOpen = false, importFiles = emptyList(), revOff = emptySet()) }
    fun onboardFile() {
        val picked = fileReview.filter { it.on }
        if (picked.isEmpty()) return
        val prop = state.prop
        val loc = tgtLoc
        val where = tgtWhere
        val made = assetUseCases.add(picked.size) { id, i ->
            val r = picked[i]
            Asset(id, r.name, r.file, loc, where, Source.FILE, Status.INPUT, typeOf(r.name), prop)
        }
        set { copy(revOff = emptySet(), revFile = false, fileOpen = false, importFiles = emptyList()) }
        flash(plural(made.size, "asset", "assets") + " imported · details needed")
    }

    // ---------- Validation ----------

    /** The values and facts the validation rules look at for one asset (same as its Validation tab). */
    fun checkInputFor(a: Asset): CheckInput = assetUseCases.checkInput(a)

    /** The user's saved validations. */
    val savedVals: List<SavedValidation> get() = saved.value

    fun sourceName(id: String): String = validationUseCases.source.name(id)

    fun sourceRules(id: String): List<Rule> = validationUseCases.source.rules(id)

    /** Jobs for the current property, newest first. */
    val propJobs: List<ValJob> get() = jobs.value.forProperty(state.prop)

    fun lastRun(sourceId: String): Long? = jobs.value.lastRun(state.prop, sourceId)

    /** Assets a run with [sheet]'s settings would cover. */
    fun scopeAssets(sheet: RunSheet): List<Asset> {
        jobs.value // "New or changed" depends on the last run and on edits: recompose when either changes.
        return validationUseCases.selectTargets(sheet.scope, sheet.chosen, sheet.sourceId, state.prop, assets)
    }

    // Side-menu pages always return to the Assets tab. It is also selected on open, so the
    // pager slides there hidden behind the page rather than visibly on close.
    fun openValidation() = set {
        copy(valOpen = true, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false, searchOpen = false, pick = emptySet())
    }
    fun closeValidation() = set {
        copy(
            valOpen = false, tab = Tab.ASSETS, assetId = null, newAsset = false,
            valSheet = null, valPicker = false, valChecksFor = null, valReview = null, valEditor = null,
        )
    }

    fun openRunSheet(sourceId: String = "quick", scope: ValScope = ValScope.ALL) =
        set { copy(valSheet = RunSheet(sourceId, scope), valPicker = scope == ValScope.CHOOSE, valChecksFor = null) }
    fun closeRunSheet() = set { copy(valSheet = null, valPicker = false) }
    fun setRunSource(id: String) = set { copy(valSheet = valSheet?.copy(sourceId = id)) }
    fun setRunScope(scope: ValScope) = set {
        val sh = valSheet ?: return@set this
        copy(valSheet = sh.copy(scope = scope), valPicker = scope == ValScope.CHOOSE)
    }
    fun openPicker() = set { copy(valPicker = true) }
    fun closePicker() = set { copy(valPicker = false) }
    fun toggleChosen(id: Int) = set {
        val sh = valSheet ?: return@set this
        copy(valSheet = sh.copy(chosen = if (id in sh.chosen) sh.chosen - id else sh.chosen + id))
    }
    fun chooseAll(all: Boolean) {
        val ids = assets.map { it.id }.toSet()
        set {
            val sh = valSheet ?: return@set this
            copy(valSheet = sh.copy(chosen = if (all) ids else emptySet()))
        }
    }

    fun runFromSheet() {
        val sh = state.valSheet ?: return
        val targets = scopeAssets(sh)
        if (targets.isEmpty()) return
        val label = when (sh.scope) {
            ValScope.ALL -> "All assets"
            ValScope.CHOOSE -> plural(targets.size, "chosen asset", "chosen assets")
            ValScope.CHANGED -> plural(targets.size, "new or changed asset", "new or changed assets")
        }
        set { copy(valSheet = null, valPicker = false) }
        startJob(sh.sourceId, targets.map { it.id }, label)
    }

    fun runOnAll(sourceId: String) = startJob(sourceId, assets.map { it.id }, "All assets")

    /** Starts a validation job; it runs in the background and fills in its results when done. */
    private fun startJob(
        sourceId: String,
        ids: List<Int>,
        label: String,
        startedAt: Long = System.currentTimeMillis(),
        instant: Boolean = false,
    ) {
        if (!instant) flash("Validation started · runs in the background")
        validationUseCases.start(
            sourceId, ids, label, state.prop,
            inputs = { assets.map { it to checkInputFor(it) } },
            startedAt = startedAt, instant = instant,
            onDone = { j -> if (!instant) flash("${j.title} finished · ${j.failed} failed · ${j.passed} passed") },
        )
    }

    fun openChecks(id: String?) = set { copy(valChecksFor = id) }
    fun openReview(jobId: Int?) = set { copy(valReview = jobId) }

    // New validation page: name, description and its steps (checks, run in order).
    fun newSaved() = set { copy(valEditor = SavedEditor(null, "", emptyList())) }
    fun closeEditor() = set { copy(valEditor = null) }
    private fun editSteps(f: (List<String>) -> List<String>) = set { copy(valEditor = valEditor?.let { it.copy(ruleIds = f(it.ruleIds)) }) }
    fun setEditorName(v: String) = set { copy(valEditor = valEditor?.copy(name = v)) }
    fun setEditorDesc(v: String) = set { copy(valEditor = valEditor?.copy(description = v)) }
    fun addStep(rule: String) = editSteps { if (rule in it) it else it + rule }
    fun removeStep(rule: String) = editSteps { it - rule }
    fun clearSteps() = editSteps { emptyList() }
    fun moveStep(rule: String, by: Int) = editSteps { l ->
        val i = l.indexOf(rule)
        val j = i + by
        if (i < 0 || j !in l.indices) l else l.toMutableList().also { it.add(j, it.removeAt(i)) }
    }
    /** "Start from": replaces the steps with a template's checks. */
    fun stepsFrom(templateId: String) {
        val t = TEMPLATES.firstOrNull { it.id == templateId } ?: return
        editSteps { t.ruleIds }
    }
    /** Saves the validation; with [run], also runs it on all assets. */
    fun saveEditor(run: Boolean = false) {
        val e = state.valEditor ?: return
        val id = validationUseCases.save(e.id, e.name, e.description, e.ruleIds) ?: return
        set { copy(valEditor = null) }
        if (run) runOnAll(id) else flash(if (e.id == null) "Validation saved" else "Validation updated")
    }
    fun askDeleteSaved(id: String?) = set { copy(valConfirmDelete = id) }
    fun deleteSaved() {
        val id = state.valConfirmDelete ?: return
        validationUseCases.delete(id)
        set { copy(valConfirmDelete = null) }
        flash("Validation deleted")
    }

    init {
        // When the session ends, forget everything on screen so the next user starts clean.
        viewModelScope.launch {
            observeAuthState().collect { if (it is AuthState.SignedOut) resetForNewSession() }
        }
        // Load the organisation's properties (all pages) into the table once its id is known.
        viewModelScope.launch { propertyUseCases.sync() }
        // The current property must be one of the loaded ones: fall back to the first.
        viewModelScope.launch {
            propertyUseCases.observe().collect { list ->
                if (list.isNotEmpty() && list.none { it.id == state.prop }) set { copy(prop = list.first().id) }
            }
        }
    }

    private fun resetForNewSession() {
        toastJob?.cancel()
        ScanService.Target.entries.forEach { scans.stop(it) }
        state = UiState()
    }

    init {
        // A few earlier runs so "Recent jobs" isn't empty on first open.
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        startJob("dups", assets.map { it.id }, "All assets", now - 74 * h, instant = true)
        startJob("full", assets.map { it.id }, "All assets", now - 50 * h, instant = true)
        startJob("quick", listOf(1, 2), "2 chosen assets", now - 21 * h, instant = true)
        startJob("quick", listOf(1), "1 chosen asset", now - 20 * h, instant = true)
    }

    // ---------- Configuration ----------

    fun openConfig() = set {
        copy(cfgOpen = true, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false, searchOpen = false, pick = emptySet())
    }
    fun closeConfig() = set { copy(cfgOpen = false, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS) }

    /** Switches the Configuration tab (Building info / Asset fields / Geofencing / Property Settings). */
    fun cfgGo(page: CfgPage) = set { copy(cfgPage = page) }

    // Building info: the property's building → floor → location tree (also used by the location picker).
    fun treeFor(prop: String = state.prop): List<LocNode> {
        trees.value // recompose when a tree is edited
        return locationUseCases.tree(prop)
    }

    fun addBuilding(name: String): String {
        val id = locationUseCases.addBuilding(state.prop, name)
        flash("Building added")
        return id
    }

    fun addFloor(buildingId: String, name: String): String {
        val id = locationUseCases.addFloor(state.prop, buildingId, name)
        flash("Floor added")
        return id
    }

    /** Add building sheet: a new building with its details. Returns its id. */
    fun addBuilding(name: String, info: BuildingInfo): String {
        val id = locationUseCases.addBuilding(state.prop, name, info)
        flash("Building added")
        return id
    }

    /** Edit building sheet. */
    fun saveBuilding(id: String, name: String, info: BuildingInfo) {
        locationUseCases.saveBuilding(state.prop, id, name, info)
        flash("Building saved")
    }

    /** Edit floors sheet: the building's floors in their new order, with names, types and codes; missing ones are removed. */
    fun saveFloors(buildingId: String, floors: List<LocNode>) {
        locationUseCases.saveFloors(state.prop, buildingId, floors)
        flash("Floors saved")
    }

    fun renameNode(id: String, name: String) {
        locationUseCases.rename(state.prop, id, name)
        flash("Renamed")
    }

    fun deleteNodes(ids: Set<String>, what: String) {
        if (locationUseCases.delete(state.prop, ids)) flash("$what deleted")
    }

    /** Adds one location, or several at once (bulk: one name per line; codes get -1, -2, … when given). */
    fun addLocations(floorId: String, names: List<String>, type: String, code: String) {
        val n = locationUseCases.addLocations(state.prop, floorId, names, type, code)
        if (n > 0) flash(plural(n, "location", "locations") + " added")
    }

    /**
     * Import buildings: rows of Building, Floor, Location, Type, Code (a header row is skipped) from a
     * CSV or Excel file. Buildings and floors are matched by name; locations already there are left alone.
     */
    fun importBuildings(uri: Uri) {
        val prop = state.prop
        viewModelScope.launch {
            when (val r = locationUseCases.importBuildings(prop, uri.toString())) {
                ImportBuildingsUseCase.Result.Unreadable -> flash("Couldn’t read that file")
                ImportBuildingsUseCase.Result.NothingFound -> flash("No buildings found · use columns Building, Floor, Location, Type, Code")
                is ImportBuildingsUseCase.Result.Imported ->
                    flash(if (r.added == 0) "Everything in the file is already here" else "Imported ${r.rows} rows")
            }
        }
    }

    fun updateLocation(id: String, name: String, type: String, code: String) {
        locationUseCases.updateLocation(state.prop, id, name, type, code)
        flash("Location saved")
    }

    // Asset fields and photos.
    val fieldCfg: List<FieldCfg> get() = assetConfig.value.fields
    val photoReqs: List<PhotoReq> get() = assetConfig.value.photoReqs
    val geoEnforce: Boolean get() = assetConfig.value.geoEnforce
    val requiredFields: List<Pair<String, String>> get() = assetConfig.value.requiredFields
    val minPhotos: Int get() = assetConfig.value.minPhotos

    fun toggleFieldVisible(key: String) {
        if (configUseCases.toggleVisible(key) == ToggleFieldVisibleUseCase.Result.MANDATORY) flash("Mandatory fields are always visible")
    }
    fun toggleFieldRequired(key: String) = configUseCases.toggleRequired(key)
    fun addField(label: String, kind: FieldKind, required: Boolean) {
        if (configUseCases.addField(label, kind, required)) flash("Field added")
    }
    fun removeField(key: String) {
        configUseCases.removeField(key)
        flash("Field removed")
    }

    fun setPhotoLimits(id: String, min: Int, max: Int) = configUseCases.setPhotoLimits(id, min, max)
    fun addPhotoReq(name: String, hint: String) {
        if (configUseCases.addPhotoReq(name, hint)) flash("Photo type added")
    }
    fun removePhotoReq(id: String) = configUseCases.removePhotoReq(id)

    fun setGeoEnforce(on: Boolean) = configUseCases.setGeoEnforce(on)

    /** How many choices a dropdown field offers ("Generic" / "Select" placeholders not counted). */
    fun optionCount(f: FieldCfg): Int = f.optionCount

    /** Field sheet: saves the whole field (mandatory makes it visible; the name field stays mandatory). */
    fun saveField(f: FieldCfg) {
        if (configUseCases.saveField(f)) flash("Field saved")
    }

    /** Add field sheet: adds the fields set up there, under More details. */
    fun addFields(list: List<FieldCfg>) {
        val n = configUseCases.addFields(list)
        if (n > 0) flash(plural(n, "field", "fields") + " added")
    }

    /**
     * Import from Excel: one field per column, its kind guessed from the values. Columns that match a
     * field already here are skipped. [onReady] gets the new fields to review.
     */
    fun importFields(uri: Uri, onReady: (List<FieldCfg>) -> Unit) {
        viewModelScope.launch {
            when (val r = configUseCases.importFields(uri.toString())) {
                ImportFieldsUseCase.Result.Unreadable -> flash("Couldn’t read that file")
                ImportFieldsUseCase.Result.NothingNew -> flash("No new fields in that file · every column is already a field")
                is ImportFieldsUseCase.Result.Fields -> onReady(r.fields)
            }
        }
    }

    /** Checks sheet: what validation looks for in the field. */
    fun setFieldChecks(key: String, flagEmpty: Boolean, rule: ValueRule, min: String, max: String) {
        configUseCases.setChecks(key, flagEmpty, rule, min, max)
        flash("Checks saved")
    }

    fun setFieldOptions(key: String, options: List<String>) {
        configUseCases.setOptions(key, options)
        flash("Options saved")
    }

    // Property Settings: which ways of adding assets the phone offers for this property.
    fun onboardingOn(t: Tab): Boolean = t.channel?.let { it !in onboardingOff.value[state.prop].orEmpty() } ?: true

    fun setOnboarding(t: Tab, on: Boolean) {
        val channel = t.channel ?: return
        propertyUseCases.setOnboarding(state.prop, channel, on)
        flash((if (t == Tab.BT) "Bluetooth" else "Wi-Fi") + " onboarding " + if (on) "on" else "off")
    }
}
