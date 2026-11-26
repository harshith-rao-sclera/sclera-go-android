package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.usecase.access.AccessBlocks
import com.sclera.sclerago.domain.usecase.access.ObserveAccessBlocksUseCase
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.model.placeScopeOf
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.ScanFacts
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.usecase.asset.AssetPlace
import com.sclera.sclerago.domain.usecase.access.ObserveAccessChangesUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveAccessFailedUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveGroupNameUseCase
import com.sclera.sclerago.domain.usecase.access.RetryAccessUseCase
import com.sclera.sclerago.domain.usecase.access.OpensPropertiesUseCase
import com.sclera.sclerago.domain.usecase.access.SeesAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.fileimport.FileImportAccessUseCase
import com.sclera.sclerago.domain.usecase.location.LocationUseCases
import com.sclera.sclerago.domain.usecase.media.FileUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.property.RemoveLostPropertiesUseCase
import com.sclera.sclerago.domain.model.LostProperties
import com.sclera.sclerago.presentation.assets.DetailTabKind
import com.sclera.sclerago.presentation.assets.photoLimitNotices
import com.sclera.sclerago.presentation.assets.notice
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.observedIn
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.UnsavedForms
import com.sclera.sclerago.presentation.model.message
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.model.viaRes
import com.sclera.sclerago.services.scan.ScanService
import com.sclera.sclerago.services.scan.ScanServiceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long the app waits at start for the server's property list before restarting the sync without it (slow or no network). */
private const val LOST_CHECK_WAIT_MS = 15_000L

