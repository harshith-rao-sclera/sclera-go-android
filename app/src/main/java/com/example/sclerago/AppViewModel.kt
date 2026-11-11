package com.example.sclerago

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sclerago.data.*
import com.example.sclerago.services.validation.*
import com.example.sclerago.services.media.ImportedFile
import com.example.sclerago.services.media.Media
import com.example.sclerago.services.media.describeFiles
import com.example.sclerago.services.media.describeVisualMedia
import com.example.sclerago.services.media.formatSize
import com.example.sclerago.services.media.timeLabel
import com.example.sclerago.services.scan.ScanService
import com.example.sclerago.services.scan.UNKNOWN_DEVICE
import com.example.sclerago.services.scan.mentions
import com.example.sclerago.services.scan.scanKey
import com.example.sclerago.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class Tab { BT, NET, ASSETS, CAMERA, FILE }
enum class LocFilter { ALL, SPEC, NONE }
enum class Sheet { NONE, LOC, PROP }
enum class SheetFor { FILTER, ASSET, BULK }
enum class NetMode { WIFI, AP }
enum class MenuItem { ASSETS, VALIDATION, CONFIGURATION, AUDIT, SURVEY, INTEGRATION }
enum class CfgPage { BUILDING, FIELDS, GEO }

data class LocValue(val loc: String?, val where: String)

/** The "Run validation" sheet: what to check (a template or saved validation id) and which assets. */
data class RunSheet(val sourceId: String = "quick", val scope: ValScope = ValScope.ALL, val chosen: Set<Int> = emptySet())

/** Creating or editing a saved validation. [id] is null for a new one. */
data class SavedEditor(val id: String?, val name: String, val ruleIds: Set<String>)

