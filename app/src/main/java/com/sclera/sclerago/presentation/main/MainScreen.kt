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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.pager.rememberPagerState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.access.PeopleScreen
import com.sclera.sclerago.presentation.assets.AssetDetailRoute
import com.sclera.sclerago.presentation.assets.CameraPage
import com.sclera.sclerago.presentation.assets.FilePage
import com.sclera.sclerago.presentation.assets.assetList
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CapturePreview
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.LocationPicker
import com.sclera.sclerago.presentation.components.PermissionSheet
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.ShakeEffect
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.config.ConfigScreen
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.launch.Entrance
import com.sclera.sclerago.presentation.launch.launchEntrance
import com.sclera.sclerago.presentation.property.PropertyListScreen
import com.sclera.sclerago.presentation.scan.BluetoothPage
import com.sclera.sclerago.presentation.scan.NetworkPage
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.validation.QuickValidate
import com.sclera.sclerago.presentation.validation.ValidationScreen
import com.sclera.sclerago.presentation.validation.ValidationViewModel
import com.sclera.sclerago.presentation.integration.IntegrationScreen
import com.sclera.sclerago.presentation.integration.ManagedSummarySheet
import com.sclera.sclerago.presentation.integration.ManagedSummaryViewModel
import com.sclera.sclerago.presentation.integration.MoveToManagedSheet
import com.sclera.sclerago.presentation.integration.MoveToManagedViewModel
import com.sclera.sclerago.presentation.assign.AssignSheet
import com.sclera.sclerago.presentation.assign.AssignViewModel
import com.sclera.sclerago.presentation.fileimport.DraftEditSheet
import com.sclera.sclerago.presentation.fileimport.FileImportViewModel