enum class Tab { BT, NET, ASSETS, CAMERA, FILE }
enum class LocFilter { ALL, SPEC, NONE }
enum class Sheet { NONE, LOC }
enum class SheetFor { FILTER, ASSET, BULK }
enum class NetMode { WIFI, AP }
enum class MenuItem { PROPERTY, USERS, ASSETS, VALIDATION, CONFIGURATION, INTEGRATION, SYNC }
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
    /** What [specSel] is: a building, floor or location (null until one is picked). */
    val specKind: NodeKind? = null,
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
    /**
     * Devices (by list) whose asset is being written right now: shown as added at once, so a quick second tap on + (or Add
     * on the selection) doesn't make another asset of the same device.
     */
    val devAdding: Map<Source, Set<String>> = emptyMap(),
    val pv: Preview? = null,
    val assetId: String? = null,
    /**
     * The tab the asset's details open on ([AppViewModel.openAssetValidation]: Validation); null keeps the screen's own
     * choice. Cleared once the details screen has shown it ([AppViewModel.assetTabOpened]).
     */
    val assetTab: DetailTabKind? = null,
    val newAsset: Boolean = false,
    val newSrc: Source = Source.MANUAL,
    /** Photos/videos the new-asset form opens with (Camera tab: the shot just taken). */
    val newMedia: List<Media> = emptyList(),
    /** The scanned device the new-asset form was opened for (tapped on the Bluetooth / Network scan list). */
    val newDevice: Device? = null,
    /** Photos/videos taken (or picked) in the Camera tab, newest first. */
    val captures: List<Media> = emptyList(),
    val camSel: Set<String> = emptySet(),
    /** The camera makes many assets: every shot goes to the server as its own file (no sorting, no photo rules). */
    val camMany: Boolean = false,
    /** Shots handed to the Camera's import to be read by the server; the UI takes them ([AppViewModel.shotsSent]). */
    val camToSend: List<Media>? = null,
    /** The Camera tab shows what the server read from the shots (the File tab's review). */
    val camReview: Boolean = false,
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
    /** With [valJobId]: the asset whose result opens on that run ("This asset's result"); null for the run alone. */
    val valAssetId: String? = null,
    // ---- Configuration page
    val cfgOpen: Boolean = false,
    // ---- Integration page
    val intOpen: Boolean = false,
    // ---- Sync settings page
    val syncOpen: Boolean = false,
    val cfgPage: CfgPage = CfgPage.BUILDING,
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
    private val accessBlocks: ObserveAccessBlocksUseCase,
    private val fileUseCases: FileUseCases,
    private val downloads: PropertyDownloadUseCases,
    private val seesAssetsUseCase: SeesAssetsUseCase,
    private val opensPropertiesUseCase: OpensPropertiesUseCase,
    private val observeAccessChanges: ObserveAccessChangesUseCase,
    private val observeAccessFailed: ObserveAccessFailedUseCase,
    private val observeGroupName: ObserveGroupNameUseCase,
    private val retryAccessUseCase: RetryAccessUseCase,
    private val fileImportAccess: FileImportAccessUseCase,
    private val removeLostProperties: RemoveLostPropertiesUseCase,
) : ViewModel() {
    var state by mutableStateOf(UiState())
        private set

    /**
     * Bumped whenever the signed-in user's access changes (fetched, failed, changed): every permission check the
     * screens read ([canCheck], [canConfigure], [canManageAccess], [seesAssets], [mayCollect]) reads it first, so they
     * recompose with the new answer instead of keeping the one from when they were first drawn.
     */
    private var accessVersion by mutableIntStateOf(0)

    /** The access couldn't be loaded (and none was kept): pages and changes that need a permission stay unavailable. */
    var accessFailed by mutableStateOf(false)
        private set

    /** The group the signed-in user is in (the side menu shows it under their name); null while unknown or in none. */
    var groupName by mutableStateOf<String?>(null)
        private set

    /** "Retry" on the banner saying the access couldn't be loaded: fetches it again. */
    fun retryAccess() = retryAccessUseCase()

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
            LocFilter.SPEC -> LocationFilter.Specific(placeScopeOf(state.specKind, state.specSel))
            LocFilter.NONE -> LocationFilter.Unmapped
        }

    /** The place the location filter picked, as building / floor / location ids (the whole property: none), for the Managed Assets Summary. */
    val placeScope: PlaceScope
        get() = if (state.loc == LocFilter.SPEC) placeScopeOf(state.specKind, state.specSel) else PlaceScope()

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
        } else state.assetId?.let { id ->
            // One in Managed Assets (opened from the Managed assets chip) opens too, read-only.
            assets.firstOrNull { it.id == id } ?: catalog.value.managed.firstOrNull { it.id == id }
        }

    /** What the new-asset form starts with: for a scanned device, what the scan found (a note, its BLE tag / IP). */
    val newPrefill: Map<String, String>
        get() = state.newDevice?.takeIf { state.newAsset }?.let { prefillFor(it) }.orEmpty()

    /** What the scan found out about the device the new-asset form was opened for; the form asks the AI about it. */
    val newScanFacts: ScanFacts?
        get() = state.newDevice?.takeIf { state.newAsset }?.facts

    /**
     * What an asset made from scanned device [d] starts with: its make and model as the scan found them, and a
     * Description with everything the scan found, one fact per line, from which the AI fills the rest. Only standard
     * fields: the property's other fields have keys of their own, so nothing is guessed into them. The lines that
     * identify the device (IP, MAC, Bluetooth address, BSSID, names) stay on the asset but never go to the AI.
     */
    private fun prefillFor(d: Device): Map<String, String> {
        val facts = d.facts
        val about = facts?.description().orEmpty().ifBlank { deviceName(d) }
        val m = mutableMapOf<String, String>()
        if (about.isNotBlank()) m["description"] = about
        facts?.make?.let { m["make"] = it }
        facts?.model?.let { m["model"] = it }
        return m
    }

    val showDetail: Boolean get() = detailAsset != null
    /** The Camera tab shows its asset list: neither the camera nor the review of shots (Multiple) is open. */
    val camSub: Boolean get() = !state.camOpen && !state.camReview
    val fileSub: Boolean get() = !state.revFile && !state.fileOpen
    val listy: Boolean
        get() = when (state.tab) {
            Tab.ASSETS, Tab.BT, Tab.NET -> true
            Tab.CAMERA -> camSub
            Tab.FILE -> fileSub
        }
    private val sheetOpen get() = state.sheet != Sheet.NONE

    /** A scan list is on screen (the Bluetooth or Network tab on its Scan side), not an asset list. */
    val scanListShown: Boolean
        get() = (state.tab == Tab.BT && state.btScan) || (state.tab == Tab.NET && state.netScan)

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

    /** Devices of [src]'s list added as assets in this scan, or being added right now. */
    private fun addedIn(src: Source): Set<String> = state.devAdded[src].orEmpty().keys + state.devAdding[src].orEmpty()

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
            // Adding assets needs Collect (the web hides Add): without it the camera stays shut and the file manager
            // isn't launched (the File tab's page says why).
            if (t == Tab.CAMERA) o = o.copy(camOpen = mayCollect())
            if (t == Tab.FILE) o = o.copy(fileOpen = true, revFile = false, pendingFilePicker = mayUploadFiles())
            // Scanning tabs always open on their Scan segment, not their asset list.
            if (t == Tab.BT) o = o.copy(btScan = true)
            if (t == Tab.NET) o = o.copy(netScan = true)
            o
        }
        // Opening a scanning tab starts its scan; leaving it stops the scan (the watch in init, see [scanShown]).
        when (t) {
            Tab.BT -> scans.start(ScanService.Target.BLUETOOTH, auto = true)
            Tab.NET -> scans.start(netTarget, auto = true)
            Tab.CAMERA -> if (!mayCollect()) flashViewOnly()
            else -> {}
        }
    }

    /** Someone who can't collect tried to add an asset: says why (their group only views, or they are away from the site). */
    private fun flashViewOnly() = flash(collectBlock.reason() ?: UiText.Res(R.string.view_only_no_add))

    /**
     * [t]'s scan list is on screen: its tab on its Scan segment (for the Network tab, in [t]'s mode), with no asset
     * details or side-menu page over it. Anywhere else the scan is stopped.
     */
    /** Scans that were running when their list left the screen, to start again when it is back. */
    private var interrupted = emptySet<ScanService.Target>()

    private fun scanShown(t: ScanService.Target): Boolean {
        val s = state
        val uncovered = s.assetId == null && !s.newAsset && !s.valOpen && !s.cfgOpen && !s.intOpen && !s.syncOpen && s.adminPage == null
        return uncovered && when (t) {
            ScanService.Target.BLUETOOTH -> s.tab == Tab.BT && s.btScan
            ScanService.Target.NETWORK, ScanService.Target.ACCESS_POINTS -> s.tab == Tab.NET && s.netScan && t == netTarget
        }
    }

    /** The pager came to rest on tab [t]; a tab no longer shown (its pager is being replaced) is ignored. */
    fun onPageSettled(t: Tab) {
        if (t != state.tab && t in navTabs) goTab(t)
    }

    /** The bottom tabs this property shows: Bluetooth / Network are hidden when their onboarding is off. */
    val navTabs: List<Tab> get() = Tab.entries - onboardingOff.value[currentProp].orEmpty().map { it.tab }.toSet()

    /**
     * Shake gesture: jump straight to the live camera, only from the Assets page itself (its list on screen, with no
     * asset, sheet, preview, side menu or side-menu page over it). Anywhere else a shake does nothing. True when it opened.
     */
    fun openCameraByShake(): Boolean {
        // Without Collect there is nothing to add, so a shake does nothing.
        if (!mayCollect()) return false
        val s = state
        val onAssetsPage = s.tab == Tab.ASSETS && listInteractive && s.pv == null && !s.menuOpen &&
            !s.valOpen && !s.cfgOpen && !s.intOpen && !s.syncOpen && s.adminPage == null
        if (onAssetsPage) goTab(Tab.CAMERA)
        return onAssetsPage
    }

    fun tapLocTab(f: LocFilter) {
        val cur = state.loc
        when (f) {
            LocFilter.ALL -> if (cur == LocFilter.ALL) openProp() else set { copy(loc = LocFilter.ALL, assetId = null) }
            // No location chosen yet: the tab opens the picker straight away.
            LocFilter.SPEC -> if (cur == LocFilter.SPEC || state.specSel.isEmpty()) openLocFilter() else set { copy(loc = LocFilter.SPEC, assetId = null) }
            LocFilter.NONE -> set { copy(loc = LocFilter.NONE, assetId = null) }
        }
    }

    /** Another property is chosen (and downloaded) on the Property list. */
    fun openProp() = openAdmin(AdminPage.PROPERTIES)

    // Side menu
    fun openMenu() = set { copy(menuOpen = true) }
    fun closeMenu() = set { copy(menuOpen = false) }
    fun pickMenu(item: MenuItem) {
        // A page the user's group can't open stays greyed out in the menu: a tap says why and nothing opens.
        menuBlock(item)?.let { return flash(UiText.Res(it.message)) }
        closeMenu()
        // The page on screen goes: asked once, here, before anything closes (photos just taken, unsaved changes).
        leaving {
            when (item) {
                MenuItem.PROPERTY -> showAdmin(AdminPage.PROPERTIES)
                MenuItem.USERS -> showAdmin(AdminPage.PEOPLE)
                // Validation, Configuration and Integration sit on top of the tabs, so they close too.
                MenuItem.ASSETS -> {
                    closeAdmin()
                    set { copy(valOpen = false, cfgOpen = false, cfgPage = CfgPage.BUILDING, intOpen = false, syncOpen = false) }
                    switchTab(Tab.ASSETS)
                }
                MenuItem.VALIDATION -> { closeAdmin(); showValidation() }
                MenuItem.CONFIGURATION -> { closeAdmin(); showConfig() }
                MenuItem.INTEGRATION -> { closeAdmin(); showIntegration() }
                MenuItem.SYNC -> { closeAdmin(); showSync() }
            }
        }
    }


    // Property list / People and access
    fun openAdmin(page: AdminPage) = leaving { showAdmin(page) }
    private fun showAdmin(page: AdminPage) =
        set { copy(adminPage = page, menuOpen = false, valOpen = false, cfgOpen = false, intOpen = false, syncOpen = false, assetId = null, newAsset = false) }
    /** Back to the assets; without a downloaded property there are none, so the Property list stays. */
    fun closeAdmin() = set { copy(adminPage = if (hasProperty) null else AdminPage.PROPERTIES) }

    /**
     * Opening a downloaded property from the list makes it the current one and shows its assets (properties that
     * aren't downloaded are downloaded first).
     */
    fun openPropertyFromList(id: String) {
        if (id !in downloadFlow.value.downloadedIds) return
        // A group without View (nothing ticked) lists its properties but can't look inside one (the web's opensProperties).
        openPropertyBlock?.let { return flash(UiText.Res(it.message)) }
        if (id != currentProp) downloads.open(id)
        closeAdmin()
        // Its first page: the assets, or Configuration for a group that only configures (the web's propertyHome).
        if (seesAssets) goTab(Tab.ASSETS) else showConfig()
    }

    /**
     * The property's assets are offered (the web's Data Collection): not to a group that only configures, whose first
     * page is Configuration.
     */
    val seesAssets: Boolean get() = accessVersion.let { seesAssetsUseCase() }

    /** The user opens properties at all: a group without View (or Configure) stays on the property list. */
    private val opensProperties: Boolean get() = accessVersion.let { opensPropertiesUseCase() }

    /** Why a downloaded property can't be opened (or downloaded) from the list: the group can't look inside one; null when it can. */
    val openPropertyBlock: AccessBlock? get() = if (opensProperties) null else blocks.assets ?: AccessBlock.NEEDS_VIEW

    /** Why each kind of button is greyed out for the signed-in user (null: it can be used), re-read with every access change. */
    val blocks: AccessBlocks get() = accessVersion.let { accessBlocks.now() }

    /** Why adding or changing assets is greyed out (no Collect, or away from a geofenced site); null when the user may. */
    val collectBlock: AccessBlock? get() = blocks.collect

    /**
     * Why side-menu entry [item] is greyed out (null: it opens), as the web's route guards: Data Collection needs View,
     * Validation needs Check, Configuration and Integration need Configure, the access page needs Manage access.
     */
    fun menuBlock(item: MenuItem): AccessBlock? = blocks.let { b ->
        when (item) {
            MenuItem.PROPERTY -> null
            MenuItem.USERS -> b.manageAccess
            MenuItem.ASSETS -> b.assets
            MenuItem.VALIDATION -> b.check
            MenuItem.CONFIGURATION, MenuItem.INTEGRATION -> b.configure
            // The device's own sync of the open property: anyone who opens it.
            MenuItem.SYNC -> null
        }
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
                specWhere = r.where, specSel = r.id, specKind = r.kind, assetId = null,
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

    /** Ticking is under way: rows show tick boxes and the add bar shows (its Add greyed out without Collect). */
    val devSelOn: Boolean get() = pickedDevices.isNotEmpty() && !showDetail && !sheetOpen

    /** The signed-in user's group can add assets (Collect); the web hides Add from anyone else. */
    fun mayCollect(): Boolean = accessVersion.let { assetUseCases.create.allowed() }

    /** The user may upload files to add assets from (Collect): only then does the File tab launch the file manager. */
    private fun mayUploadFiles(): Boolean = accessVersion.let { fileImportAccess.allowed() }
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
        if (!mayCollect()) return flashViewOnly()
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
        // Its asset is still being written (its + was just tapped): it opens once it exists.
        if (d.id in state.devAdding[src].orEmpty()) return
        val made = state.devAdded[src]?.get(d.id)
        when {
            made != null && assets.any { it.id == made } -> openAsset(made)
            // The new-asset form is only for someone who may add assets (Collect).
            !mayCollect() -> flashViewOnly()
            else -> {
                if (made != null) unmarkAdded(src, d.id)
                set { copy(newAsset = true, newSrc = src, newMedia = emptyList(), newDevice = d, assetId = null) }
            }
        }
    }

    /** Adds a scanned device straight away as a draft asset (its + button), filled in from what the scan found. */
    fun addDevice(d: Device) = addDevices(listOf(d))

    /**
     * Adds [devices] as draft assets in the current place, filled in from each device (name, type, BLE tag / IP, a note).
     * A device already added in this scan, or still being added (a second tap on + before the first is written), is
     * skipped, so each device makes one asset however often it is tapped.
     */
    private fun addDevices(devices: List<Device>) {
        if (!mayCollect()) return flashViewOnly()
        val src = scanSource
        val busy = addedIn(src)
        val fresh = devices.distinctBy { it.id }.filter { it.id !in busy }
        if (fresh.isEmpty()) return
        val ids = fresh.map { it.id }.toSet()
        // Marked before anything is written (on the main thread, as the taps arrive): the rows show as added at once.
        set { copy(devAdding = devAdding + (src to (devAdding[src].orEmpty() + ids))) }
        val place = AssetPlace(currentProp, tgtLoc, tgtWhere, tgtPlace)
        write {
            try {
                var made = 0
                fresh.forEach { d ->
                    val created = assetUseCases.create(
                        deviceName(d), "", emptyList(), prefillFor(d), false,
                        place = place, captures = emptyList(), device = d, deviceSource = src,
                    )
                    markAdded(src, d.id, created.id)
                    made++
                }
                val added = UiText.Plural(R.plurals.scrt_assets_added_as_drafts, made)
                flash(UiText.Res(R.string.notice_with_source, listOf(added, UiText.Res(src.viaRes))))
            } finally {
                // Added (now in devAdded) or failed (it can be tapped again): no longer in progress.
                set { copy(devAdding = devAdding + (src to (devAdding[src].orEmpty() - ids))) }
            }
        }
    }

    fun openAsset(id: String) = set { copy(assetId = id, newAsset = false, assetTab = null) }

    /** Opens asset [id] on its Validation tab: a row's failed or needs-a-look validation mark (the web's badge). */
    fun openAssetValidation(id: String) = set { copy(assetId = id, newAsset = false, assetTab = DetailTabKind.VALIDATION) }

    /** The details screen has opened on [UiState.assetTab]: later it keeps the tab its user picks. */
    fun assetTabOpened() = set { if (assetTab == null) this else copy(assetTab = null) }

    /** The "+" button: the new-asset form, only for someone who may add assets (Collect); the web hides Add otherwise. */
    fun openNew() {
        if (!mayCollect()) return flashViewOnly()
        set { copy(newAsset = true, newSrc = Source.MANUAL, newMedia = emptyList(), newDevice = null, assetId = null) }
    }
    fun closeDetail() {
        // A camera shot that never became an asset is thrown away with the form.
        if (state.newAsset) fileUseCases.deleteOwned(state.newMedia)
        set { copy(assetId = null, assetTab = null, newAsset = false, newMedia = emptyList(), newDevice = null) }
    }

    /** A shot the new asset's form opened with was removed there: it isn't made into the asset, and its file goes. */
    fun dropNewMedia(m: Media) {
        if (!state.newAsset) return
        fileUseCases.deleteOwned(state.newMedia.filter { it.id == m.id })
        set { copy(newMedia = newMedia.filterNot { it.id == m.id }) }
    }

    /** Camera tab: open the new-asset details with these photos/videos already attached. */
    private fun openNewWithMedia(media: List<Media>) {
        if (media.isEmpty()) return
        if (!mayCollect()) return flashViewOnly()
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
        /** The shots as the page has them (sorted, or moved between kinds by hand); null: sort them again. */
        shots: List<Media>? = null,
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
                captures = shots?.filter { m -> s.newMedia.any { it.id == m.id } } ?: s.newMedia, device = s.newDevice, deviceSource = s.newSrc,
                sortCaptures = shots == null,
            )
            if (created.via == CreateAssetUseCase.Via.SCAN) s.newDevice?.let { markAdded(s.newSrc, it.id, created.id) }
            // Shots left out over the photo limits: their files go, and the limit is said instead.
            fileUseCases.deleteOwned(created.leftOut.refused)
            val refused = photoLimitNotices(created.leftOut)
            when (created.via) {
                CreateAssetUseCase.Via.CAMERA -> flash(refused.firstOrNull() ?: UiText.Res(R.string.notice_with_source, listOf(savedAs, UiText.Res(Source.CAMERA.viaRes))))
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
    fun openCam() = if (!mayCollect()) flashViewOnly() else set { copy(camOpen = true) }

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
    /**
     * Ticks or unticks a shot in the strip; past the photos its kind (nameplate, asset, label ID) or one asset may have,
     * it says so instead.
     */
    fun toggleStrip(id: String) {
        val s = state
        if (id in s.camSel) return set { copy(camSel = camSel - id) }
        val m = s.captures.firstOrNull { it.id == id } ?: return
        // Many assets: every shot is its own file, so there is nothing to count.
        if (s.camMany) return set { copy(camSel = camSel + id) }
        viewModelScope.launch {
            shotLock.withLock {
                val refusal = assetUseCases.sortShot.refusal(m, tickedShots())
                if (refusal == null) set { copy(camSel = camSel + id) } else flash(refusal.notice())
            }
        }
    }

    /** The shots ticked in the strip. */
    private fun tickedShots(): List<Media> = state.captures.filter { it.id in state.camSel }

    /** One shot is sorted and checked at a time, so two quick shots can't both take the last room of a kind. */
    private val shotLock = Mutex()
    fun setVideoMode(v: Boolean) = set { copy(videoMode = v) }

    /**
     * A photo/video from the in-app camera joins the strip and the camera stays open for more; Done ([doneCapturing])
     * makes one asset of the ticked ones. Each photo is read on the device as it is taken and sorted into nameplate, label
     * ID or asset: it is ticked while its kind and the asset still have room under the property's photo rules, else it
     * stays unticked and the camera says why (a sixth nameplate photo where five are allowed).
     */
    fun addCapture(m: Media) {
        // Many assets: each shot goes to the server as it is, ticked; it isn't sorted or counted against the photo rules.
        if (state.camMany) return set { copy(captures = captures + m, camSel = camSel + m.id) }
        // Ticked at once, so the count keeps up with a burst of shots; sorted next (one at a time, in the order taken),
        // and unticked again if its kind or the asset has no room left.
        unsorted += m.id
        set { copy(captures = captures + m, camSel = camSel + m.id) }
        viewModelScope.launch {
            shotLock.withLock {
                try {
                    // Measured against the shots already sorted, not the later ones still waiting their turn.
                    val sorted = assetUseCases.sortShot(m, tickedShots().filter { it.id != m.id && it.id !in unsorted })
                    // Gone meanwhile (Done, discarded): nothing to tick.
                    if (state.captures.none { it.id == m.id }) return@withLock
                    set {
                        copy(
                            captures = captures.map { if (it.id == m.id) sorted.shot else it },
                            camSel = if (sorted.refusal == null) camSel else camSel - m.id,
                        )
                    }
                    sorted.refusal?.let { flash(it.notice()) }
                } finally {
                    unsorted -= m.id
                }
            }
        }
    }

    /** Shots in the strip ticked as they came in and not sorted yet ([addCapture]). Main thread only. */
    private val unsorted = mutableSetOf<String>()

    /**
     * Done in the camera: Single, the ticked shots open one new asset's details (sorted into their kinds, then read by
     * the AI); Multiple, they join the review. With nothing ticked, Multiple goes back to the shots already waiting in
     * the review ([reviewWaiting], after "Take more").
     */
    fun doneCapturing(reviewWaiting: Boolean = false) {
        if (!mayCollect()) return flashViewOnly()
        val picked = state.captures.filter { it.id in state.camSel }
        if (state.camMany && picked.isEmpty() && reviewWaiting) return set { copy(camOpen = false, camReview = true) }
        if (picked.isEmpty()) return
        if (state.camMany) return sendShots(picked)
        set { copy(captures = captures.filter { it.id !in camSel }, camSel = emptySet(), camOpen = false) }
        openNewWithMedia(picked)
    }

    /**
     * Gallery button in the camera: the picked photos/videos join the strip as if just taken ([addCapture]: Single sorts
     * and ticks each while there is room, Multiple ticks them all) and the camera stays open; Done takes them with the
     * shots. They aren't the app's files, so discarding the strip leaves them in the gallery.
     */
    fun addPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!mayCollect()) return flashViewOnly()
        viewModelScope.launch {
            fileUseCases.describeMedia(uris.map { it.toString() }).forEach(::addCapture)
        }
    }

    /**
     * The camera's One asset / Many assets. Shots taken one way aren't used the other (One asset sorts and counts them;
     * Many assets sends each as it is), so with shots in the strip, or waiting in the review after Take more
     * ([reviewWaiting]; [discardReview] throws those away), it asks the same question as leaving the camera ("Discard
     * your photos?"): Discard deletes them and switches, Keep stays as it is.
     */
    fun setCamMany(many: Boolean, reviewWaiting: Boolean = false, discardReview: () -> Unit = {}) {
        if (many == state.camMany) return
        val strip = state.captures.isNotEmpty()
        val review = !many && reviewWaiting
        if (!strip && !review) return set { copy(camMany = many) }
        afterDiscard = {
            if (review) {
                discardReview()
                fileUseCases.deleteOwned(camSent)
                camSent = emptyList()
            }
            set { copy(camMany = many) }
        }
        discardMedia = if (strip) state.captures else emptyList()
        discardStrip = strip
        set { copy(leaveAsk = LeaveAsk.PHOTOS) }
    }

    /**
     * Multiple: [shots] go to the Camera tab's review, as files picked on the File tab do (Adding from files needs
     * Collect): there they can be previewed or removed, then Read sends each to the server as its own file. The shots
     * leave the strip; their files go once the review is done with ([closeCamReview]).
     */
    private fun sendShots(shots: List<Media>) {
        if (!mayUploadFiles()) return flashViewOnly()
        camSent = camSent + shots
        set {
            copy(
                captures = captures.filter { c -> shots.none { it.id == c.id } }, camSel = camSel - shots.map { it.id }.toSet(),
                camOpen = false, camReview = true, camToSend = shots,
            )
        }
    }

    /** The Camera's import has taken the shots to read. */
    fun shotsSent() = set { copy(camToSend = null) }

    /** Shots sent to the server, whose files on the device go once their review is done with. */
    private var camSent: List<Media> = emptyList()

    /** The Camera's review is done with (added, thrown away): back to the Camera tab's list; the sent shots' files go. */
    fun closeCamReview() {
        fileUseCases.deleteOwned(camSent)
        camSent = emptyList()
        set { copy(camReview = false) }
    }

    /** "Take more" on the Camera's review: back to the camera, still on Many assets. */
    fun backToCamera() = if (!mayCollect()) flashViewOnly() else set { copy(camReview = false, camOpen = true) }

    fun toggleRev(id: String) = set { copy(revOff = if (id in revOff) revOff - id else revOff + id) }

    // ---------- Files ----------

    fun cancelFile() = set { copy(fileOpen = false) }
    /** The File tab's page; the system file manager opens over it only for someone who may add assets (Collect). */
    fun openFile() = set { copy(fileOpen = true, revFile = false, pendingFilePicker = mayUploadFiles()) }
    fun filePickerLaunched() = set { copy(pendingFilePicker = false) }

    /**
     * The system file manager closed; [anyFiles]: files are waiting (picked now or before), so their review shows.
     * Uploading and reading them is presentation/fileimport's FileImportViewModel.
     */
    fun onFilesPicked(anyFiles: Boolean) = set { copy(fileOpen = false, revFile = anyFiles) }

    fun backToFiles() = if (!mayUploadFiles()) flashViewOnly() else set { copy(pendingFilePicker = true) }

    /** The File tab's review is done with (added, discarded or emptied): back to its asset list. */
    fun closeFiles() = set { copy(revFile = false, fileOpen = false) }

    // ---------- Validation ----------

    /** The signed-in user may run validations (the server's check permission; validate includes it). */
    val canCheck: Boolean get() = blocks.check == null

    /** The Validation page is on screen: opened, and the user may run checks (as the web's route guard). */
    val validationShown: Boolean get() = state.valOpen && canCheck

    // Side-menu pages always return to the Assets tab. It is also selected on open, so the
    // pager slides there hidden behind the page rather than visibly on close.
    fun openValidation() = ifPageAllowed(MenuItem.VALIDATION) { leaving { showValidation() } }
    private fun showValidation() {
        if (!canCheck) return
        set { copy(valOpen = true, cfgOpen = false, intOpen = false, syncOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    }
    fun closeValidation() = set { copy(valOpen = false, valJobId = null, valAssetId = null, tab = Tab.ASSETS, assetId = null, newAsset = false) }

    /** "Open in Validation" from an asset: the Validation page, on the run [jobId] (the asset closes). */
    fun openValidationRun(jobId: String) = openValidationAt(jobId, null)

    /** "Open in Validation" from an asset: the Validation page on the run [jobId] with [assetId]'s result open. */
    fun openValidationResult(jobId: String, assetId: String) = openValidationAt(jobId, assetId)

    private fun openValidationAt(jobId: String, assetId: String?) = ifPageAllowed(MenuItem.VALIDATION) { leaving {
        set {
            copy(
                valOpen = true, valJobId = jobId, valAssetId = assetId, cfgOpen = false, intOpen = false, syncOpen = false, tab = Tab.ASSETS, menuOpen = false,
                assetId = null, newAsset = false,
            )
        }
    } }

    /** Runs [open] when the user's group may open [page]'s page; otherwise says why (a link to a page they can't use). */
    private fun ifPageAllowed(page: MenuItem, open: () -> Unit) = menuBlock(page)?.let { flash(UiText.Res(it.message)) } ?: open()

    /** The Validation page opened the run (and the asset's result) asked for. */
    fun validationRunShown() = set { copy(valJobId = null, valAssetId = null) }

    // ---------- Integration ----------

    /** The signed-in user may configure the property (the server's configure permission): the Integration page shows only then. */
    val canConfigure: Boolean get() = blocks.configure == null

    /** The Users page is only for someone who may manage people and access (as the server's user list). */
    val canManageAccess: Boolean get() = blocks.manageAccess == null

    /** The Configuration / Integration / Users pages are on screen: opened, and still allowed (as the web's route guards). */
    val configShown: Boolean get() = state.cfgOpen && canConfigure
    val integrationShown: Boolean get() = state.intOpen && canConfigure
    val peopleShown: Boolean get() = state.adminPage == AdminPage.PEOPLE && canManageAccess

    fun openIntegration() = ifPageAllowed(MenuItem.INTEGRATION) { leaving { showIntegration() } }
    private fun showIntegration() {
        if (!canConfigure) return
        set { copy(intOpen = true, valOpen = false, cfgOpen = false, syncOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    }
    /** Back from Integration: to the assets, or to Configuration for a group that isn't offered them. */
    fun closeIntegration() = if (!seesAssets) showConfig() else set { copy(intOpen = false, tab = Tab.ASSETS) }

    // ---------- Sync settings ----------

    /** Sync settings of the open property (side menu, under it); it needs a downloaded property. */
    private fun showSync() {
        if (!hasProperty) return
        set { copy(syncOpen = true, valOpen = false, cfgOpen = false, intOpen = false, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    }

    /** Back from Sync settings: to the assets, or to Configuration for a group that isn't offered them. */
    fun closeSync() = if (!seesAssets) { set { copy(syncOpen = false) }; showConfig() } else set { copy(syncOpen = false, tab = Tab.ASSETS) }

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
                set { copy(loc = LocFilter.ALL, specLocs = emptyList(), specPath = emptyList(), specWhere = "", specSel = "", specKind = null, assetId = null, devAdded = emptyMap()) }
                if (state.tab !in navTabs) goTab(Tab.ASSETS)
            }
        }
        // A group that only configures isn't offered the assets: an open property shows Configuration, its first page,
        // also once the access arrives after the property opened (sign-in, a restart). A group that can't open a property
        // at all (no View) goes back to the property list.
        viewModelScope.launch {
            combine(
                downloads.observe().map { it.currentId }.distinctUntilChanged(), seesAssetsUseCase.observe(), opensPropertiesUseCase.observe(),
            ) { id, sees, opens -> Triple(id != null, sees, opens) }
                .collect { (open, sees, opens) ->
                    when {
                        !open -> Unit
                        !opens -> if (state.adminPage == null) openAdmin(AdminPage.PROPERTIES)
                        !sees && state.adminPage == null && !state.cfgOpen && !state.intOpen && !state.syncOpen -> showConfig()
                    }
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
            // Properties the user lost access to are removed first (online), so their sync doesn't start and then get
            // closed under it.
            val lost = if (downloads.isOnline()) removeLostProperties.beforeSyncStarts(LOST_CHECK_WAIT_MS) else null
            lost?.let(::onLostProperties)
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
    fun openConfig() = ifPageAllowed(MenuItem.CONFIGURATION) { leaving { showConfig() } }
    private fun showConfig() {
        if (!canConfigure) return
        set { copy(cfgOpen = true, valOpen = false, intOpen = false, syncOpen = false, cfgPage = CfgPage.BUILDING, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false) }
    }
    /** Back from Configuration: to the assets, or (Configuration being a configure-only group's first page) the side menu. */
    fun closeConfig() = if (!seesAssets) openMenu() else set { copy(cfgOpen = false, cfgPage = CfgPage.BUILDING, tab = Tab.ASSETS) }

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

    /**
     * Back in the foreground: the properties are read again, and with them their settings (Bluetooth / Wi-Fi onboarding,
     * photo rules), so a change made elsewhere (the web, another phone) shows without restarting the app. Offline the
     * last known settings stay.
     */
    fun onForeground() {
        viewModelScope.launch {
            try {
                propertyUseCases.refresh()
            } catch (_: OnlineException) {
                // Offline or refused: nothing to say, the settings already shown stay.
            }
        }
    }

    fun setOnboarding(t: Tab, on: Boolean) {
        val channel = t.channel ?: return
        if (channel in onboardingPending) return
        val prop = currentProp
        onboardingPending = onboardingPending + (channel to on)
        viewModelScope.launch {
            try {
                propertyUseCases.setOnboarding(prop, channel, on)
                flash(
                    UiText.Res(
                        if (t == Tab.BT) (if (on) R.string.scrt_onboarding_bt_on else R.string.scrt_onboarding_bt_off)
                        else if (on) R.string.scrt_onboarding_wifi_on else R.string.scrt_onboarding_wifi_off,
                    ),
                )
            } catch (e: OnlineException) {
                flash(e.error.toUiText())
            } finally {
                // Saved: the server's settings now show; failed: the switch goes back to them.
                onboardingPending = onboardingPending - channel
            }
        }
    }

    // ---------- The signed-in user's access ----------

    /** Changes with every change to the access; a screen that reads it redraws its permission checks then. */
    val accessStamp: Int get() = accessVersion

    // Last in the class, so everything it reads is set up before the access's current value arrives.
    init {
        // Every change to the access re-reads the checks on screen, and closes what is no longer allowed.
        viewModelScope.launch {
            observeAccessChanges().collect {
                accessVersion++
                closeWhatIsNotAllowed()
            }
        }
        viewModelScope.launch { observeAccessFailed().collect { accessFailed = it } }
        viewModelScope.launch { observeGroupName().collect { groupName = it } }
        // A downloaded property the server no longer lists (access removed, or deleted) has its data deleted from the
        // device. When it was the one open, the user is taken to the property list and told; its data is already gone,
        // so nothing asks to keep unsaved changes first.
        viewModelScope.launch {
            removeLostProperties().collect(::onLostProperties)
        }
    }

    /** Says what was removed; when it was the property open, the user is taken to the property list. */
    private fun onLostProperties(lost: LostProperties) {
        if (lost.wasCurrent) {
            showAdmin(AdminPage.PROPERTIES)
            flash(UiText.Res(R.string.property_access_removed))
        } else {
            flash(UiText.Plural(R.plurals.property_access_removed_others, lost.ids.size, listOf(lost.ids.size)))
        }
    }

    /**
     * As the web's route guards, re-checked whenever the access changes: Validation needs Check, Configuration and
     * Integration need Configure and the Users page needs Manage access; a page whose permission is lost (or not known
     * yet) closes. Without Collect the camera closes and the file manager isn't launched. Unsaved changes on a closed
     * page are dropped: the server would refuse them now.
     */
    private fun closeWhatIsNotAllowed() {
        val s = state
        if (s.valOpen && !canCheck) set { copy(valOpen = false, valJobId = null, valAssetId = null, tab = Tab.ASSETS) }
        if ((s.cfgOpen || s.intOpen) && !canConfigure) set { copy(cfgOpen = false, intOpen = false, cfgPage = CfgPage.BUILDING, tab = Tab.ASSETS) }
        if (s.adminPage == AdminPage.PEOPLE && !canManageAccess) closeAdmin()
        // A group that can't open a property goes back to the list; one that only configures has no assets page to fall
        // back on: Configuration is its first page.
        if (hasProperty && !opensProperties && state.adminPage == null) openAdmin(AdminPage.PROPERTIES)
        else if (hasProperty && !seesAssets && state.adminPage == null && !state.cfgOpen && !state.intOpen && !state.syncOpen) showConfig()
        if (!mayUploadFiles()) set { copy(pendingFilePicker = false) }
        if (!mayCollect()) {
            set { copy(camOpen = false) }
        } else if (state.tab != Tab.CAMERA && !state.camOpen) {
            // Collect arrived: the camera is ready again for when its tab opens (as before the access was known).
            set { copy(camOpen = true) }
        }
    }
}
