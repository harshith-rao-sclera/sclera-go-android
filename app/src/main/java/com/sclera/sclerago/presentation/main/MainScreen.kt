package com.sclera.sclerago.presentation.main

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.PremiseNotice
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.presentation.access.PeopleScreen
import com.sclera.sclerago.presentation.assets.AssetDetailRoute
import com.sclera.sclerago.presentation.assets.CameraPage
import com.sclera.sclerago.presentation.assets.FilePage
import com.sclera.sclerago.presentation.assets.assetList
import com.sclera.sclerago.presentation.assign.AssignSheet
import com.sclera.sclerago.presentation.assign.AssignViewModel
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessFailedBanner
import com.sclera.sclerago.presentation.components.CapturePreview
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.coverPageBehind
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.components.LocalUnsavedForms
import com.sclera.sclerago.presentation.components.LocationPicker
import com.sclera.sclerago.presentation.components.PermissionSheet
import com.sclera.sclerago.presentation.components.PremiseBanner
import com.sclera.sclerago.presentation.premise.PremisePrompt
import com.sclera.sclerago.presentation.premise.PremiseViewModel
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.ShakeEffect
import com.sclera.sclerago.presentation.components.ToastEffect
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.config.ConfigScreen
import com.sclera.sclerago.presentation.fileimport.DraftEditSheet
import com.sclera.sclerago.presentation.fileimport.FileImportViewModel
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.integration.IntegrationScreen
import com.sclera.sclerago.presentation.integration.ManagedSummarySheet
import com.sclera.sclerago.presentation.integration.ManagedSummaryViewModel
import com.sclera.sclerago.presentation.integration.MoveToManagedSheet
import com.sclera.sclerago.presentation.integration.MoveToManagedViewModel
import com.sclera.sclerago.presentation.launch.Entrance
import com.sclera.sclerago.presentation.launch.launchEntrance
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.property.PropertyListScreen
import com.sclera.sclerago.presentation.scan.BluetoothPage
import com.sclera.sclerago.presentation.scan.NetworkPage
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.validation.QuickValidate
import com.sclera.sclerago.presentation.syncsettings.SyncSettingsScreen
import com.sclera.sclerago.presentation.validation.ValidationScreen
import com.sclera.sclerago.presentation.validation.ValidationViewModel

