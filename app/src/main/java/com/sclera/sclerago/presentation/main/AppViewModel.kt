package com.sclera.sclerago.presentation.main

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.LocPick
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.MAX_PHOTOS_PER_ASSET
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.ScanFacts
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.model.findNode
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.integration.IntegrationAccessUseCase
import com.sclera.sclerago.domain.usecase.location.LocationUseCases
import com.sclera.sclerago.domain.usecase.media.FileUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.validation.ValidationAccessUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.UnsavedForms
import com.sclera.sclerago.presentation.model.viaRes
import com.sclera.sclerago.services.scan.ScanService
import com.sclera.sclerago.services.scan.ScanServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
     * Select all is on: every device not added yet in this scan is ticked, including ones the scan finds
     * later, except those in [devSkip] (unticked by hand).
     */
    val devAll: Boolean = false,
    val devSkip: Set<String> = emptySet(),
    /**
     * Scanned devices added as assets in the current scan of each list (Bluetooth, network devices, access points):
     * device id → the id of the asset made from it. Kept in memory only, never written anywhere: forgotten when the
     * user starts a new scan, the property changes, they sign out or the app closes. A scan paused for an asset's
     * form and resumed keeps it.
     */
    val devAdded: Map<Source, Map<String, String>> = emptyMap(),
    val pv: Preview? = null,
    val assetId: String? = null,
    val newAsset: Boolean = false,
    val newSrc: Source = Source.MANUAL,
    /** Photos/videos the new-asset form opens with (Camera tab: the shot just taken). */
    val newMedia: List<Media> = emptyList(),
    /** The scanned device the new-asset form was opened for (tapped on the Bluetooth / Network scan list). */
    val newDevice: Device? = null,
    /** Photos/videos taken (or picked) in the Camera tab, newest first. */
    val captures: List<Media> = emptyList(),
    val camSel: Set<String> = emptySet(),
    /** Leaving would throw something away: what the question is about ([AppViewModel.discardAndLeave]). */
    val leaveAsk: LeaveAsk? = null,
    val videoMode: Boolean = false,
    /** Set when the File tab should launch the system file manager; the UI clears it. */
    val pendingFilePicker: Boolean = false,
    val revOff: Set<String> = emptySet(),
    val toast: UiText = UiText.Raw(""),
    val toastOn: Boolean = false,
    /** Side menu (header menu button). */
    val menuOpen: Boolean = false,
    // ---- Validation page
    val valOpen: Boolean = false,
    /** A run to open on the Validation page as it opens ("Open in Validation" from an asset); null once it has. */
    val valJobId: String? = null,
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
    /** Shown instead of "ready" / "will need input" (a file's upload and reading, or a draft to check). */
    val note: UiText? = null,
    val noteColor: Color = Color.Unspecified,
)

