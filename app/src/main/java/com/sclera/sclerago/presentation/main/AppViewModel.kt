package com.sclera.sclerago.presentation.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.LocPick
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.model.captureName
import com.sclera.sclerago.domain.model.scanKey
import com.sclera.sclerago.domain.model.timeLabel
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.validation.ObserveValidationRunCountUseCase
import com.sclera.sclerago.domain.usecase.validation.ValidationAccessUseCase
import com.sclera.sclerago.domain.usecase.integration.IntegrationAccessUseCase
import com.sclera.sclerago.domain.usecase.location.LocationUseCases
import com.sclera.sclerago.domain.usecase.media.FileUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.model.viaRes
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.services.media.formatSize
import com.sclera.sclerago.services.scan.ScanService
import com.sclera.sclerago.services.scan.ScanServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Tab { BT, NET, ASSETS, CAMERA, FILE }
enum class LocFilter { ALL, SPEC, NONE }
enum class Sheet { NONE, LOC }
enum class SheetFor { FILTER, ASSET, BULK }
enum class NetMode { WIFI, AP }
enum class MenuItem { PROPERTY, USERS, ASSETS, VALIDATION, CONFIGURATION, AUDIT, SURVEY, INTEGRATION }
enum class CfgPage { BUILDING, FIELDS, GEO, SETTINGS }
enum class AdminPage { PROPERTIES, PEOPLE }


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
    /** The location chosen for the lists' location tab (empty until one is picked). */
    val specLocs: List<String> = emptyList(),
    val specPath: List<String> = emptyList(),
    val specWhere: String = "",
    val specSel: String = "",
    val sheet: Sheet = Sheet.NONE,
    val sheetFor: SheetFor = SheetFor.FILTER,
    /** A place picked for the open asset, waiting for its details to take it as an unsaved change. */
    val detailPlace: LocValue? = null,
    /** Scanned devices ticked on the Bluetooth / Network scan list, to add as assets together. */
    val devPick: Set<String> = emptySet(),
    /**
     * Select all is on: every device that isn't an asset yet is ticked, including ones the scan finds
     * later, except those in [devSkip] (unticked by hand).
     */
    val devAll: Boolean = false,
    val devSkip: Set<String> = emptySet(),
    val pv: Preview? = null,
    val assetId: String? = null,
    val newAsset: Boolean = false,
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
    val toast: UiText = UiText.Raw(""),
    val toastOn: Boolean = false,
    /** Side menu (header menu button). */
    val menuOpen: Boolean = false,
    // ---- Validation page
    val valOpen: Boolean = false,
    // ---- Configuration page
    val cfgOpen: Boolean = false,
    // ---- Integration page
    val intOpen: Boolean = false,
    val cfgPage: CfgPage = CfgPage.FIELDS,
    // ---- Property list / People and access (side menu)
    val adminPage: AdminPage? = null,
)