private val TrackEasing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * What the File tab can upload to scan for assets: spreadsheets (xlsx, xlsm, xls, csv, tsv), PDFs, photos, videos, Word
 * (docx) and text (txt, md, json, xml), each at most 200 MB. Each pick is checked again by name ([IMPORTABLE_KINDS]).
 */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel.sheet.macroEnabled.12",
    "application/vnd.ms-excel",
    "application/pdf",
    "image/*",
    "video/*",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/*",
    "application/json",
    "application/xml",
)

@Composable
fun ScleraApp(
    vm: AppViewModel = hiltViewModel(),
    list: AssetListViewModel = hiltViewModel(),
    move: MoveToManagedViewModel = hiltViewModel(),
    assign: AssignViewModel = hiltViewModel(),
    summary: ManagedSummaryViewModel = hiltViewModel(),
    validation: ValidationViewModel = hiltViewModel(),
    files: FileImportViewModel = hiltViewModel(),
    /** The Camera's Many assets: its own import, apart from the File tab's. */
    shots: FileImportViewModel = hiltViewModel(key = CAMERA_IMPORT),
    bulk: BulkEditViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
    premise: PremiseViewModel = hiltViewModel(),
) {
    // Every form reports unsaved changes here, so tabs and side-menu pages can ask before dropping them.
    CompositionLocalProvider(LocalUnsavedForms provides vm.unsavedForms) {
        val s = vm.state
        // Each time the app comes back to the foreground, settings changed elsewhere (the web) are read again.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val watch = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.onForeground() }
            lifecycle.addObserver(watch)
            onDispose { lifecycle.removeObserver(watch) }
        }
        // Read here so everything below (the menu's sections, the + button, an asset's Assign and Move, the selection
        // dock's actions) is drawn again with the new answers whenever the signed-in user's access changes.
        vm.accessStamp
        // The app's messages (every page's, asset details' and the side-menu pages' too) show as the one toast at the top.
        ToastEffect(s.toast, s.toastOn)
        // Offline, the server-only actions are dimmed with a badge; a tap says why.
        val online by connectivity.online.collectAsStateWithLifecycle()
        val explainOffline = { why: OnlineOnly -> vm.flash(UiText.Res(why.message)) }
        // Validate (selection bar or an asset): the assets the Run validation sheet opens on, as the web's QuickValidate.
        var quickIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
        // Its templates are read as soon as a property is open, so the first tap on Validate opens the sheet at once.
        val currentProp = vm.currentProp
        LaunchedEffect(currentProp) { validation.prepare(currentProp) }
        val lists by list.state.collectAsStateWithLifecycle()
        // Bluetooth / Network drop out when the property's onboarding for them is off (Configuration →
        // Property Settings); the pager is rebuilt then, so page numbers always match the visible tabs.
        val tabs = vm.navTabs
        val pager = key(tabs) { rememberPagerState(initialPage = tabs.indexOf(s.tab).coerceAtLeast(0)) { tabs.size } }

        // Tab state drives the pager; a finished swipe drives the tab state.
        LaunchedEffect(s.tab, pager) {
            val i = tabs.indexOf(s.tab)
            if (i >= 0 && pager.currentPage != i) pager.animateScrollToPage(i, animationSpec = tween(380, easing = TrackEasing))
        }
        // Whenever scrolling stops (finished swipe, or a programmatic slide the user interrupted
        // with a touch), the page the pager rests on becomes the selected tab. The page is read against this pager's own
        // tabs: when the tabs change (Bluetooth / Network hidden once the property's settings arrive) a page number means
        // another tab in the new list (page 2, Assets of five, is File of three), which opened the file picker on start.
        LaunchedEffect(pager) {
            snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
                if (!scrolling) tabs.getOrNull(pager.currentPage)?.let(vm::onPageSettled)
            }
        }

        // The lists show the current property, narrowed by the location tab; a selection belongs to the page it was made on.
        LaunchedEffect(vm.locationFilter) { list.setScope(vm.locationFilter) }
        // A filter chosen on one property doesn't carry over to the next.
        LaunchedEffect(vm.currentProp) { list.bindProperty(vm.currentProp) }
        // The Network tab's list follows its mode: Wi-Fi network shows "wifi" assets, Access points "access_point" ones.
        LaunchedEffect(vm.netSource) { list.setNetworkSource(vm.netSource) }
        LaunchedEffect(s.tab, vm.currentProp, vm.showDetail, s.adminPage, s.valOpen, s.cfgOpen, s.intOpen, s.syncOpen) { list.clearSelection() }
        LaunchedEffect(list) { list.notices.collect { vm.flash(it) } }
        // Move to managed assets needs the property's VDMS link (checked again after the Integration page).
        LaunchedEffect(vm.currentProp, s.intOpen) { if (!s.intOpen) move.bind(vm.currentProp) }
        LaunchedEffect(move) { move.notices.collect { vm.flash(it) } }
        // Assign asset gives assets of the current property to a person.
        LaunchedEffect(vm.currentProp) { assign.bind(vm.currentProp) }
        LaunchedEffect(assign) { assign.notices.collect { vm.flash(it) } }
        LaunchedEffect(bulk) { bulk.notices.collect { vm.flash(it) } }

        // On the Assets page, shake the phone (two firm shakes) to jump straight to the camera; elsewhere a shake does nothing.
        val view = LocalView.current
        ShakeEffect {
            if (vm.openCameraByShake()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        // The scans need runtime permissions. When the scan service asks, a sheet explains why first
        // (PermissionSheet, below); Continue shows Android's own prompt, whose answer goes back to the service.
        var systemPromptOpen by remember { mutableStateOf(false) }
        val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            systemPromptOpen = false
            vm.onScanPermission(res.isNotEmpty() && res.values.all { it })
        }

        // The File tab hands off to Android's own file manager (Storage Access Framework).
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            files.add(uris)
            vm.onFilesPicked(uris.isNotEmpty() || files.hasWork)
        }
        LaunchedEffect(s.pendingFilePicker) {
            if (s.pendingFilePicker) {
                vm.filePickerLaunched()
                filePicker.launch(IMPORT_MIME_TYPES)
            }
        }

        val detail = vm.detailAsset
        // Each side-menu page shows only while its permission allows it (as the web's route guards).
        val validationShown = vm.validationShown
        val overlay = detail != null || s.sheet != Sheet.NONE || s.pv != null || s.menuOpen || validationShown || vm.configShown || vm.integrationShown || s.syncOpen || s.adminPage != null || move.state.sheet != null || summary.shown != null || assign.sheet != null || bulk.sheet != null || files.state.edit != null || shots.state.edit != null
        val detailView: @Composable (Asset) -> Unit = { d ->
            AssetDetailRoute(
                asset = d,
                isNew = s.newAsset,
                onBack = vm::closeDetail,
                onChangeLoc = vm::openLocAsset,
                onCreate = { name, info, media, values, submit, exception, place, shots ->
                    // A hand-made asset is shown in the full list, whatever status was being filtered.
                    if (vm.createAsset(name, info, media, values, submit, exception, place, shots) == CreateAssetUseCase.Via.MANUAL) list.setStatusFilter(Tab.ASSETS, null)
                },
                onDelete = vm::deleteAsset,
                onNotice = vm::flash,
                pickedPlace = s.detailPlace,
                onPlaceTaken = vm::detailPlaceTaken,
                // One in Managed Assets is read-only here: it can't be assigned or moved again.
                onAssign = when {
                    d.managed -> null
                    assign.mayAssign() -> ({ assign.open(setOf(d.id), d.assignedTo) })
                    // Someone who isn't an admin takes it for themselves, as the web's "Assign to me".
                    assign.mayTake() -> ({ assign.take(listOf(d)) })
                    else -> null
                },
                // Neither assign nor take: Assign still shows, greyed out, saying why (no Collect, or the access isn't known).
                assignReason = if (d.managed || assign.mayAssign() || assign.mayTake()) null else vm.blocks.assign.reason(),
                assignToMe = assign.mayTake(),
                // A validated asset goes to Managed assets from its own bar too (Configure, once the property is linked); without
                // them it shows greyed out, saying why.
                onMove = if (!d.managed && move.offered(listOf(d))) ({ move.tap(setOf(d.id), onValidatedFilter = true) }) else null,
                moveReason = if (!d.managed && move.offered(listOf(d))) move.blockedReason(onValidatedFilter = true) else null,
                onValidate = { quickIds = listOf(d.id) },
                onOpenResult = vm::openValidationResult,
                prefill = vm.newPrefill,
                scan = vm.newScanFacts,
                onDropCapture = vm::dropNewMedia,
                openTab = s.assetTab,
                onTabOpened = vm::assetTabOpened,
            )
        }

        val selecting = lists.selecting && vm.listInteractive
        val visibleItems = lists.lists[s.tab]?.items.orEmpty()
        // As the web's Validated tab: Move works only while the list is filtered to validated assets.
        val onValidatedFilter = lists.statusFilter[s.tab] == Status.VALIDATED

        // System back closes the topmost transient UI first.
        BackHandler(enabled = lists.searchOpen) { list.closeSearch() }
        BackHandler(enabled = selecting) { list.clearSelection() }
        BackHandler(enabled = vm.devSelOn) { vm.clearDevicePick() }

        val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Box(Modifier.fillMaxSize().background(C.Bg)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // A collector away from a property with geofencing on looks only: outside its fences the header says so
                // under the property's name; without the location (permission, or no fix) the banner below says why.
                val premiseNotice by premise.notice.collectAsStateWithLifecycle()
                DashboardHeader(
                    propertyName = vm.propName,
                    path = vm.specPathShown,
                    selected = s.loc,
                    onMenu = vm::openMenu,
                    onPick = vm::tapLocTab,
                    // The property tab's arrow opens the Property list page; the location tab's opens the location picker.
                    onChevron = { if (it == LocFilter.ALL) vm.openProp() else vm.openLocFilter() },
                    modifier = Modifier.launchEntrance(Entrance.DashHeader),
                    outsideGeofence = premiseNotice == PremiseNotice.OFF_PREMISE,
                )
                // The user's access couldn't be loaded: pages and changes that need a permission stay unavailable until it is.
                if (vm.accessFailed) AccessFailedBanner(vm::retryAccess, Modifier.readableWidth().padding(horizontal = 10.dp, vertical = 6.dp))
                PremiseBanner(premiseNotice, premise::askAgain, Modifier.readableWidth().padding(horizontal = 10.dp, vertical = 6.dp))
                Box(Modifier.weight(1f).fillMaxWidth().launchEntrance(Entrance.DashContent)) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // No swiping away from the camera while it holds shots: leaving asks first (the tabs do).
                        userScrollEnabled = !overlay && !vm.cameraHasShots,
                        beyondViewportPageCount = 1,
                        key = { it },
                    ) { page ->
                        when (tabs[page]) {
                            Tab.BT -> BluetoothPage(vm)
                            Tab.NET -> NetworkPage(vm)
                            Tab.ASSETS -> AssetsPage(vm)
                            Tab.CAMERA -> CameraPage(vm, shots, online)
                            Tab.FILE -> FilePage(vm, files, online)
                        }
                    }

                    // Asset details cover the list: a tap on a bare part of them stays on them.
                    if (detail != null && !validationShown) FullPage { detailView(detail) }

                    // The + and search buttons and the selection docks never show together: selecting hides the buttons.
                    // They belong to the asset lists: a scan list (Bluetooth, Wi-Fi, access points) doesn't show them.
                    if (vm.listInteractive && !vm.scanListShown && !selecting && !vm.devSelOn) {
                        Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                            DashboardFabs(
                                searchOpen = lists.searchOpen,
                                onSearchOpenChange = { if (it) list.openSearch() else list.closeSearch() },
                                query = lists.query,
                                onQueryChange = list::setQuery,
                                // Adding needs Collect: without it the + stays, greyed out, and a tap says why.
                                onAdd = vm::openNew,
                                addBlock = vm.collectBlock.reason(),
                                modifier = Modifier.align(Alignment.BottomEnd),
                                onFilter = { list.openFilter(s.tab) },
                                filterCount = lists.filter.count,
                            )
                        }
                    }
                    // Selection bar floats over the bottom of the list, just above the tabs.
                    androidx.compose.animation.AnimatedVisibility(
                        selecting,
                        Modifier.align(Alignment.BottomCenter).readableWidth(),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        val shown = lists.dockShown()
                        SelectionDock(
                            count = lists.picked.size,
                            visible = visibleItems.size,
                            allPicked = visibleItems.isNotEmpty() && visibleItems.all { it.id in lists.picked },
                            onClear = list::clearSelection,
                            onSelectAll = { list.selectAll(s.tab) },
                            // An action the user's group doesn't allow is left off the bar (lists.dockShown()); one held back
                            // by a passing state (access loading, off site) or the ticked assets stays, greyed out, and a tap
                            // says why (lists.dockAccess()).
                            onValidate = if (shown.validate) ({ quickIds = lists.picked.toList() }) else null,
                            // As the web's selection bar: no Assign, Submit or Delete on the Validated filter; Submit only for drafts.
                            // Assign (a reviewer picks who has them) is a reviewer's only; anyone else gets "Assign to me".
                            onAssign = when {
                                onValidatedFilter || !shown.assign -> null
                                assign.mayAssign() -> ({ assign.open(lists.picked, lists.pickedAssets.singleOrNull()?.assignedTo) })
                                else -> ({})
                            },
                            // The web's "Assign to me", beside Assign for anyone but a reviewer (whose Assign offers themselves),
                            // as on the asset's own bar: greyed out (a tap says why) when one is assigned or submitted.
                            onTake = if (onValidatedFilter || lists.access.reviewer || !shown.take) null else ({ assign.take(lists.pickedAssets, onDone = list::clearSelection) }),
                            takeAccess = lists.blocks.assign.reason()
                                ?: (if (assign.mayTake()) assign.takeReason(lists.pickedAssets) else UiText.Res(R.string.access_take_unavailable)),
                            onEdit = if (onValidatedFilter || !shown.edit) null else ({ bulk.open(lists.picked) }),
                            // As the web: Submit stays on the bar, greyed out when a ticked asset is already submitted.
                            onSubmit = if (!onValidatedFilter && shown.submit && lists.submitOffered(s.tab)) list::submitSelected else null,
                            submitDimmed = lists.submitBlocked,
                            onDelete = if (!onValidatedFilter && shown.delete) list::askDelete else null,
                            // Move is on the bar while a ticked asset is validated.
                            onMove = if (lists.anyValidated && shown.move) ({ move.tap(lists.picked, onValidatedFilter) }) else null,
                            moveDimmed = move.blockedReason(onValidatedFilter) != null,
                            offline = !online,
                            onOffline = explainOffline,
                            onChange = if (lists.changeOffered) list::openChangeMenu else null,
                            access = lists.dockAccess(),
                        )
                    }
                    // Scanned devices ticked on the Bluetooth / Network list: add them all as assets.
                    androidx.compose.animation.AnimatedVisibility(
                        vm.devSelOn,
                        Modifier.align(Alignment.BottomCenter).readableWidth(),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) { DeviceAddDock(vm) }
                }
                // The tabs stay visible everywhere, asset details included; while the keyboard is up (search, a form) they
                // stay behind it rather than riding up on top of it.
                if (!keyboardUp) DashboardNav(tabs, pager.currentPage + pager.currentPageOffsetFraction, vm::goTab, Modifier.launchEntrance(Entrance.DashNav))
            }

            // The side-menu pages cover the dashboard: each sits in a FullPage, so a tap on a bare part of it (its bar, a
            // gap) stays on it rather than reaching the location tabs or a list underneath.
            // Validation page (side menu) covers the whole app; an asset opened from its results sits on top.
            if (validationShown) {
                FullPage {
                    ValidationScreen(
                        vm.currentProp, vm.propName, vm.assets, vm::openAsset, vm::closeValidation, onMenu = vm::openMenu,
                        openJobId = s.valJobId, openAssetId = s.valAssetId, onJobOpened = vm::validationRunShown,
                    )
                    if (detail != null) {
                        Box(Modifier.fillMaxSize().coverPageBehind().background(C.Bg).statusBarsPadding().navigationBarsPadding()) { detailView(detail) }
                    }
                }
            }

            // Configuration page (side menu): building info, asset fields, geofencing.
            if (vm.configShown) FullPage { ConfigScreen(vm) }

            // Integration page (side menu): the property's link to a VDMS.
            if (vm.integrationShown) FullPage { IntegrationScreen(vm.currentProp, vm.propName, vm::closeIntegration, onMenu = vm::openMenu) }

            // Sync settings page (side menu, under the open property): what waits to upload and the sync switches.
            if (s.syncOpen && vm.hasProperty) FullPage { SyncSettingsScreen(vm.currentProp, vm.propName, vm::closeSync, onMenu = vm::openMenu) }

            // Property list / People and access (side menu).
            when (s.adminPage) {
                AdminPage.PROPERTIES -> FullPage { PropertyListScreen(vm) }
                // Only for someone who may manage people and access.
                AdminPage.PEOPLE -> if (vm.peopleShown) FullPage { PeopleScreen(vm) }
                null -> {}
            }

            // The asset list's filter (people and types), opened from the filter button.
            lists.filterSheet?.let {
                AssetFilterSheet(
                    it, list::closeFilter, list::toggleFilterPerson, list::toggleFilter,
                    onReset = list::resetFilter,
                )
            }

            // Bulk Edit sits under the location sheet it opens.
            bulk.sheet?.let {
                BulkEditSheet(it, bulk, onPickPlace = vm::editSelLocation, onUpdated = list::clearSelection, updateBlock = lists.blocks.collect.reason())
            }

            when (s.sheet) {
                Sheet.LOC -> LocationPicker(
                    vm.currentProp, vm.propName, s.specSel, vm::closeLoc,
                    onPick = { r ->
                        if (s.sheetFor == SheetFor.BULK) {
                            // Kept on the bulk Edit sheet until Update.
                            bulk.setPlace(vm.locValue(r), r.label)
                            vm.closeLoc()
                        } else vm.pickLoc(r)
                    },
                    tree = vm.treeFor(),
                )
                Sheet.NONE -> {}
            }
            summary.shown?.let { ManagedSummarySheet(it, summary::close, loading = summary.loading) }
            move.state.sheet?.let { sheet -> MoveToManagedSheet(move.state.networks, sheet, move, onSubmitted = {
                list.clearSelection()
                // Moved from its own page: back to the list.
                if (detail?.id in sheet.ids) vm.closeDetail()
            }, networksLoading = move.state.networksLoading, networksFailed = move.state.networksFailed) }
            assign.sheet?.let { sheet -> AssignSheet(sheet, assign, onSubmitted = list::clearSelection) }
            if (files.state.edit != null) DraftEditSheet(files)
            if (shots.state.edit != null) DraftEditSheet(shots)
            quickIds?.let { ids ->
                QuickValidate(
                    vm.currentProp, vm.assets, ids, vm::flash, onClose = { quickIds = null },
                    // A run opens Validation on it; one saved to run later opens the Validation page.
                    onOpenJob = { jobId -> if (jobId != null) vm.openValidationRun(jobId) else vm.openValidation() },
                    vm = validation,
                )
            }
            s.pv?.let { CapturePreview(it, vm::closePreview) }
            SideMenu(vm)

            // Why a scan needs its permission, just before Android asks.
            val ask = vm.scanPermissionAsk
            val askFor = vm.scanPermissionRequest
            if (ask != null && askFor != null && !systemPromptOpen) {
                PermissionSheet(
                    ask,
                    onContinue = {
                        systemPromptOpen = true
                        scanPermission.launch(askFor.toTypedArray())
                    },
                    onNotNow = { vm.onScanPermission(false) },
                )
            }
            // Why a collector's location is wanted (geofencing), just before Android asks.
            PremisePrompt(premise)
            if (lists.confirmDelete) {
                val n = lists.picked.size
                BackHandler(onBack = list::cancelDelete)
                ConfirmDialog(
                    icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                    title = deleteSelectionTitle(n),
                    body = deleteSelectionBody(n),
                    no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                    onNo = list::cancelDelete,
                    onYes = list::deleteSelected,
                )
            }
            SelectionChangeDialogs(lists, list)
            when (s.leaveAsk) {
                null -> Unit
                LeaveAsk.PHOTOS -> DiscardChangesDialog(
                    onKeep = vm::keepEditing, onDiscard = vm::discardAndLeave,
                    title = stringResource(R.string.camera_discard_title), body = stringResource(R.string.camera_discard_body),
                    keep = stringResource(R.string.camera_discard_keep),
                )
                LeaveAsk.CHANGES -> DiscardChangesDialog(onKeep = vm::keepEditing, onDiscard = vm::discardAndLeave)
            }
        }
    }
}

/** A page drawn over the dashboard, filling it; touches on it never reach the dashboard underneath. */
@Composable
private fun FullPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().coverPageBehind()) { content() }
}

@Composable
private fun AssetsPage(vm: AppViewModel) {
    val list = rememberAssetListBinding(vm, Tab.ASSETS)
    PullRefresh(list.refreshing, list.onRefresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { assetList(list) }
    }
}

/** The key of the Camera's own file import (Many assets), apart from the File tab's. */
private const val CAMERA_IMPORT = "camera-import"