private val TrackEasing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/**
 * What the File tab can upload, the files the server reads (its `readers.kind_of`): spreadsheets (xlsx, xlsm, xls,
 * csv, tsv), PDFs, photos, videos, Word (docx) and text (txt, md, json, xml). Each pick is checked again by name.
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
    bulk: BulkEditViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    val s = vm.state
    // Offline, the server-only actions are dimmed with a badge; a tap says why.
    val online by connectivity.online.collectAsStateWithLifecycle()
    val explainOffline = { why: OnlineOnly -> vm.flash(UiText.Res(why.message)) }
    // Validate (selection bar or an asset): the assets the Run validation sheet opens on, as the web's QuickValidate.
    var quickIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
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
    // with a touch), the page the pager rests on becomes the selected tab.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) vm.onPageSettled(pager.currentPage)
        }
    }

    // The lists show the current property, narrowed by the location tab; a selection belongs to the page it was made on.
    LaunchedEffect(vm.locationFilter) { list.setScope(vm.locationFilter) }
    LaunchedEffect(s.tab, vm.currentProp, vm.showDetail, s.adminPage, s.valOpen, s.cfgOpen, s.intOpen) { list.clearSelection() }
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
    val overlay = detail != null || s.sheet != Sheet.NONE || s.pv != null || s.menuOpen || s.valOpen || s.cfgOpen || s.intOpen || s.adminPage != null || move.state.sheet != null || summary.shown != null || assign.sheet != null || bulk.sheet != null || files.state.edit != null
    val detailView: @Composable (Asset) -> Unit = { d ->
        AssetDetailRoute(
            asset = d,
            isNew = s.newAsset,
            onBack = vm::closeDetail,
            onChangeLoc = vm::openLocAsset,
            onCreate = { name, info, media, values, submit, exception, place ->
                // A hand-made asset is shown in the full list, whatever status was being filtered.
                if (vm.createAsset(name, info, media, values, submit, exception, place) == CreateAssetUseCase.Via.MANUAL) list.setStatusFilter(Tab.ASSETS, null)
            },
            onDelete = vm::deleteAsset,
            onNotice = vm::flash,
            pickedPlace = s.detailPlace,
            onPlaceTaken = vm::detailPlaceTaken,
            onAssign = if (assign.mayAssign()) ({ assign.open(setOf(d.id)) }) else null,
            onValidate = { quickIds = listOf(d.id) },
            onOpenRun = vm::openValidationRun,
            prefill = vm.newPrefill,
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

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            DashboardHeader(
                propertyName = vm.propName,
                path = vm.specPathShown,
                selected = s.loc,
                onMenu = vm::openMenu,
                onPick = vm::tapLocTab,
                onChevron = { if (it == LocFilter.ALL) vm.openProp() else vm.openLocFilter() },
                modifier = Modifier.launchEntrance(Entrance.DashHeader),
            )
            Box(Modifier.weight(1f).fillMaxWidth().launchEntrance(Entrance.DashContent)) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !overlay,
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    when (tabs[page]) {
                        Tab.BT -> BluetoothPage(vm)
                        Tab.NET -> NetworkPage(vm)
                        Tab.ASSETS -> AssetsPage(vm)
                        Tab.CAMERA -> CameraPage(vm)
                        Tab.FILE -> FilePage(vm, files, online)
                    }
                }

                if (detail != null && !s.valOpen) detailView(detail)

                // The + and search buttons and the selection docks never show together: selecting hides the buttons.
                if (vm.listInteractive && !selecting && !vm.devSelOn) {
                    Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                        DashboardFabs(
                            searchOpen = lists.searchOpen,
                            onSearchOpenChange = { if (it) list.openSearch() else list.closeSearch() },
                            query = lists.query,
                            onQueryChange = list::setQuery,
                            onAdd = vm::openNew,
                            modifier = Modifier.align(Alignment.BottomEnd),
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
                    SelectionDock(
                        count = lists.picked.size,
                        visible = visibleItems.size,
                        allPicked = visibleItems.isNotEmpty() && visibleItems.all { it.id in lists.picked },
                        onClear = list::clearSelection,
                        onSelectAll = { list.selectAll(s.tab) },
                        onValidate = if (validation.mayCheck()) ({ quickIds = lists.picked.toList() }) else null,
                        onAssign = if (assign.mayAssign()) ({ assign.open(lists.picked) }) else null,
                        onEdit = if (onValidatedFilter || !list.mayCollect()) null else ({ bulk.open(lists.picked) }),
                        onSubmit = if (list.mayCollect()) list::submitSelected else null,
                        onDelete = if (list.mayCollect()) list::askDelete else null,
                        onMove = if (move.offered(lists.pickedAssets)) ({ move.tap(lists.picked, onValidatedFilter) }) else null,
                        moveDimmed = move.blockedReason(onValidatedFilter) != null,
                        offline = !online,
                        onOffline = explainOffline,
                        onChange = if (lists.changeOptions.isNotEmpty()) list::openChangeMenu else null,
                    )
                }
                // Scanned devices ticked on the Bluetooth / Network list: add them all as assets.
                androidx.compose.animation.AnimatedVisibility(
                    vm.devSelOn,
                    Modifier.align(Alignment.BottomCenter).readableWidth(),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) { DeviceAddDock(vm) }
                // Messages sit above everything in the content area, asset details included.
                androidx.compose.animation.AnimatedVisibility(
                    s.toastOn,
                    Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut(),
                ) { Toast(s.toast.asString()) }
            }
            // The tabs stay visible everywhere, asset details included.
            DashboardNav(tabs, pager.currentPage + pager.currentPageOffsetFraction, vm::goTab, Modifier.launchEntrance(Entrance.DashNav))
        }

        // Validation page (side menu) covers the whole app; an asset opened from its results sits on top.
        if (s.valOpen) {
            ValidationScreen(
                vm.currentProp, vm.propName, vm.assets, vm::openAsset, vm::closeValidation, onMenu = vm::openMenu,
                openJobId = s.valJobId, onJobOpened = vm::validationRunShown,
            )
            if (detail != null) {
                Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) { detailView(detail) }
            }
        }

        // Configuration page (side menu): building info, asset fields, geofencing.
        if (s.cfgOpen) ConfigScreen(vm)

        // Integration page (side menu): the property's link to a VDMS.
        if (s.intOpen) IntegrationScreen(vm.currentProp, vm.propName, vm::closeIntegration, onMenu = vm::openMenu)

        // Property list / People and access (side menu).
        when (s.adminPage) {
            AdminPage.PROPERTIES -> PropertyListScreen(vm)
            AdminPage.PEOPLE -> PeopleScreen(vm)
            null -> {}
        }

        // Bulk Edit sits under the location sheet it opens.
        bulk.sheet?.let { BulkEditSheet(it, bulk, onPickPlace = vm::editSelLocation, onUpdated = list::clearSelection) }

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
        summary.shown?.let { ManagedSummarySheet(it, summary::close) }
        move.state.sheet?.let { sheet -> MoveToManagedSheet(move.state.networks, sheet, move, onSubmitted = list::clearSelection) }
        assign.sheet?.let { sheet -> AssignSheet(sheet, assign, onSubmitted = list::clearSelection) }
        if (files.state.edit != null) DraftEditSheet(files)
        quickIds?.let { ids -> QuickValidate(vm.currentProp, vm.assets, ids, vm::flash, onClose = { quickIds = null }, vm = validation) }
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
    }
}

@Composable
private fun AssetsPage(vm: AppViewModel) {
    val list = rememberAssetListBinding(vm, Tab.ASSETS)
    PullRefresh(list.refreshing, list.onRefresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { assetList(list) }
    }
}