data class ReviewItem(
    val id: String,
    val name: String,
    val sub: UiText,
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
    private val validationAccess: ValidationAccessUseCase,
    private val integrationAccess: IntegrationAccessUseCase,
    observeRunCount: ObserveValidationRunCountUseCase,
    private val fileUseCases: FileUseCases,
    private val downloads: PropertyDownloadUseCases,
) : ViewModel() {
    var state by mutableStateOf(UiState())
        private set

    private fun set(f: UiState.() -> UiState) {
        state = state.f()
    }

    // The data, as the screens read it (recomposing when it changes).
    private val catalog = assetUseCases.observe().observedIn(viewModelScope)
    private val propertyFlow = propertyUseCases.observe().observedIn(viewModelScope)
    private val downloadFlow = downloads.observe().observedIn(viewModelScope)
    private val onboardingOff = propertyUseCases.observeOnboarding().observedIn(viewModelScope)
    private val tree = locationUseCases.observeTree().observedIn(viewModelScope)
    private val runCount = observeRunCount().observedIn(viewModelScope)

    /** Makes a change to the offline data in the background; when it is refused or fails the user is told why (never the details). */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: DataException) {
                flash(e.error.toUiText())
            }
        }
    }

    private var toastJob: Job? = null
    fun flash(msg: UiText) {
        set { copy(toast = msg, toastOn = true) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2600)
            set { copy(toastOn = false) }
        }
    }

    /** Message not yet moved to string resources (Validation and Configuration pages). */
    fun flash(msg: String) = flash(UiText.Raw(msg))

    // ---------- Derived data ----------

    /** The assets of the downloaded property: the one database is the property being worked on, whichever sample property is picked. */
    val assets: List<Asset> get() = catalog.value.assets

    val properties: List<Property> get() = propertyFlow.value

    /** Which assets the lists show by location. */
    val locationFilter: LocationFilter
        get() = when (state.loc) {
            LocFilter.ALL -> LocationFilter.All
            LocFilter.SPEC -> LocationFilter.Specific(state.specLocs)
            LocFilter.NONE -> LocationFilter.Unmapped
        }

    /** The place the location filter picked, as building / floor / location ids (the whole property: none), for the Managed Assets Summary. */
    val placeScope: PlaceScope
        get() {
            val node = if (state.loc == LocFilter.SPEC) treeFor().findNode(state.specSel) else null
            return when (node?.kind) {
                NodeKind.BUILDING -> PlaceScope(buildingIds = listOf(node.id))
                NodeKind.FLOOR -> PlaceScope(floorIds = listOf(node.id))
                NodeKind.LOCATION -> PlaceScope(locationIds = listOf(node.id))
                null -> PlaceScope()
            }
        }

    /** Where new assets go: the location chosen in the filter (its name, its "where" text and its id), else nowhere. */
    private val tgtLoc: String?
        get() = if (state.loc == LocFilter.SPEC) state.specLocs.singleOrNull() ?: state.specPath.lastOrNull() else null
    private val tgtWhere: String get() = if (state.loc == LocFilter.SPEC) state.specWhere else ""
    private val tgtPlace: String? get() = if (state.loc == LocFilter.SPEC) state.specSel.ifEmpty { null } else null

    /** The property being worked on: the downloaded one opened last; empty until a property is downloaded. */
    val currentProp: String get() = downloadFlow.value.currentId.orEmpty()

    /** A property is downloaded, so its assets, sections and side-menu card can show. */
    val hasProperty: Boolean get() = currentProp.isNotEmpty()

    val propName: String get() = properties.firstOrNull { it.id == currentProp }?.name.orEmpty()
    /** The location tab path, or null when no location is chosen yet. */
    val specPathShown: List<String>? get() = state.specPath.takeIf { it.isNotEmpty() }

    val detailAsset: Asset?
        get() = if (state.newAsset) {
            val cam = state.newMedia.firstOrNull()
            Asset(
                "", cam?.let { captureName(it) } ?: "", "", tgtLoc, tgtWhere,
                state.newSrc, Status.DRAFT, AssetType.GENERIC, currentProp,
                media = state.newMedia,
            )
        } else state.assetId?.let { id -> assets.firstOrNull { it.id == id } }

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

    /** The asset lists are on screen and nothing covers them (so selecting and searching make sense). */
    val listInteractive: Boolean get() = listy && !showDetail && !sheetOpen

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
                sub = if (m.video) UiText.Res(R.string.review_video_sub, listOf(m.durationText)) else UiText.Res(R.string.review_photo_sub),
                ready = false, on = m.id !in state.revOff, media = m,
            )
        }

    // Files
    val fileReview: List<ReviewItem>
        get() = state.importFiles.map { f ->
            ReviewItem(
                id = f.id, name = f.baseName, sub = UiText.Res(R.string.review_file_sub, listOf(f.name, formatSize(f.size))), ready = false,
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
            var o = copy(tab = t, assetId = null, newAsset = false, newMedia = emptyList(), devPick = emptySet(), devAll = false, devSkip = emptySet())
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
    val navTabs: List<Tab> get() = Tab.entries - onboardingOff.value[currentProp].orEmpty().map { it.tab }.toSet()

    /** Shake gesture: jump straight to the live camera from anywhere. */
    fun openCameraByShake() {
        val s = state
        if (s.tab == Tab.CAMERA && s.camOpen && !s.revCam && s.sheet == Sheet.NONE && s.pv == null) return
        goTab(Tab.CAMERA)
        set { copy(sheet = Sheet.NONE, pv = null) }
    }

    fun tapLocTab(f: LocFilter) {
        val cur = state.loc
        when (f) {
            LocFilter.ALL -> if (cur == LocFilter.ALL) openProp() else set { copy(loc = LocFilter.ALL, assetId = null) }
            // No location chosen yet: the tab opens the picker straight away.
            LocFilter.SPEC -> if (cur == LocFilter.SPEC || state.specLocs.isEmpty()) openLocFilter() else set { copy(loc = LocFilter.SPEC, assetId = null) }
            LocFilter.NONE -> set { copy(loc = LocFilter.NONE, assetId = null) }
        }
    }

    /** Another property is chosen (and downloaded) on the Property list. */
    fun openProp() = openAdmin(AdminPage.PROPERTIES)

    // Side menu
    fun openMenu() = set { copy(menuOpen = true) }
    fun closeMenu() = set { copy(menuOpen = false) }
    fun pickMenu(item: MenuItem) {
        closeMenu()
        when (item) {
            MenuItem.PROPERTY -> openAdmin(AdminPage.PROPERTIES)
            MenuItem.USERS -> openAdmin(AdminPage.PEOPLE)
            MenuItem.ASSETS -> { closeAdmin(); goTab(Tab.ASSETS) }
            MenuItem.VALIDATION -> { closeAdmin(); openValidation() }
            MenuItem.CONFIGURATION -> { closeAdmin(); openConfig() }
            MenuItem.AUDIT -> comingSoon(R.string.menu_audit)
            MenuItem.SURVEY -> comingSoon(R.string.menu_survey)
            MenuItem.INTEGRATION -> { closeAdmin(); openIntegration() }
        }
    }

    private fun comingSoon(@StringRes label: Int) = flash(UiText.Res(R.string.menu_coming_soon, listOf(UiText.Res(label))))

    // Property list / People and access
    fun openAdmin(page: AdminPage) = set {
        copy(adminPage = page, menuOpen = false, valOpen = false, cfgOpen = false, intOpen = false, assetId = null, newAsset = false)
    }
    /** Back to the assets; without a downloaded property there are none, so the Property list stays. */
    fun closeAdmin() = set { copy(adminPage = if (hasProperty) null else AdminPage.PROPERTIES) }

    /**
     * Opening a downloaded property from the list makes it the current one and shows its assets (properties that
     * aren't downloaded are downloaded first).
     */
    fun openPropertyFromList(id: String) {
        if (id !in downloadFlow.value.downloadedIds) return
        if (id != currentProp) downloads.open(id)
        closeAdmin()
        goTab(Tab.ASSETS)
    }

    fun openLocFilter() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.FILTER) }
    fun openLocAsset() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.ASSET) }
    /** Edit on the selection: a location for the selected assets (the web's bulk Edit). */
    fun editSelLocation() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.BULK) }
    fun closeLoc() = set { copy(sheet = Sheet.NONE) }

    /** The open asset's details took the place picked for it. */
    fun detailPlaceTaken() = set { copy(detailPlace = null) }

    /** A location chosen for the open asset or for the lists' location filter (the selection's goes to the bulk Edit sheet, [BulkEditViewModel.setPlace]). */
    fun pickLoc(r: LocPick) {
        val v = locValue(r)
        val detail = detailAsset
        if (state.sheetFor == SheetFor.ASSET && detail != null && detail.id.isNotEmpty()) {
            // Not written yet: the details keep it with their other changes until Save.
            set { copy(sheet = Sheet.NONE, detailPlace = v) }
        } else set {
            copy(
                sheet = Sheet.NONE, loc = LocFilter.SPEC, specLocs = r.locs, specPath = r.path.ifEmpty { listOf(r.label) },
                specWhere = r.where, specSel = r.id, assetId = null,
            )
        }
    }

    /** What a picked location gives an asset. */
    fun locValue(r: LocPick) = LocValue(if (r.locs.size == 1) r.locs[0] else r.label, r.where, r.id)

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
        clearDevicePick()
        addDevices(picked)
    }

    /**
     * A scanned device: open its asset if it already has one (a tap on the row), otherwise add it straight away as a
     * draft (its + button), as Add does for ticked devices (no form to fill in first; tap it then to open the new asset).
     */
    fun openDevice(d: Device) {
        val existing = assetUseCases.matchDevices(listOf(d), scanSource, assets)[d.id]
        if (existing != null) set { copy(assetId = existing, newAsset = false) } else addDevices(listOf(d))
    }

    /** Adds [devices] as draft assets in the current place, filled in from each device (name, type, BLE tag / IP, a note). */
    private fun addDevices(devices: List<Device>) {
        val src = scanSource
        val place = AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
        write {
            devices.forEach { d ->
                assetUseCases.create(
                    deviceName(d), "", emptyList(), prefillFor(d, src), false,
                    place = place, captures = emptyList(), device = d, deviceSource = src,
                )
            }
            val added = plural(devices.size, "asset", "assets") + if (devices.size == 1) " added as a draft" else " added as drafts"
            flash(UiText.Res(R.string.notice_with_source, listOf(UiText.Raw(added), UiText.Res(src.viaRes))))
        }
    }

    fun openAsset(id: String) = set { copy(assetId = id, newAsset = false) }

    fun openNew() = set { copy(newAsset = true, newSrc = Source.MANUAL, newMedia = emptyList(), assetId = null) }
    fun closeDetail() {
        // A camera shot that never became an asset is thrown away with the form.
        if (state.newAsset) fileUseCases.deleteOwned(state.newMedia)
        set { copy(assetId = null, newAsset = false, newMedia = emptyList()) }
    }

    /** Camera tab: open the new-asset details with these photos/videos already attached. */
    private fun openNewWithMedia(media: List<Media>) {
        if (media.isEmpty()) return
        set {
            copy(
                newAsset = true, newSrc = Source.CAMERA, newMedia = media, assetId = null,
            )
        }
    }

    fun createAsset(
        name: String,
        detail: String,
        media: List<Media> = emptyList(),
        values: Map<String, String> = emptyMap(),
        submit: Boolean = false,
        exception: Boolean = false,
    ): CreateAssetUseCase.Via {
        val s = state
        // The form is for camera shots and hand-made assets; scanned devices are added directly (addDevices).
        val via = assetUseCases.create.viaFor(s.newMedia, null)
        val place = AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
        val savedAs = UiText.Res(
            when {
                !submit -> R.string.asset_added_draft
                exception -> R.string.asset_exception_notice
                else -> R.string.asset_added_submitted
            },
        )
        when (via) {
            // From the Camera tab: stay on the camera for the next one.
            CreateAssetUseCase.Via.CAMERA -> set { copy(newAsset = false, newMedia = emptyList()) }
            CreateAssetUseCase.Via.MANUAL, CreateAssetUseCase.Via.SCAN -> set { copy(newAsset = false, tab = Tab.ASSETS) }
        }
        write {
            val created = assetUseCases.create(
                name, detail, media, values, submit, place,
                captures = s.newMedia, device = null, deviceSource = s.newSrc,
            )
            when (created.via) {
                CreateAssetUseCase.Via.CAMERA -> flash(UiText.Res(R.string.notice_with_source, listOf(savedAs, UiText.Res(Source.CAMERA.viaRes))))
                CreateAssetUseCase.Via.SCAN -> flash(UiText.Res(R.string.notice_with_source, listOf(savedAs, UiText.Res(created.source.viaRes))))
                CreateAssetUseCase.Via.MANUAL -> flash(savedAs)
            }
        }
        return via
    }

    fun deleteAsset(id: String) {
        set { copy(assetId = null) }
        write {
            assetUseCases.delete(listOf(id))
            flash(UiText.Res(R.string.asset_deleted))
        }
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
        val prop = currentProp
        val loc = tgtLoc
        val where = tgtWhere
        val place = tgtPlace
        val used = picked.map { it.id }.toSet()
        set {
            copy(
                revOff = emptySet(), revCam = false, camOpen = false, camSel = camSel - used,
                captures = captures.filter { it.id !in used },
            )
        }
        write {
            val made = assetUseCases.add(picked.size) { id, i ->
                val r = picked[i]
                val m = r.media!!
                Asset(
                    id, r.name, (if (m.video) "Video ${m.durationText}" else "Photo") + " · captured " + timeLabel(m.createdAt).take(5),
                    loc, where, Source.CAMERA, Status.DRAFT, AssetType.GENERIC, prop, listOf(m), locationId = place,
                )
            }
            flash(UiText.Plural(R.plurals.assets_added_details, made.size))
        }
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
        val prop = currentProp
        val loc = tgtLoc
        val where = tgtWhere
        val place = tgtPlace
        set { copy(revOff = emptySet(), revFile = false, fileOpen = false, importFiles = emptyList()) }
        write {
            val made = assetUseCases.add(picked.size) { id, i ->
                val r = picked[i]
                Asset(id, r.name, r.file, loc, where, Source.FILE, Status.DRAFT, typeOf(r.name), prop, locationId = place)
            }
            flash(UiText.Plural(R.plurals.assets_imported_details, made.size))
        }
    }

    // ---------- Validation ----------

    /** The signed-in user may run validations (the server's check permission; validate includes it). */
    val canCheck: Boolean get() = validationAccess.canCheck()

    /** How many validation runs the property has (the side menu's count), as last synced. */
    val validationRunCount: Int get() = runCount.value

    // Side-menu pages always return to the Assets tab. It is also selected on open, so the
    // pager slides there hidden behind the page rather than visibly on close.
    fun openValidation() = set {
        copy(valOpen = true, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false)
    }
    fun closeValidation() = set { copy(valOpen = false, tab = Tab.ASSETS, assetId = null, newAsset = false) }

    // ---------- Integration ----------

    /** The signed-in user may configure the property (the server's configure permission): the Integration page shows only then. */
    val canConfigure: Boolean get() = integrationAccess()

    fun openIntegration() = set {
        copy(intOpen = true, valOpen = false, cfgOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false)
    }
    fun closeIntegration() = set { copy(intOpen = false, tab = Tab.ASSETS) }

    init {
        // When the session ends, forget everything on screen so the next user starts clean.
        viewModelScope.launch {
            observeAuthState().map { it is AuthState.SignedIn }.distinctUntilChanged().collect { signedIn ->
                if (signedIn) openRecentProperty() else resetForNewSession()
            }
        }
        // A location belongs to its property: when the property changes, the one chosen is forgotten.
        viewModelScope.launch {
            downloads.observe().map { it.currentId }.distinctUntilChanged().collect {
                set { copy(loc = LocFilter.ALL, specLocs = emptyList(), specPath = emptyList(), specWhere = "", specSel = "", assetId = null) }
                if (state.tab !in navTabs) goTab(Tab.ASSETS)
            }
        }
    }

    /**
     * After sign-in: the app opens on the recently used property's assets and reopens its data, or on the Property
     * list when no property has been downloaded yet.
     */
    private fun openRecentProperty() {
        viewModelScope.launch {
            if (downloads.observe().first { it.restored }.currentId == null) openAdmin(AdminPage.PROPERTIES)
            // Every downloaded property syncs again, the current one included.
            downloads.resume()
        }
    }

    private fun resetForNewSession() {
        toastJob?.cancel()
        ScanService.Target.entries.forEach { scans.stop(it) }
        state = UiState()
    }

    // ---------- Configuration ----------

    /** Opens Configuration, only for someone who may configure the property (the web hides it otherwise). */
    fun openConfig() = if (!canConfigure) Unit else set {
        copy(cfgOpen = true, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false)
    }
    fun closeConfig() = set { copy(cfgOpen = false, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS) }

    /** Switches the Configuration tab (Building info / Asset fields / Geofencing / Property Settings). */
    fun cfgGo(page: CfgPage) = set { copy(cfgPage = page) }

    // The downloaded property's building → floor → location tree, for the location picker.
    fun treeFor(): List<LocNode> = tree.value

    // Building info, Asset fields, photo rules, geofence and AI in validation are read and changed at the server by
    // presentation/config's view models (FieldsConfigViewModel, PlacesConfigViewModel, ConfigViewModel).

    // Property Settings: which ways of adding assets the phone offers for this property.

    /** Switches being saved, with the value they show meanwhile (as the web: they change at once and go back on failure). */
    private var onboardingPending by mutableStateOf(emptyMap<OnboardingChannel, Boolean>())

    fun onboardingOn(t: Tab): Boolean = t.channel?.let { c -> onboardingPending[c] ?: (c !in onboardingOff.value[currentProp].orEmpty()) } ?: true

    /** A switch is being saved, so it doesn't take another tap until the server answers. */
    fun onboardingSaving(t: Tab): Boolean = t.channel in onboardingPending

    fun setOnboarding(t: Tab, on: Boolean) {
        val channel = t.channel ?: return
        if (channel in onboardingPending) return
        val prop = currentProp
        onboardingPending = onboardingPending + (channel to on)
        viewModelScope.launch {
            try {
                propertyUseCases.setOnboarding(prop, channel, on)
                flash((if (t == Tab.BT) "Bluetooth" else "Wi-Fi") + " onboarding " + if (on) "on" else "off")
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            } finally {
                // Saved: the server's settings now show; failed: the switch goes back to them.
                onboardingPending = onboardingPending - channel
            }
        }
    }
}
