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
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
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
import com.sclera.sclerago.presentation.components.PropertySheet
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
import com.sclera.sclerago.presentation.validation.ValidationScreen

private val TrackEasing = CubicBezierEasing(0.22f, 0.8f, 0.24f, 1f)

/** What the File tab can import: spreadsheets, CSV, PDF, photos and videos. */
private val IMPORT_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "application/pdf",
    "image/*",
    "video/*",
)

@Composable
fun ScleraApp(vm: AppViewModel = hiltViewModel(), list: AssetListViewModel = hiltViewModel()) {
    val s = vm.state
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
    LaunchedEffect(s.prop, vm.locationFilter) { list.setScope(s.prop, vm.locationFilter) }
    LaunchedEffect(s.tab, s.prop, vm.showDetail, s.adminPage, s.valOpen, s.cfgOpen) { list.clearSelection() }
    LaunchedEffect(list) { list.notices.collect { vm.flash(it) } }

    // Shake the phone (two firm shakes) to jump straight to the camera.
    val view = LocalView.current
    ShakeEffect {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        vm.openCameraByShake()
    }

    // The scans need runtime permissions. When the scan service asks, a sheet explains why first
    // (PermissionSheet, below); Continue shows Android's own prompt, whose answer goes back to the service.
    var systemPromptOpen by remember { mutableStateOf(false) }
    val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        systemPromptOpen = false
        vm.onScanPermission(res.isNotEmpty() && res.values.all { it })
    }

    // The File tab hands off to Android's own file manager (Storage Access Framework).
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.onFilesPicked(it) }
    LaunchedEffect(s.pendingFilePicker) {
        if (s.pendingFilePicker) {
            vm.filePickerLaunched()
            filePicker.launch(IMPORT_MIME_TYPES)
        }
    }

    val detail = vm.detailAsset
    val overlay = detail != null || s.sheet != Sheet.NONE || s.pv != null || s.menuOpen || s.valOpen || s.cfgOpen || s.adminPage != null
    val detailView: @Composable (Asset) -> Unit = { d ->
        AssetDetailRoute(
            asset = d,
            isNew = s.newAsset,
            prefill = vm.newPrefill,
            onBack = vm::closeDetail,
            onChangeLoc = vm::openLocAsset,
            onCreate = { name, info, media, values, submit ->
                // A hand-made asset is shown in the full list, whatever status was being filtered.
                if (vm.createAsset(name, info, media, values, submit) == CreateAssetUseCase.Via.MANUAL) list.setStatusFilter(Tab.ASSETS, null)
            },
            onDelete = vm::deleteAsset,
            onNotice = vm::flash,
        )
    }

    val selecting = lists.selecting && vm.listInteractive
    val visibleItems = lists.lists[s.tab]?.items.orEmpty()

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
                        Tab.FILE -> FilePage(vm)
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
                        onValidate = list::validateSelected,
                        onAssign = vm::assignSel,
                        onSubmit = list::submitSelected,
                        onDelete = list::askDelete,
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
            ValidationScreen(vm)
            if (detail != null) {
                Box(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding()) { detailView(detail) }
            }
        }

        // Configuration page (side menu): building info, asset fields, geofencing.
        if (s.cfgOpen) ConfigScreen(vm)

        // Property list / People and access (side menu).
        when (s.adminPage) {
            AdminPage.PROPERTIES -> PropertyListScreen(vm)
            AdminPage.PEOPLE -> PeopleScreen(vm)
            null -> {}
        }

        when (s.sheet) {
            Sheet.PROP -> PropertySheet(vm)
            Sheet.LOC -> LocationPicker(
                vm.state.prop, vm.propName, s.specSel, vm::closeLoc,
                onPick = { r ->
                    if (s.sheetFor == SheetFor.BULK) {
                        list.assignSelected(vm.locValue(r), r.label)
                        vm.closeLoc()
                    } else vm.pickLoc(r)
                },
                tree = vm.treeFor(),
            )
            Sheet.NONE -> {}
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
    }
}

@Composable
private fun AssetsPage(vm: AppViewModel) {
    val list = rememberAssetListBinding(vm, Tab.ASSETS)
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { assetList(list) }
}
