package com.sclera.sclerago.presentation.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.LocPick
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.model.OnlineException
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
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.validation.ValidationUseCases
import com.sclera.sclerago.domain.usecase.validation.forProperty
import com.sclera.sclerago.domain.usecase.validation.lastRun
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.TEMPLATES
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.ValScope
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
import kotlinx.coroutines.launch
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
data class RunSheet(val sourceId: String = "quick", val scope: ValScope = ValScope.ALL, val chosen: Set<String> = emptySet())

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
    val specLocs: List<String> = emptyList(),
    val specPath: List<String> = emptyList(),
    val specWhere: String = "",
    val specSel: String = "",
    val sheet: Sheet = Sheet.NONE,
    val sheetFor: SheetFor = SheetFor.FILTER,
    val prop: String = "",
    val propQ: String = "",
    val pv: Preview? = null,
    val assetId: String? = null,
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
    val toast: UiText = UiText.Raw(""),
    val toastOn: Boolean = false,
    /** Side menu (header menu button). */
    val menuOpen: Boolean = false,
    // ---- Validation page
    val valOpen: Boolean = false,
    val valSheet: RunSheet? = null,
    val valPicker: Boolean = false,
    /** Template / saved validation whose list of checks is shown. */
    val valChecksFor: String? = null,
    val valReview: String? = null,
    val valEditor: SavedEditor? = null,
    val valConfirmDelete: String? = null,
    // ---- Configuration page
    val cfgOpen: Boolean = false,
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
    private val tree = locationUseCases.observeTree().observedIn(viewModelScope)
    private val assetConfig = configUseCases.observe().observedIn(viewModelScope)
    private val jobs = validationUseCases.observeJobs().observedIn(viewModelScope)
    private val saved = validationUseCases.observeSaved().observedIn(viewModelScope)

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

    /** Where new assets go: the location chosen in the filter (its name, its "where" text and its id), else nowhere. */
    private val tgtLoc: String?
        get() = if (state.loc == LocFilter.SPEC) state.specLocs.singleOrNull() ?: state.specPath.lastOrNull() else null
    private val tgtWhere: String get() = if (state.loc == LocFilter.SPEC) state.specWhere else ""
    private val tgtPlace: String? get() = if (state.loc == LocFilter.SPEC) state.specSel.ifEmpty { null } else null

    /** The property being worked on: the one picked, else the first of the list (none while the list is empty). */
    val currentProp: String get() = properties.firstOrNull { it.id == state.prop }?.id ?: properties.firstOrNull()?.id ?: state.prop

    val propName: String get() = properties.firstOrNull { it.id == currentProp }?.name.orEmpty()
    /** The location tab path, or null when no location is chosen yet. */
    val specPathShown: List<String>? get() = state.specPath.takeIf { it.isNotEmpty() }

    val detailAsset: Asset?
        get() = if (state.newAsset) {
            val d = state.newDevice
            val cam = state.newMedia.firstOrNull()
            Asset(
                "", d?.let { deviceName(it) } ?: cam?.let { captureName(it) } ?: "", d?.sub ?: "", tgtLoc, tgtWhere,
                state.newSrc, Status.DRAFT, d?.type ?: AssetType.GENERIC, currentProp,
                media = state.newMedia,
            )
        } else state.assetId?.let { id -> assets.firstOrNull { it.id == id } }

    /** Form values filled in from the scanned device the new asset comes from. */
    val newPrefill: Map<String, String>
        get() {
            val d = state.newDevice ?: return emptyMap()
            val m = mutableMapOf("note" to "${state.newSrc.label}: ${d.name} · ${d.sub}")
            when (state.newSrc) {
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

    fun propRows(): List<Pair<Property, String>> {
        val ql = state.propQ.trim().lowercase()
        val n = catalog.value.assets.size
        return properties.filter { ql.isEmpty() || it.name.lowercase().contains(ql) }.map { p ->
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
        get() = btDevices.let { d -> scans.card(ScanService.Target.BLUETOOTH, d.size, d.count { it.isNew }) }

    val netCard: ScanService.Card
        get() = netDevices.let { d -> scans.card(netTarget, d.size, d.count { it.isNew }) }

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

    val btDevices: List<DeviceRow> get() = rows(scans.rows(ScanService.Target.BLUETOOTH), Source.BT)
    val netDevices: List<DeviceRow>
        get() = rows(scans.rows(netTarget), if (state.netMode == NetMode.AP) Source.AP else Source.WIFI)

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
            var o = copy(tab = t, assetId = null, newAsset = false, newDevice = null, newMedia = emptyList())
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
            LocFilter.SPEC -> if (cur == LocFilter.SPEC || state.specLocs.isEmpty()) openLocFilter() else set { copy(loc = LocFilter.SPEC, assetId = null) }
            LocFilter.NONE -> set { copy(loc = LocFilter.NONE, assetId = null) }
        }
    }

    fun openProp() = set { copy(sheet = Sheet.PROP) }

    // Side menu
    fun openMenu() = set { copy(menuOpen = true) }
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
            MenuItem.AUDIT -> comingSoon(R.string.menu_audit)
            MenuItem.SURVEY -> comingSoon(R.string.menu_survey)
            MenuItem.INTEGRATION -> comingSoon(R.string.menu_integration)
        }
    }

    private fun comingSoon(@StringRes label: Int) = flash(UiText.Res(R.string.menu_coming_soon, listOf(UiText.Res(label))))

    // Property list / People and access
    fun openAdmin(page: AdminPage) = set {
        copy(adminPage = page, menuOpen = false, valOpen = false, cfgOpen = false, assetId = null, newAsset = false)
    }
    fun closeAdmin() = set { copy(adminPage = null) }

    /** Opening a property from the list makes it the current one and shows its assets. */
    fun openPropertyFromList(id: String) {
        closeAdmin()
        pickProp(id)
        goTab(Tab.ASSETS)
    }
    fun closeProp() = set { copy(sheet = Sheet.NONE, propQ = "") }
    fun setPropQ(v: String) = set { copy(propQ = v) }
    fun pickProp(id: String) {
        val changed = id != currentProp
        set { copy(prop = id, sheet = Sheet.NONE, loc = LocFilter.ALL, assetId = null, propQ = "") }
        // The new property may not onboard over the tab that's open.
        if (state.tab !in navTabs) goTab(Tab.ASSETS)
        if (changed) flash(UiText.Res(R.string.property_showing, listOf(propName)))
    }

    fun openLocFilter() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.FILTER) }
    fun openLocAsset() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.ASSET) }
    fun assignSel() = set { copy(sheet = Sheet.LOC, sheetFor = SheetFor.BULK) }
    fun closeLoc() = set { copy(sheet = Sheet.NONE) }

    /** A location chosen for the open asset or for the lists' location filter (the selection is the list's, see [AssetListViewModel.assignSelected]). */
    fun pickLoc(r: LocPick) {
        val v = locValue(r)
        val detail = detailAsset
        if (state.sheetFor == SheetFor.ASSET && detail != null && detail.id.isNotEmpty()) {
            set { copy(sheet = Sheet.NONE) }
            write { assetUseCases.assignLocation(listOf(detail.id), v) }
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
        set { copy(btScan = scan) }
        if (scan) scans.start(ScanService.Target.BLUETOOTH, auto = true)
    }
    fun setNetScan(scan: Boolean) {
        set { copy(netScan = scan) }
        if (scan) scans.start(netTarget, auto = true)
    }
    fun setNetMode(m: NetMode) {
        set { copy(netMode = m) }
        if (state.netScan) scans.start(netTarget, auto = true)
    }
    /** Tap on the radar card: pause a running scan, or start a new one. */
    fun toggleBt() = scans.toggle(ScanService.Target.BLUETOOTH)
    fun toggleNet() = scans.toggle(netTarget)
    /** Tap on a scanned device: open its asset if it already has one, otherwise the add form prefilled from it. */
    fun openDevice(d: Device) {
        val src = scanSource
        val existing = assetUseCases.matchDevices(listOf(d), src, assets)[d.id]
        set {
            if (existing != null) copy(assetId = existing, newAsset = false, newDevice = null)
            else copy(newAsset = true, newDevice = d, newSrc = src, newMedia = emptyList(), assetId = null)
        }
    }

    fun openAsset(id: String) = set { copy(assetId = id, newAsset = false) }

    fun openNew() = set { copy(newAsset = true, newDevice = null, newSrc = Source.MANUAL, newMedia = emptyList(), assetId = null) }
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
            )
        }
    }

    fun createAsset(
        name: String,
        detail: String,
        media: List<Media> = emptyList(),
        values: Map<String, String> = emptyMap(),
        submit: Boolean = false,
    ): CreateAssetUseCase.Via {
        val s = state
        val via = assetUseCases.create.viaFor(s.newMedia, s.newDevice)
        val place = AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
        val savedAs = UiText.Res(if (submit) R.string.asset_added_submitted else R.string.asset_added_draft)
        when (via) {
            // From the Camera tab: stay on the camera for the next one.
            CreateAssetUseCase.Via.CAMERA -> set { copy(newAsset = false, newMedia = emptyList()) }
            // From a scan: stay on the scan.
            CreateAssetUseCase.Via.SCAN -> set { copy(newAsset = false, newDevice = null) }
            CreateAssetUseCase.Via.MANUAL -> set { copy(newAsset = false, tab = Tab.ASSETS) }
        }
        write {
            val created = assetUseCases.create(
                name, detail, media, values, submit, place,
                captures = s.newMedia, device = s.newDevice, deviceSource = s.newSrc,
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
                    loc, where, Source.CAMERA, Status.INPUT, AssetType.GENERIC, prop, listOf(m), locationId = place,
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
                Asset(id, r.name, r.file, loc, where, Source.FILE, Status.INPUT, typeOf(r.name), prop, locationId = place)
            }
            flash(UiText.Plural(R.plurals.assets_imported_details, made.size))
        }
    }

    // ---------- Validation ----------

    /** The user's saved validations. */
    val savedVals: List<SavedValidation> get() = saved.value

    fun sourceName(id: String): String = validationUseCases.source.name(id)

    fun sourceRules(id: String): List<Rule> = validationUseCases.source.rules(id)

    /** Jobs of the downloaded property (the server's and the ones run on this device), newest first. */
    val propJobs: List<ValJob> get() = jobs.value.forProperty(currentProp)

    fun lastRun(sourceId: String): Long? = jobs.value.lastRun(currentProp, sourceId)

    /** Assets a run with [sheet]'s settings would cover. */
    fun scopeAssets(sheet: RunSheet): List<Asset> {
        jobs.value // "New or changed" depends on the last run and on edits: recompose when either changes.
        return validationUseCases.selectTargets(sheet.scope, sheet.chosen, sheet.sourceId, currentProp, assets)
    }

    // Side-menu pages always return to the Assets tab. It is also selected on open, so the
    // pager slides there hidden behind the page rather than visibly on close.
    fun openValidation() = set {
        copy(valOpen = true, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false)
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
    fun toggleChosen(id: String) = set {
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
    private fun startJob(sourceId: String, ids: List<String>, label: String) {
        flash("Validation started · runs in the background")
        validationUseCases.start(
            sourceId, ids, label, currentProp,
            inputs = { assetUseCases.checkInput(assets) },
            onDone = { j -> flash("${j.title} finished · ${j.failed} failed · ${j.passed} passed") },
        )
    }

    fun openChecks(id: String?) = set { copy(valChecksFor = id) }
    fun openReview(jobId: String?) = set { copy(valReview = jobId) }

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
        write {
            val id = validationUseCases.save(e.id, e.name, e.description, e.ruleIds) ?: return@write
            set { copy(valEditor = null) }
            if (run) runOnAll(id) else flash(if (e.id == null) "Validation saved" else "Validation updated")
        }
    }
    fun askDeleteSaved(id: String?) = set { copy(valConfirmDelete = id) }
    fun deleteSaved() {
        val id = state.valConfirmDelete ?: return
        set { copy(valConfirmDelete = null) }
        write {
            validationUseCases.delete(id)
            flash("Validation deleted")
        }
    }

    init {
        // When the session ends, forget everything on screen so the next user starts clean.
        viewModelScope.launch {
            observeAuthState().collect { if (it is AuthState.SignedOut) resetForNewSession() }
        }
    }

    private fun resetForNewSession() {
        toastJob?.cancel()
        ScanService.Target.entries.forEach { scans.stop(it) }
        state = UiState()
    }

    // ---------- Configuration ----------

    fun openConfig() = set {
        copy(cfgOpen = true, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false)
    }
    fun closeConfig() = set { copy(cfgOpen = false, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS) }

    /** Switches the Configuration tab (Building info / Asset fields / Geofencing / Property Settings). */
    fun cfgGo(page: CfgPage) = set { copy(cfgPage = page) }

    // Building info: the downloaded property's building → floor → location tree (also used by the location picker).
    fun treeFor(): List<LocNode> = tree.value

    /** Adds a building (with its details when given); [onAdded] gets its id. */
    fun addBuilding(name: String, info: BuildingInfo? = null, onAdded: (String) -> Unit = {}) = write {
        onAdded(locationUseCases.addBuilding(name, info))
        flash("Building added")
    }

    /** Adds a floor to a building; [onAdded] gets its id. */
    fun addFloor(buildingId: String, name: String, onAdded: (String) -> Unit = {}) = write {
        onAdded(locationUseCases.addFloor(buildingId, name))
        flash("Floor added")
    }

    /** Edit building sheet. */
    fun saveBuilding(id: String, name: String, info: BuildingInfo) = write {
        locationUseCases.saveBuilding(id, name, info)
        flash("Building saved")
    }

    /** Edit floors sheet: the building's floors in their new order, with names, types and codes; missing ones are removed. */
    fun saveFloors(buildingId: String, floors: List<LocNode>) = write {
        locationUseCases.saveFloors(buildingId, floors)
        flash("Floors saved")
    }

    fun renameNode(id: String, name: String) = write {
        locationUseCases.rename(id, name)
        flash("Renamed")
    }

    fun deleteNodes(ids: Set<String>, what: String) = write {
        if (locationUseCases.delete(ids)) flash("$what deleted")
    }

    /** Adds one location, or several at once (bulk: one name per line; codes get -1, -2, … when given). */
    fun addLocations(floorId: String, names: List<String>, type: String, code: String) = write {
        val n = locationUseCases.addLocations(floorId, names, type, code)
        if (n > 0) flash(plural(n, "location", "locations") + " added")
    }

    /**
     * Import buildings: rows of Building, Floor, Location, Type, Code (a header row is skipped) from a
     * CSV or Excel file. Buildings and floors are matched by name; locations already there are left alone.
     */
    fun importBuildings(uri: Uri) {
        write {
            when (val r = locationUseCases.importBuildings(uri.toString())) {
                ImportBuildingsUseCase.Result.Unreadable -> flash("Couldn’t read that file")
                ImportBuildingsUseCase.Result.NothingFound -> flash("No buildings found · use columns Building, Floor, Location, Type, Code")
                is ImportBuildingsUseCase.Result.Imported ->
                    flash(if (r.added == 0) "Everything in the file is already here" else "Imported ${r.rows} rows")
            }
        }
    }

    fun updateLocation(id: String, name: String, type: String, code: String) = write {
        locationUseCases.updateLocation(id, name, type, code)
        flash("Location saved")
    }

    // Asset fields and photos.
    val fieldCfg: List<FieldCfg> get() = assetConfig.value.fields
    val photoReqs: List<PhotoReq> get() = assetConfig.value.photoReqs
    val geoEnforce: Boolean get() = assetConfig.value.geoEnforce
    val requiredFields: List<Pair<String, String>> get() = assetConfig.value.requiredFields
    val minPhotos: Int get() = assetConfig.value.minPhotos

    fun toggleFieldVisible(key: String) = write {
        if (configUseCases.toggleVisible(key) == ToggleFieldVisibleUseCase.Result.MANDATORY) flash("Mandatory fields are always visible")
    }
    fun toggleFieldRequired(key: String) = write { configUseCases.toggleRequired(key) }
    fun addField(label: String, kind: FieldKind, required: Boolean) = write {
        if (configUseCases.addField(label, kind, required)) flash("Field added")
    }
    fun removeField(key: String) = write {
        configUseCases.removeField(key)
        flash("Field removed")
    }

    fun setPhotoLimits(id: String, min: Int, max: Int) = write { configUseCases.setPhotoLimits(id, min, max) }
    fun addPhotoReq(name: String, hint: String) = write {
        if (configUseCases.addPhotoReq(name, hint)) flash("Photo type added")
    }
    fun removePhotoReq(id: String) = write { configUseCases.removePhotoReq(id) }

    fun setGeoEnforce(on: Boolean) = write { configUseCases.setGeoEnforce(on) }

    /** How many choices a dropdown field offers ("Generic" / "Select" placeholders not counted). */
    fun optionCount(f: FieldCfg): Int = f.optionCount

    /** Field sheet: saves the whole field (mandatory makes it visible; the name field stays mandatory). */
    fun saveField(f: FieldCfg) = write {
        if (configUseCases.saveField(f)) flash("Field saved")
    }

    /** Add field sheet: adds the fields set up there, under More details. */
    fun addFields(list: List<FieldCfg>) = write {
        val n = configUseCases.addFields(list)
        if (n > 0) flash(plural(n, "field", "fields") + " added")
    }

    /**
     * Import from Excel: one field per column, its kind guessed from the values. Columns that match a
     * field already here are skipped. [onReady] gets the new fields to review.
     */
    fun importFields(uri: Uri, onReady: (List<FieldCfg>) -> Unit) {
        write {
            when (val r = configUseCases.importFields(uri.toString())) {
                ImportFieldsUseCase.Result.Unreadable -> flash("Couldn’t read that file")
                ImportFieldsUseCase.Result.NothingNew -> flash("No new fields in that file · every column is already a field")
                is ImportFieldsUseCase.Result.Fields -> onReady(r.fields)
            }
        }
    }

    /** Checks sheet: what validation looks for in the field. */
    fun setFieldChecks(key: String, flagEmpty: Boolean, rule: ValueRule, min: String, max: String) = write {
        configUseCases.setChecks(key, flagEmpty, rule, min, max)
        flash("Checks saved")
    }

    fun setFieldOptions(key: String, options: List<String>) = write {
        configUseCases.setOptions(key, options)
        flash("Options saved")
    }

    // Property Settings: which ways of adding assets the phone offers for this property.
    fun onboardingOn(t: Tab): Boolean = t.channel?.let { it !in onboardingOff.value[currentProp].orEmpty() } ?: true

    fun setOnboarding(t: Tab, on: Boolean) {
        val channel = t.channel ?: return
        val prop = currentProp
        viewModelScope.launch {
            try {
                propertyUseCases.setOnboarding(prop, channel, on)
                flash((if (t == Tab.BT) "Bluetooth" else "Wi-Fi") + " onboarding " + if (on) "on" else "off")
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            }
        }
    }
}