data class Preview(
    val name: String,
    val sub: String,
    val bg: Color,
    val plate: Boolean,
    val video: Boolean,
    val media: Media? = null,
)

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
    val added: Set<String> = emptySet(),
    val specLocs: List<String> = listOf("location1"),
    val specPath: List<String> = listOf("building 1", "floor 1", "location1"),
    val specWhere: String = "location1 · floor 1",
    val specSel: String = "l1",
    val locOverride: Map<Int, LocValue> = emptyMap(),
    val sheet: Sheet = Sheet.NONE,
    val sheetFor: SheetFor = SheetFor.FILTER,
    val prop: String = "p1",
    val propQ: String = "",
    val stOver: Map<Int, Status> = emptyMap(),
    val pick: Set<Int> = emptySet(),
    val pv: Preview? = null,
    val assetId: Int? = null,
    val newAsset: Boolean = false,
    /** Scanned device the new-asset form was opened from (null = added manually). */
    val newDevice: Device? = null,
    val newSrc: Source = Source.MANUAL,
    /** Photos/videos the new-asset form opens with (Camera tab: the shot just taken). */
    val newMedia: List<Media> = emptyList(),
    /** Scanned device id -> asset created from it in this session. */
    val deviceAsset: Map<String, Int> = emptyMap(),
    val extra: List<Asset> = emptyList(),
    val deleted: Set<Int> = emptySet(),
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
    val savedDetails: Map<Int, Map<String, String>> = emptyMap(),
    /** Media attached to existing assets from the detail screen. */
    val mediaAdded: Map<Int, List<Media>> = emptyMap(),
    val nextId: Int = 100,
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
    /** When each asset was last added or edited (for "New or changed"). */
    val touched: Map<Int, Long> = emptyMap(),
    // ---- Configuration page
    val cfgOpen: Boolean = false,
    val cfgPage: CfgPage = CfgPage.BUILDING,
    /** Edited building → floor → location trees, per property (falls back to the seed tree). */
    val trees: Map<String, List<LocNode>> = emptyMap(),
    val nodeSeq: Int = 1,
    val fieldCfg: List<FieldCfg> = DEFAULT_FIELDS,
    val photoReqs: List<PhotoReq> = DEFAULT_PHOTO_REQS,
    val geoEnforce: Boolean = false,
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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf(UiState())
        private set

    private fun set(f: UiState.() -> UiState) {
        state = state.f()
    }

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

    val assets: List<Asset>
        get() = with(state) {
            (extra + SEED_ASSETS)
                .filter { it.id !in deleted && it.prop == prop }
                .map { a ->
                    var x = a
                    locOverride[a.id]?.let { x = x.copy(loc = it.loc, where = it.where) }
                    stOver[a.id]?.let { x = x.copy(st = it) }
                    savedDetails[a.id]?.get("name")?.takeIf { it.isNotBlank() }?.let { x = x.copy(name = it) }
                    mediaAdded[a.id]?.let { x = x.copy(media = x.media + it) }
                    x
                }
        }

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

    val propName: String get() = (PROPERTIES.firstOrNull { it.id == state.prop } ?: PROPERTIES[0]).name
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
    val selOn: Boolean get() = pickedIds.isNotEmpty() && listy && !showDetail && !sheetOpen
    val showFabs: Boolean get() = listy && !showDetail && !sheetOpen && !selOn

    /** Only ids that still exist in the current property count as picked. */
    val pickedIds: Set<Int> get() = state.pick.filter { id -> assets.any { it.id == id } }.toSet()

    private val curItems: List<Asset> get() = if (listy) listFor(state.tab).items else emptyList()
    val allPicked: Boolean get() = curItems.isNotEmpty() && curItems.all { it.id in pickedIds }
    val curCount: Int get() = curItems.size

    fun propRows(): List<Pair<Property, String>> {
        val ql = state.propQ.trim().lowercase()
        val all = (state.extra + SEED_ASSETS).filter { it.id !in state.deleted }
        return PROPERTIES.filter { ql.isEmpty() || it.name.lowercase().contains(ql) }.map { p ->
            val n = all.count { it.prop == p.id }
            p to if (n > 0) plural(n, "asset", "assets") + (if (p.gps) " · 5 buildings" else "") else "No assets yet"
        }
    }

    // ---------- Scanning: the scans themselves live in services/scan/ScanService ----------

    private val scans = ScanService(app, viewModelScope)

    override fun onCleared() {
        scans.shutdown()
        super.onCleared()
    }

    /** The Network tab's current mode as a scan. */
    private val netTarget: ScanService.Target
        get() = if (state.netMode == NetMode.AP) ScanService.Target.ACCESS_POINTS else ScanService.Target.NETWORK

    /** A Bluetooth scan is running right now. */
    val btOn: Boolean get() = scans.isScanning(ScanService.Target.BLUETOOTH)

    /** The scan for the Network tab's current mode (Wi-Fi network / access points) is running right now. */
    val netOn: Boolean get() = scans.isScanning(netTarget)

    /** Runtime permissions the UI should ask for now (or null); the answer goes to [onScanPermission]. */
    val scanPermissionRequest: List<String>? get() = scans.permissionRequest

    fun onScanPermission(granted: Boolean) = scans.onPermissionResult(granted)

    val btCard: ScanService.Card
        get() = btDevices.let { d -> scans.card(ScanService.Target.BLUETOOTH, d.size, d.count { it.isNew }) }

    val netCard: ScanService.Card
        get() = netDevices.let { d -> scans.card(netTarget, d.size, d.count { it.isNew }) }

    private fun deviceName(d: Device) = if (d.name in setOf(UNKNOWN_DEVICE, "Hidden network")) "" else d.name

    /** Where a scanned tag / IP / MAC can show up on an asset: its subtitle, BLE tag, IP address or notes. */
    private fun assetKeys(a: Asset): List<String> {
        val f = DETAILS[a.id]?.fields.orEmpty() + state.savedDetails[a.id].orEmpty()
        return listOf(a.detail) + listOf("ble", "ip", "label", "note").mapNotNull { f[it] }
    }

    private val scanSource: Source
        get() = when {
            state.tab == Tab.BT -> Source.BT
            state.netMode == NetMode.AP -> Source.AP
            else -> Source.WIFI
        }

    /** For matching scanned devices to assets: each [src] asset's id with all the text a tag / IP / MAC could appear in. */
    private fun assetTexts(src: Source, list: List<Asset> = assets): List<Pair<Int, String>> =
        list.filter { it.src == src }.map { it.id to assetKeys(it).joinToString("\n") }

    /** The asset a scanned device already belongs to: one made from it here, or one whose fields mention it. */
    private fun assetFor(d: Device, list: List<Asset>, texts: List<Pair<Int, String>>): Int? {
        state.deviceAsset[d.id]?.takeIf { id -> list.any { it.id == id } }?.let { return it }
        val key = scanKey(d)
        return texts.firstOrNull { (_, text) -> mentions(text, key) }?.first
    }

    private fun assetFor(d: Device, src: Source): Int? {
        val list = assets
        return assetFor(d, list, assetTexts(src, list))
    }

    /** Scan rows, built once per refresh: a network sweep can return hundreds of hosts. */
    private fun rows(devices: List<Device>, src: Source): List<DeviceRow> {
        val list = assets
        val texts = assetTexts(src, list)
        return devices.map { DeviceRow(it, it.id !in state.added && assetFor(it, list, texts) == null) }
    }

    val btDevices: List<DeviceRow> get() = rows(scans.rows(ScanService.Target.BLUETOOTH), Source.BT)
    val netDevices: List<DeviceRow>
        get() = rows(scans.rows(netTarget), if (state.netMode == NetMode.AP) Source.AP else Source.WIFI)

    // Camera: every selected capture becomes its own draft asset.
    private fun captureName(m: Media) = (if (m.video) "Asset from video " else "Asset from photo ") + timeLabel(m.createdAt)

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
            var o = copy(tab = t, assetId = null, newAsset = false, newDevice = null, newMedia = emptyList(), pick = emptySet())
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
            Tab.BT -> scans.start(ScanService.Target.BLUETOOTH)
            Tab.NET -> scans.start(netTarget)
            else -> {}
        }
        if (from == Tab.BT && t != Tab.BT) scans.stop(ScanService.Target.BLUETOOTH)
        if (from == Tab.NET && t != Tab.NET) scans.stop(ScanService.Target.ACCESS_POINTS)
    }

    fun onPageSettled(page: Int) {
        val t = Tab.entries[page]
        if (t != state.tab) goTab(t)
    }

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
            MenuItem.ASSETS -> goTab(Tab.ASSETS)
            MenuItem.VALIDATION -> openValidation()
            MenuItem.CONFIGURATION -> openConfig()
            else -> flash(item.name.lowercase().replaceFirstChar { it.uppercase() } + " is coming soon")
        }
    }
    fun closeProp() = set { copy(sheet = Sheet.NONE, propQ = "") }
    fun setPropQ(v: String) = set { copy(propQ = v) }
    fun pickProp(id: String) {
        val changed = id != state.prop
        set { copy(prop = id, sheet = Sheet.NONE, loc = LocFilter.ALL, assetId = null, pick = emptySet(), propQ = "") }
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
                set { copy(sheet = Sheet.NONE, locOverride = locOverride + (detail.id to v)) }
                touch(detail.id)
            }
            state.sheetFor == SheetFor.BULK -> {
                val ids = pickedIds
                set { copy(sheet = Sheet.NONE, locOverride = locOverride + ids.associateWith { v }, pick = emptySet()) }
                touch(*ids.toIntArray())
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
        set { copy(btScan = scan) }
        if (scan) scans.start(ScanService.Target.BLUETOOTH)
    }
    fun setNetScan(scan: Boolean) {
        set { copy(netScan = scan) }
        if (scan) scans.start(netTarget)
    }
    fun setNetMode(m: NetMode) {
        set { copy(netMode = m) }
        if (state.netScan) scans.start(netTarget)
    }
    /** Tap on the radar card: pause a running scan, or start a new one. */
    fun toggleBt() = scans.toggle(ScanService.Target.BLUETOOTH)
    fun toggleNet() = scans.toggle(netTarget)
    /** Tap on a scanned device: open its asset if it already has one, otherwise the add form prefilled from it. */
    fun openDevice(d: Device) {
        val existing = assetFor(d, scanSource)
        set {
            if (existing != null) copy(assetId = existing, newAsset = false, newDevice = null, searchOpen = false, pick = emptySet())
            else copy(newAsset = true, newDevice = d, newSrc = scanSource, newMedia = emptyList(), assetId = null, searchOpen = false, pick = emptySet())
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
        set { copy(stOver = stOver + ids.associateWith { Status.VALIDATED }, pick = emptySet()) }
        touch(*ids.toIntArray())
        flash(plural(ids.size, "asset", "assets") + " validated")
    }

    /** Status change for one asset (from its detail screen). */
    fun setAssetStatus(id: Int, st: Status) {
        set { copy(stOver = stOver + (id to st)) }
        touch(id)
        flash("Asset " + st.label.lowercase())
    }

    fun submitSel() {
        val ids = pickedIds
        set { copy(stOver = stOver + ids.associateWith { Status.SUBMITTED }, pick = emptySet()) }
        touch(*ids.toIntArray())
        flash(plural(ids.size, "asset", "assets") + " submitted")
    }

    fun askDeleteSel() = set { copy(confirmDeleteSel = true) }
    fun cancelDeleteSel() = set { copy(confirmDeleteSel = false) }
    fun deleteSel() {
        val ids = pickedIds
        set { copy(deleted = deleted + ids, pick = emptySet(), confirmDeleteSel = false) }
        flash(plural(ids.size, "asset", "assets") + " deleted")
    }

    fun openSearch() = set { copy(searchOpen = true) }
    fun closeSearch() = set { copy(searchOpen = false, q = "") }
    fun setQ(v: String) = set { copy(q = v) }

    fun openNew() = set { copy(newAsset = true, newDevice = null, newSrc = Source.MANUAL, newMedia = emptyList(), assetId = null, searchOpen = false, pick = emptySet()) }
    fun closeDetail() {
        // A camera shot that never became an asset is thrown away with the form.
        if (state.newAsset) deleteOwned(state.newMedia)
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
        set { copy(savedDetails = savedDetails + (id to values), stOver = stOver + (id to Status.DRAFT)) }
        touch(id)
        flash("Saved as draft")
    }

    /** Submit on an asset's details: keeps the values and locks it as Submitted. */
    fun submitAsset(id: Int, values: Map<String, String>) {
        set { copy(savedDetails = savedDetails + (id to values), stOver = stOver + (id to Status.SUBMITTED)) }
        touch(id)
        flash("Asset submitted · it can’t be edited now")
    }

    fun createAsset(
        name: String,
        detail: String,
        media: List<Media> = emptyList(),
        values: Map<String, String> = emptyMap(),
        submit: Boolean = false,
    ) {
        val id = state.nextId
        val st = if (submit) Status.SUBMITTED else Status.DRAFT
        val savedAs = if (submit) "Asset submitted" else "Asset added as draft"
        if (values.isNotEmpty()) set { copy(savedDetails = savedDetails + (id to values)) }
        val camShots = state.newMedia
        if (camShots.isNotEmpty()) {
            // From the Camera tab: the shot(s) plus anything added in the form; stay on the camera for the next one.
            val first = camShots.first()
            val a = Asset(
                id, name.ifBlank { captureName(first) },
                if (detail.isBlank() || detail == "Added manually") (if (first.video) "Video ${first.durationText}" else "Photo") + " · captured " + timeLabel(first.createdAt).take(5) else detail,
                tgtLoc, tgtWhere, Source.CAMERA, st, typeOf(name), state.prop, camShots,
            )
            set {
                copy(
                    extra = listOf(a) + extra, nextId = nextId + 1, newAsset = false, newMedia = emptyList(),
                    mediaAdded = if (media.isEmpty()) mediaAdded else mediaAdded + (id to media),
                )
            }
            touch(id)
            flash("$savedAs · via Camera")
            return
        }
        if (media.isNotEmpty()) set { copy(mediaAdded = mediaAdded + (id to media)) }
        val d = state.newDevice
        if (d != null) {
            // From a scan: keep the device's own line unless the form gave something better, and stay on the scan.
            val src = state.newSrc
            val a = Asset(
                id, name.ifBlank { d.name }, if (detail.isBlank() || detail == "Added manually") d.sub else detail,
                // The name typed on the form wins when it says what the asset is; otherwise keep what the scan identified.
                tgtLoc, tgtWhere, src, st, typeOf(name).takeIf { name.isNotBlank() && it != AssetType.GENERIC } ?: d.type, state.prop,
            )
            set {
                copy(
                    extra = listOf(a) + extra, nextId = nextId + 1, newAsset = false, newDevice = null,
                    added = added + d.id, deviceAsset = deviceAsset + (d.id to id),
                )
            }
            touch(id)
            flash("$savedAs · ${src.via}")
            return
        }
        val a = Asset(
            id, name.ifBlank { "Untitled asset" }, detail.ifBlank { "Added manually" },
            tgtLoc, tgtWhere, Source.MANUAL, st, typeOf(name), state.prop,
        )
        set {
            copy(
                extra = listOf(a) + extra, nextId = nextId + 1, newAsset = false, tab = Tab.ASSETS,
                statusFilter = statusFilter + (Tab.ASSETS to null),
            )
        }
        touch(id)
        flash(savedAs)
    }

    fun deleteAsset(id: Int) {
        set { copy(deleted = deleted + id, assetId = null) }
        flash("Asset deleted")
    }

    fun saveDetail(id: Int, values: Map<String, String>) {
        set { copy(savedDetails = savedDetails + (id to values)) }
        touch(id)
    }

    /** Photos/videos from the asset's camera ("Add photo") attached to an existing asset. */
    fun attachMedia(assetId: Int, media: List<Media>) {
        if (media.isEmpty() || assetId < 0) return
        set { copy(mediaAdded = mediaAdded + (assetId to (mediaAdded[assetId].orEmpty() + media))) }
        touch(assetId)
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
            openNewWithMedia(describeVisualMedia(getApplication(), uris))
        }
    }

    fun discardCam() {
        deleteOwned(state.captures.filter { it.id in state.camSel })
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
        val base = state.nextId
        val made = picked.mapIndexed { i, r ->
            val m = r.media!!
            Asset(
                base + i, r.name, (if (m.video) "Video ${m.durationText}" else "Photo") + " · captured " + timeLabel(m.createdAt).take(5),
                tgtLoc, tgtWhere, Source.CAMERA, Status.INPUT, AssetType.GENERIC, state.prop, listOf(m),
            )
        }
        val used = picked.map { it.id }.toSet()
        set {
            copy(
                extra = made + extra, nextId = nextId + made.size, revOff = emptySet(),
                revCam = false, camOpen = false, camSel = camSel - used, captures = captures.filter { it.id !in used },
            )
        }
        touch(*made.map { it.id }.toIntArray())
        flash(plural(made.size, "asset", "assets") + " added · details needed")
    }

    private fun deleteOwned(list: List<Media>) {
        val owned = list.filter { it.owned }
        if (owned.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            owned.forEach { m -> runCatching { Uri.parse(m.uri).path?.let { File(it).delete() } } }
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
            val files = describeFiles(getApplication(), uris)
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
        val base = state.nextId
        val made = picked.mapIndexed { i, r ->
            Asset(base + i, r.name, r.file, tgtLoc, tgtWhere, Source.FILE, Status.INPUT, typeOf(r.name), state.prop)
        }
        set {
            copy(
                extra = made + extra, nextId = nextId + made.size, revOff = emptySet(),
                revFile = false, fileOpen = false, importFiles = emptyList(),
            )
        }
        touch(*made.map { it.id }.toIntArray())
        flash(plural(made.size, "asset", "assets") + " imported · details needed")
    }

    // ---------- Validation ----------

    private fun touch(vararg ids: Int) {
        val now = System.currentTimeMillis()
        set { copy(touched = touched + ids.associate { it to now }) }
    }

    /** The values and facts the validation rules look at for one asset (same as its Validation tab). */
    fun checkInputFor(a: Asset): CheckInput {
        val values = mapOf("name" to a.name, "sys" to "Generic", "typ" to "Generic", "sub" to "Generic") +
            DETAILS[a.id]?.fields.orEmpty() + state.savedDetails[a.id].orEmpty()
        val fromCam = a.src == Source.CAMERA
        return CheckInput(
            values, a.loc != null, a.st, fromCam, a.media.size + if (fromCam && a.media.isEmpty()) 3 else 0,
            required = requiredFields, minPhotos = minPhotos,
        )
    }

    /** Runs validation jobs and keeps saved validations (services/validation/ValidationService). */
    private val validation = ValidationService(viewModelScope)

    /** The user's saved validations. */
    val savedVals: List<SavedValidation> get() = validation.saved

    fun sourceName(id: String): String = validation.sourceName(id)

    fun sourceRules(id: String): List<Rule> = validation.sourceRules(id)

    /** Jobs for the current property, newest first. */
    val propJobs: List<ValJob> get() = validation.jobsFor(state.prop)

    fun lastRun(sourceId: String): Long? = validation.lastRun(state.prop, sourceId)

    /** Assets a run with [sheet]'s settings would cover. */
    fun scopeAssets(sheet: RunSheet): List<Asset> = when (sheet.scope) {
        ValScope.ALL -> assets
        ValScope.CHOOSE -> assets.filter { it.id in sheet.chosen }
        ValScope.CHANGED -> {
            val since = lastRun(sheet.sourceId)
            if (since == null) assets else assets.filter { (state.touched[it.id] ?: 0L) > since }
        }
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
        validation.start(
            sourceId, ids, label, state.prop,
            inputs = { assets.map { it to checkInputFor(it) } },
            startedAt = startedAt, instant = instant,
            onDone = { j -> if (!instant) flash("${j.title} finished · ${j.failed} failed · ${j.passed} passed") },
        )
    }

    fun openChecks(id: String?) = set { copy(valChecksFor = id) }
    fun openReview(jobId: Int?) = set { copy(valReview = jobId) }

    fun newSaved() = set { copy(valEditor = SavedEditor(null, "", emptySet())) }
    fun editSaved(id: String) {
        val sv = validation.saved.firstOrNull { it.id == id } ?: return
        set { copy(valEditor = SavedEditor(sv.id, sv.name, sv.ruleIds.toSet())) }
    }
    fun closeEditor() = set { copy(valEditor = null) }
    fun setEditorName(v: String) = set { copy(valEditor = valEditor?.copy(name = v)) }
    fun toggleEditorRule(rule: String) = set {
        val e = valEditor ?: return@set this
        copy(valEditor = e.copy(ruleIds = if (rule in e.ruleIds) e.ruleIds - rule else e.ruleIds + rule))
    }
    fun saveEditor() {
        val e = state.valEditor ?: return
        if (!validation.save(e.id, e.name, e.ruleIds)) return
        set { copy(valEditor = null) }
        flash(if (e.id == null) "Validation saved" else "Validation updated")
    }
    fun askDeleteSaved(id: String?) = set { copy(valConfirmDelete = id) }
    fun deleteSaved() {
        val id = state.valConfirmDelete ?: return
        validation.delete(id)
        set { copy(valConfirmDelete = null) }
        flash("Validation deleted")
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
        copy(cfgOpen = true, cfgPage = CfgPage.BUILDING, tab = Tab.ASSETS, menuOpen = false, assetId = null, newAsset = false, searchOpen = false, pick = emptySet())
    }
    fun closeConfig() = set { copy(cfgOpen = false, cfgPage = CfgPage.BUILDING, tab = Tab.ASSETS) }

    /** Switches the Configuration tab (Building info / Asset fields / Geofencing). */
    fun cfgGo(page: CfgPage) = set { copy(cfgPage = page) }

    // Building info: the property's building → floor → location tree (also used by the location picker).
    fun treeFor(prop: String = state.prop): List<LocNode> = state.trees[prop] ?: locationTree(prop)

    private fun editTree(f: (List<LocNode>) -> List<LocNode>) = set { copy(trees = trees + (prop to f(trees[prop] ?: locationTree(prop)))) }

    private fun nextNodeId(prefix: String): String {
        val id = "$prefix-n${state.nodeSeq}"
        set { copy(nodeSeq = nodeSeq + 1) }
        return id
    }

    fun addBuilding(name: String): String {
        val id = nextNodeId("b")
        editTree { it + LocNode(id, name.trim(), NodeKind.BUILDING, emptyList()) }
        flash("Building added")
        return id
    }

    fun addFloor(buildingId: String, name: String): String {
        val id = nextNodeId("f")
        editTree { t -> t.mapNode(buildingId) { b -> b.copy(kids = b.kids.orEmpty() + LocNode(id, name.trim(), NodeKind.FLOOR, emptyList())) } }
        flash("Floor added")
        return id
    }

    fun renameNode(id: String, name: String) {
        editTree { t -> t.mapNode(id) { it.copy(name = name.trim()) } }
        flash("Renamed")
    }

    fun deleteNodes(ids: Set<String>, what: String) {
        if (ids.isEmpty()) return
        editTree { it.removeNodes(ids) }
        flash("$what deleted")
    }

    /** Adds one location, or several at once (bulk: one name per line; codes get -1, -2, … when given). */
    fun addLocations(floorId: String, names: List<String>, type: String, code: String) {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val made = clean.mapIndexed { i, n ->
            val c = code.trim().let { if (it.isNotEmpty() && clean.size > 1) "$it-${i + 1}" else it }
            LocNode(nextNodeId("l"), n, NodeKind.LOCATION, null, 0, type.trim(), c)
        }
        editTree { t -> t.mapNode(floorId) { f -> f.copy(kids = f.kids.orEmpty() + made) } }
        flash(plural(made.size, "location", "locations") + " added")
    }

    fun updateLocation(id: String, name: String, type: String, code: String) {
        editTree { t -> t.mapNode(id) { it.copy(name = name.trim(), type = type.trim(), code = code.trim()) } }
        flash("Location saved")
    }

    // Asset fields and photos.
    val requiredFields: List<Pair<String, String>>
        get() = state.fieldCfg.filter { it.required && it.visible }.map { it.key to it.label }
    val minPhotos: Int get() = state.photoReqs.sumOf { it.min }

    fun toggleFieldVisible(key: String) = set {
        copy(fieldCfg = fieldCfg.map { f ->
            if (f.key != key || f.locked) f else f.copy(visible = !f.visible, required = if (f.visible) false else f.required)
        })
    }
    fun toggleFieldRequired(key: String) = set {
        copy(fieldCfg = fieldCfg.map { f ->
            if (f.key != key || f.locked) f else f.copy(required = !f.required, visible = f.visible || !f.required)
        })
    }
    fun addField(label: String, kind: FieldKind, required: Boolean) {
        val l = label.trim()
        if (l.isEmpty()) return
        val key = "cf_" + System.currentTimeMillis()
        set { copy(fieldCfg = fieldCfg + FieldCfg(key, l, kind, FieldSection.MORE, visible = true, required = required, custom = true)) }
        flash("Field added")
    }
    fun removeField(key: String) {
        set { copy(fieldCfg = fieldCfg.filter { it.key != key || !it.custom }) }
        flash("Field removed")
    }

    fun setPhotoLimits(id: String, min: Int, max: Int) = set {
        copy(photoReqs = photoReqs.map {
            if (it.id != id) it else {
                val mn = min.coerceIn(0, 9)
                it.copy(min = mn, max = max.coerceIn(maxOf(mn, 1), 9))
            }
        })
    }
    fun addPhotoReq(name: String, hint: String) {
        if (name.isBlank()) return
        set { copy(photoReqs = photoReqs + PhotoReq("p" + System.currentTimeMillis(), name.trim(), hint.trim(), 0, 1)) }
        flash("Photo type added")
    }
    fun removePhotoReq(id: String) = set { copy(photoReqs = photoReqs.filter { it.id != id }) }

    fun setGeoEnforce(on: Boolean) = set { copy(geoEnforce = on) }
}