/** What leaving asks about: photos or videos just taken, or a form's unsaved changes. */
enum class LeaveAsk { PHOTOS, CHANGES }

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

    /**
     * Where new assets go: the location chosen in the filter (its name, its "where" text and its id), also while the
     * property tab is showing every asset; nowhere while the Unmapped tab is open, or when no location has been chosen.
     */
    private val usesChosenLocation: Boolean
        get() = state.loc == LocFilter.SPEC || (state.loc == LocFilter.ALL && state.specSel.isNotEmpty())
    private val tgtLoc: String?
        get() = if (usesChosenLocation) state.specLocs.singleOrNull() ?: state.specPath.lastOrNull() else null
    private val tgtWhere: String get() = if (usesChosenLocation) state.specWhere else ""
    private val tgtPlace: String? get() = if (usesChosenLocation) state.specSel.ifEmpty { null } else null

    /** The property being worked on: the downloaded one opened last; empty until a property is downloaded. */
    val currentProp: String get() = downloadFlow.value.currentId.orEmpty()

    /** A property is downloaded, so its assets, sections and side-menu card can show. */
    val hasProperty: Boolean get() = currentProp.isNotEmpty()

    val propName: String get() = properties.firstOrNull { it.id == currentProp }?.name.orEmpty()
    /** The location tab path, or null when no location is chosen yet. */
    val specPathShown: List<String>? get() = state.specPath.takeIf { it.isNotEmpty() }

    val detailAsset: Asset?
        get() = if (state.newAsset) {
            // No name to start with (not "Asset from photo …"): the AI reads one off the Camera tab's photo, or it is typed.
            // A scanned device starts with what the scan found: its name, its line (IP / MAC, model) and its type.
            val d = state.newDevice
            Asset(
                "", d?.let(::deviceName).orEmpty(), d?.sub.orEmpty(), tgtLoc, tgtWhere,
                state.newSrc, Status.DRAFT, d?.type ?: AssetType.GENERIC, currentProp,
                media = state.newMedia, locationId = tgtPlace,
            )
        } else state.assetId?.let { id -> assets.firstOrNull { it.id == id } }

    /** What the new-asset form starts with: for a scanned device, what the scan found (a note, its BLE tag / IP). */
    val newPrefill: Map<String, String>
        get() = state.newDevice?.takeIf { state.newAsset }?.let { prefillFor(it) }.orEmpty()

    /** What the scan found out about the device the new-asset form was opened for; the form asks the AI about it. */
    val newScanFacts: ScanFacts?
        get() = state.newDevice?.takeIf { state.newAsset }?.facts

    /**
     * What an asset made from scanned device [d] starts with: its make and model as the scan found them, and a
     * Description of what it is (kind, OS, services…), from which the AI fills the rest. Only standard fields: the
     * property's other fields have keys of their own, so nothing is guessed into them. The description carries no
     * identifiers (no IP or MAC address, BLE address, BSSID or SSID), as the AI reads it.
     */
    private fun prefillFor(d: Device): Map<String, String> {
        val facts = d.facts
        val about = facts?.summary().orEmpty().ifBlank { deviceName(d) }
        val m = mutableMapOf<String, String>()
        if (about.isNotBlank()) m["description"] = about
        facts?.make?.let { m["make"] = it }
        facts?.model?.let { m["model"] = it }
        return m
    }

    val showDetail: Boolean get() = detailAsset != null
    val camSub: Boolean get() = !state.camOpen
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
        get() = btDevices.let { rows -> scans.card(ScanService.Target.BLUETOOTH, rows.size, rows.count { it.id in addedIn(Source.BT) }) }

    val netCard: ScanService.Card
        get() = netDevices.let { rows -> scans.card(netTarget, rows.size, rows.count { it.id in addedIn(netSource) }) }

    private fun deviceName(d: Device) = if (d.name in setOf(UNKNOWN_DEVICE, "Hidden network")) "" else d.name

    /** The source of the Network tab's current mode: access points, or Wi-Fi network devices (its scan and its asset list). */
    val netSource: Source get() = if (state.netMode == NetMode.AP) Source.AP else Source.WIFI

    private val scanSource: Source
        get() = if (state.tab == Tab.BT) Source.BT else netSource

    /**
     * What the scans found. A device is never matched to an asset in the database; only those added during the
     * current scan are marked ([addedDevices]), and tapping one opens the asset made from it.
     */
    val btDevices: List<Device> get() = namedAsAdded(scans.rows(ScanService.Target.BLUETOOTH), Source.BT)
    val netDevices: List<Device> get() = namedAsAdded(scans.rows(netTarget), netSource)

    /**
     * A device added as an asset shows that asset's name: the scan's own name for it changes as it learns more (a
     * hostname such as "MAC-BAF86B" first, a Bonjour name later), and starts over when the scan restarts, while the
     * asset keeps the name it was saved with (or renamed to).
     */
    private fun namedAsAdded(rows: List<Device>, src: Source): List<Device> {
        val made = state.devAdded[src].orEmpty()
        if (made.isEmpty()) return rows
        val names = assets.filter { it.id in made.values }.associate { it.id to it.name }
        return rows.map { d -> made[d.id]?.let(names::get)?.takeIf { it.isNotBlank() }?.let { d.copy(name = it) } ?: d }
    }

    private fun addedIn(src: Source): Set<String> = state.devAdded[src].orEmpty().keys

    /** Devices on the scan list on screen that were added as assets in this scan. */
    val addedDevices: Set<String> get() = addedIn(scanSource)

    /** Marks device [deviceId] as made into asset [assetId] in the current scan of [src]'s list. */
    private fun markAdded(src: Source, deviceId: String, assetId: String) =
        set { copy(devAdded = devAdded + (src to (devAdded[src].orEmpty() + (deviceId to assetId)))) }

    /** Device [deviceId]'s asset is gone (deleted): it is no longer marked as added. */
    private fun unmarkAdded(src: Source, deviceId: String) =
        set { copy(devAdded = devAdded + (src to (devAdded[src].orEmpty() - deviceId))) }

    /** The user starts a new scan of [src]'s list: what was added in the last one is forgotten. */
    private fun forgetAdded(src: Source) = set { copy(devAdded = devAdded - src) }

    // Camera: every selected capture becomes its own draft asset.
    // ---------- Actions ----------

    /** A bottom tab (or a finished swipe): asks first when leaving would throw away photos just taken or unsaved changes ([leaving]). */
    fun goTab(t: Tab) = leaving(t) { switchTab(t) }

    private fun switchTab(t: Tab) {
        set {
            var o = copy(tab = t, assetId = null, newAsset = false, newMedia = emptyList(), newDevice = null, devPick = emptySet(), devAll = false, devSkip = emptySet())
            if (t == Tab.CAMERA) o = o.copy(camOpen = true)
            if (t == Tab.FILE) o = o.copy(fileOpen = true, revFile = false, pendingFilePicker = true)
            // Scanning tabs always open on their Scan segment, not their asset list.
            if (t == Tab.BT) o = o.copy(btScan = true)
            if (t == Tab.NET) o = o.copy(netScan = true)
            o
        }
        // Opening a scanning tab starts its scan; leaving it stops the scan (the watch in init, see [scanShown]).
        when (t) {
            Tab.BT -> scans.start(ScanService.Target.BLUETOOTH, auto = true)
            Tab.NET -> scans.start(netTarget, auto = true)
            else -> {}
        }
    }

    /**
     * [t]'s scan list is on screen: its tab on its Scan segment (for the Network tab, in [t]'s mode), with no asset
     * details or side-menu page over it. Anywhere else the scan is stopped.
     */
    /** Scans that were running when their list left the screen, to start again when it is back. */
    private var interrupted = emptySet<ScanService.Target>()

    private fun scanShown(t: ScanService.Target): Boolean {
        val s = state
        val uncovered = s.assetId == null && !s.newAsset && !s.valOpen && !s.cfgOpen && !s.intOpen && s.adminPage == null
        return uncovered && when (t) {
            ScanService.Target.BLUETOOTH -> s.tab == Tab.BT && s.btScan
            ScanService.Target.NETWORK, ScanService.Target.ACCESS_POINTS -> s.tab == Tab.NET && s.netScan && t == netTarget
        }
    }

    fun onPageSettled(page: Int) {
        val t = navTabs.getOrNull(page) ?: return
        if (t != state.tab) goTab(t)
    }

    /** The bottom tabs this property shows: Bluetooth / Network are hidden when their onboarding is off. */
    val navTabs: List<Tab> get() = Tab.entries - onboardingOff.value[currentProp].orEmpty().map { it.tab }.toSet()

    /**
     * Shake gesture: jump straight to the live camera, only from the Assets page itself (its list on screen, with no
     * asset, sheet, preview, side menu or side-menu page over it). Anywhere else a shake does nothing. True when it opened.
     */
    fun openCameraByShake(): Boolean {
        val s = state
        val onAssetsPage = s.tab == Tab.ASSETS && listInteractive && s.pv == null && !s.menuOpen &&
            !s.valOpen && !s.cfgOpen && !s.intOpen && s.adminPage == null
        if (onAssetsPage) goTab(Tab.CAMERA)
        return onAssetsPage
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
            MenuItem.AUDIT -> return comingSoon(R.string.menu_audit)
            MenuItem.SURVEY -> return comingSoon(R.string.menu_survey)
            MenuItem.CONFIGURATION -> if (!canConfigure) return
            else -> Unit
        }
        // The page on screen goes: asked once, here, before anything closes (photos just taken, unsaved changes).
        leaving {
            when (item) {
                MenuItem.PROPERTY -> showAdmin(AdminPage.PROPERTIES)
                MenuItem.USERS -> showAdmin(AdminPage.PEOPLE)
                // Validation, Configuration and Integration sit on top of the tabs, so they close too.
                MenuItem.ASSETS -> {
                    closeAdmin()
                    set { copy(valOpen = false, cfgOpen = false, cfgPage = CfgPage.FIELDS, intOpen = false) }
                    switchTab(Tab.ASSETS)
                }
                MenuItem.VALIDATION -> { closeAdmin(); showValidation() }
                MenuItem.CONFIGURATION -> { closeAdmin(); showConfig() }
                MenuItem.INTEGRATION -> { closeAdmin(); showIntegration() }
                MenuItem.AUDIT, MenuItem.SURVEY -> Unit
            }
        }
    }

    private fun comingSoon(@StringRes label: Int) = flash(UiText.Res(R.string.menu_coming_soon, listOf(UiText.Res(label))))

    // Property list / People and access
    fun openAdmin(page: AdminPage) = leaving { showAdmin(page) }
    private fun showAdmin(page: AdminPage) =
        set { copy(adminPage = page, menuOpen = false, valOpen = false, cfgOpen = false, intOpen = false, assetId = null, newAsset = false) }
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
        if (state.sheetFor == SheetFor.ASSET && detail != null) {
            // Not written yet: the details keep it with their other changes until Save (or, for a new asset, until
            // it is created).
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
    fun toggleBt() {
        if (!btOn) forgetAdded(Source.BT)
        scans.toggle(ScanService.Target.BLUETOOTH)
    }
    fun toggleNet() {
        if (!netOn) forgetAdded(netSource)
        scans.toggle(netTarget)
    }
    // ---------- Adding several scanned devices at once ----------

    /** The scan list on screen (Bluetooth, or the Network tab's current mode), or empty off the scan pages. */
    private val shownDevices: List<Device>
        get() = when {
            state.tab == Tab.BT && state.btScan -> btDevices
            state.tab == Tab.NET && state.netScan -> netDevices
            else -> emptyList()
        }

    /** Devices on the list not added yet in this scan: the ones that can be ticked. */
    private val addableDevices: List<Device> get() = addedDevices.let { added -> shownDevices.filter { it.id !in added } }

    /** Ticked devices that are still on the list and not added yet (with Select all on, new finds are ticked too). */
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
     * Select all on the scan list: ticks every device not added yet and keeps ticking new
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
     * Opens a scanned device: the asset made from it in this scan when there is one, otherwise the new-asset form
     * filled in with what the scan found (also when that asset has since been deleted, which drops its tick).
     */
    fun openDevice(d: Device) {
        val src = scanSource
        val made = state.devAdded[src]?.get(d.id)
        when {
            made != null && assets.any { it.id == made } -> openAsset(made)
            else -> {
                if (made != null) unmarkAdded(src, d.id)
                set { copy(newAsset = true, newSrc = src, newMedia = emptyList(), newDevice = d, assetId = null) }
            }
        }
    }

    /** Adds a scanned device straight away as a draft asset (its + button), filled in from what the scan found. */
    fun addDevice(d: Device) = addDevices(listOf(d))

    /** Adds [devices] as draft assets in the current place, filled in from each device (name, type, BLE tag / IP, a note). */
    private fun addDevices(devices: List<Device>) {
        val src = scanSource
        val place = AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
        write {
            devices.forEach { d ->
                val created = assetUseCases.create(
                    deviceName(d), "", emptyList(), prefillFor(d), false,
                    place = place, captures = emptyList(), device = d, deviceSource = src,
                )
                markAdded(src, d.id, created.id)
            }
            val added = plural(devices.size, "asset", "assets") + if (devices.size == 1) " added as a draft" else " added as drafts"
            flash(UiText.Res(R.string.notice_with_source, listOf(UiText.Raw(added), UiText.Res(src.viaRes))))
        }
    }

    fun openAsset(id: String) = set { copy(assetId = id, newAsset = false) }

    fun openNew() = set { copy(newAsset = true, newSrc = Source.MANUAL, newMedia = emptyList(), newDevice = null, assetId = null) }
    fun closeDetail() {
        // A camera shot that never became an asset is thrown away with the form.
        if (state.newAsset) fileUseCases.deleteOwned(state.newMedia)
        set { copy(assetId = null, newAsset = false, newMedia = emptyList(), newDevice = null) }
    }

    /** Camera tab: open the new-asset details with these photos/videos already attached. */
    private fun openNewWithMedia(media: List<Media>) {
        if (media.isEmpty()) return
        set {
            copy(
                newAsset = true, newSrc = Source.CAMERA, newMedia = media, newDevice = null, assetId = null,
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
        picked: LocValue? = null,
    ): CreateAssetUseCase.Via {
        val s = state
        // Camera shots, a scanned device (tapped on the scan list) or by hand.
        val via = assetUseCases.create.viaFor(s.newMedia, s.newDevice)
        // The place picked in the form, else where the location filter put it.
        val place = picked?.let { AssetPlace(currentProp, it.loc, it.where, it.placeId) } ?: AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
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
            // From a scan: stay on the scan list for the next device.
            CreateAssetUseCase.Via.SCAN -> set { copy(newAsset = false, newDevice = null) }
            CreateAssetUseCase.Via.MANUAL -> set { copy(newAsset = false, tab = Tab.ASSETS) }
        }
        write {
            val created = assetUseCases.create(
                name, detail, media, values, submit, place,
                captures = s.newMedia, device = s.newDevice, deviceSource = s.newSrc,
            )
            if (created.via == CreateAssetUseCase.Via.SCAN) s.newDevice?.let { markAdded(s.newSrc, it.id, created.id) }
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

    fun cancelCam() = leaving { set { copy(camOpen = false) } }
    fun openCam() = set { copy(camOpen = true) }

    /** The camera is on screen with shots in its strip that aren't an asset yet (swiping away is off meanwhile). */
    val cameraHasShots: Boolean
        get() = state.tab == Tab.CAMERA && state.camOpen && state.captures.isNotEmpty() && !showDetail

    /** The forms on screen with unsaved changes (they report themselves; see [UnsavedForms]). */
    val unsavedForms = UnsavedForms()

    /** Where the user was going when the question opened; it goes on if they discard. */
    private var afterDiscard: (() -> Unit)? = null

    /** The photos and videos discarding throws away, and whether they are the camera strip's. */
    private var discardMedia: List<Media> = emptyList()
    private var discardStrip = false

    /**
     * Runs [go], unless it would throw something away. Photos or videos just taken (shots still in the camera's strip
     * when it leaves the camera, [toTab] being the tab it goes to and null for anything else; or those a new asset's
     * form opened with): "Discard your photos?" asks first. A form with unsaved changes (asset details, a sheet,
     * Geofencing): "Discard your changes?" asks first. [go] runs only if the user discards.
     */
    private fun leaving(toTab: Tab? = null, go: () -> Unit) {
        val s = state
        val strip = cameraHasShots && toTab != Tab.CAMERA
        val formShots = if (s.newAsset) s.newMedia else emptyList()
        val ask = when {
            strip || formShots.isNotEmpty() -> LeaveAsk.PHOTOS
            unsavedForms.any -> LeaveAsk.CHANGES
            else -> return go()
        }
        afterDiscard = go
        discardMedia = (if (strip) s.captures else emptyList()) + formShots
        discardStrip = strip
        set { copy(leaveAsk = ask) }
    }

    /** "Keep" on the question: stay where they are. */
    fun keepEditing() {
        afterDiscard = null
        discardMedia = emptyList()
        discardStrip = false
        set { copy(leaveAsk = null) }
    }

    /** "Discard" on the question: the shots (if any) are deleted, then the user goes where they were going. */
    fun discardAndLeave() {
        val go = afterDiscard
        val strip = discardStrip
        fileUseCases.deleteOwned(discardMedia)
        afterDiscard = null
        discardMedia = emptyList()
        discardStrip = false
        set {
            copy(
                captures = if (strip) emptyList() else captures, camSel = if (strip) emptySet() else camSel,
                newMedia = if (newAsset) emptyList() else newMedia, leaveAsk = null,
            )
        }
        go?.invoke()
    }
    /** Ticks or unticks a shot in the strip; past the photos one asset may have, it says so instead. */
    fun toggleStrip(id: String) {
        val s = state
        if (id in s.camSel) return set { copy(camSel = camSel - id) }
        val m = s.captures.firstOrNull { it.id == id } ?: return
        if (!m.video && tickedPhotos() >= MAX_PHOTOS_PER_ASSET) return flash(UiText.Res(R.string.camera_asset_full, listOf(MAX_PHOTOS_PER_ASSET)))
        set { copy(camSel = camSel + id) }
    }

    private fun tickedPhotos(): Int = state.captures.count { it.id in state.camSel && !it.video }
    fun setVideoMode(v: Boolean) = set { copy(videoMode = v) }

    /**
     * A photo/video from the in-app camera joins the strip, ticked while one asset still has room for it, and the camera
     * stays open for more; Done ([doneCapturing]) makes one asset of the ticked ones.
     */
    fun addCapture(m: Media) {
        val room = m.video || tickedPhotos() < MAX_PHOTOS_PER_ASSET
        set { copy(captures = captures + m, camSel = if (room) camSel + m.id else camSel) }
        if (!room) flash(UiText.Res(R.string.camera_asset_full, listOf(MAX_PHOTOS_PER_ASSET)))
    }

    /** Done in the camera: the ticked shots open one new asset's details (sorted into their kinds, then read by the AI). */
    fun doneCapturing() {
        val picked = state.captures.filter { it.id in state.camSel }
        if (picked.isEmpty()) return
        set { copy(captures = captures.filter { it.id !in camSel }, camSel = emptySet(), camOpen = false) }
        openNewWithMedia(picked)
    }

    /** Gallery button in the camera: the picked photos/videos open one new asset's details. */
    fun addPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            openNewWithMedia(fileUseCases.describeMedia(uris.map { it.toString() }))
        }
    }

    fun toggleRev(id: String) = set { copy(revOff = if (id in revOff) revOff - id else revOff + id) }

    // ---------- Files ----------

    fun cancelFile() = set { copy(fileOpen = false) }
    fun openFile() = set { copy(fileOpen = true, revFile = false, pendingFilePicker = true) }
    fun filePickerLaunched() = set { copy(pendingFilePicker = false) }

    /**
     * The system file manager closed; [anyFiles]: files are waiting (picked now or before), so their review shows.
     * Uploading and reading them is presentation/fileimport's FileImportViewModel.
     */
    fun onFilesPicked(anyFiles: Boolean) = set { copy(fileOpen = false, revFile = anyFiles) }

    fun backToFiles() = set { copy(pendingFilePicker = true) }

    /** The File tab's review is done with (added, discarded or emptied): back to its asset list. */
    fun closeFiles() = set { copy(revFile = false, fileOpen = false) }

    // ---------- Validation ----------

    /** The signed-in user may run validations (the server's check permission; validate includes it). */
    val canCheck: Boolean get() = validationAccess.canCheck()

    // Side-menu pages always return to the Assets tab. It is also selected on open, so the
    // pager slides there hidden behind the page rather than visibly on close.
    fun openValidation() = leaving { showValidation() }
    private fun showValidation() =
        set { copy(valOpen = true, cfgOpen = false, intOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    fun closeValidation() = set { copy(valOpen = false, valJobId = null, tab = Tab.ASSETS, assetId = null, newAsset = false) }

    /** "Open in Validation" from an asset: the Validation page, on the run [jobId] (the asset closes). */
    fun openValidationRun(jobId: String) = leaving {
        set { copy(valOpen = true, valJobId = jobId, cfgOpen = false, intOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    }

    /** The Validation page opened the run asked for. */
    fun validationRunShown() = set { copy(valJobId = null) }

    // ---------- Integration ----------

    /** The signed-in user may configure the property (the server's configure permission): the Integration page shows only then. */
    val canConfigure: Boolean get() = integrationAccess()

    fun openIntegration() = leaving { showIntegration() }
    private fun showIntegration() =
        set { copy(intOpen = true, valOpen = false, cfgOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
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
                // Devices added in a scan were added to the property left behind.
                set { copy(loc = LocFilter.ALL, specLocs = emptyList(), specPath = emptyList(), specWhere = "", specSel = "", assetId = null, devAdded = emptyMap()) }
                if (state.tab !in navTabs) goTab(Tab.ASSETS)
            }
        }
        // Bluetooth and network scans (the network sweep included) run only while their scan list is on screen:
        // another tab, the tab's Assets list, an asset's details or a side-menu page stops them (they cost battery).
        // A scan stopped that way starts again when its list is back on screen; one the user paused stays paused.
        viewModelScope.launch {
            snapshotFlow { ScanService.Target.entries.filter(::scanShown).toSet() }.collect { shown ->
                ScanService.Target.entries.forEach { t ->
                    when {
                        t in shown && t in interrupted -> {
                            interrupted = interrupted - t
                            scans.start(t, auto = true)
                        }
                        t !in shown && scans.isScanning(t) -> {
                            interrupted = interrupted + t
                            scans.stop(t)
                        }
                    }
                }
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
        scans.forgetFinished()
        interrupted = emptySet()
        state = UiState()
    }

    // ---------- Configuration ----------

    /** Opens Configuration, only for someone who may configure the property (the web hides it otherwise). */
    fun openConfig() = if (!canConfigure) Unit else leaving { showConfig() }
    private fun showConfig() =
        set { copy(cfgOpen = true, valOpen = false, intOpen = false, cfgPage = CfgPage.FIELDS, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
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
