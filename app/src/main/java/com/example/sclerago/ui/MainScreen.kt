package com.example.sclerago.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sclerago.AppViewModel
import com.example.sclerago.LocFilter
import com.example.sclerago.Sheet
import com.example.sclerago.Tab
import com.example.sclerago.data.Asset
import com.example.sclerago.ui.icons.Ico
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C
import com.example.sclerago.ui.theme.Txt

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
fun ScleraApp(vm: AppViewModel = viewModel()) {
    val s = vm.state
    val pager = rememberPagerState(initialPage = s.tab.ordinal) { Tab.entries.size }

    // Tab state drives the pager; a finished swipe drives the tab state.
    LaunchedEffect(s.tab) {
        if (pager.currentPage != s.tab.ordinal) pager.animateScrollToPage(s.tab.ordinal, animationSpec = tween(380, easing = TrackEasing))
    }
    // Whenever scrolling stops (finished swipe, or a programmatic slide the user interrupted
    // with a touch), the page the pager rests on becomes the selected tab.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) vm.onPageSettled(pager.currentPage)
        }
    }

    // Shake the phone (two firm shakes) to jump straight to the camera.
    val view = LocalView.current
    ShakeEffect {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        vm.openCameraByShake()
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
    val overlay = detail != null || s.sheet != Sheet.NONE || s.pv != null || s.menuOpen || s.valOpen || s.cfgOpen
    val detailView: @Composable (Asset) -> Unit = { d ->
        AssetDetailScreen(
            asset = d,
            isNew = s.newAsset,
            prefill = vm.newPrefill,
            savedValues = s.savedDetails[d.id],
            onBack = vm::closeDetail,
            onChangeLoc = vm::openLocAsset,
            onCreate = vm::createAsset,
            onAttach = { vm.attachMedia(d.id, it) },
            onStatus = { vm.setAssetStatus(d.id, it) },
            onDelete = vm::deleteAsset,
            onSave = { vm.saveDraft(d.id, it) },
            onSubmit = { vm.submitAsset(d.id, it) },
            fields = s.fieldCfg,
            minPhotos = vm.minPhotos,
        )
    }

    // System back closes the topmost transient UI first.
    BackHandler(enabled = s.searchOpen) { vm.closeSearch() }
    BackHandler(enabled = vm.selOn) { vm.clearPick() }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Header(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !overlay,
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    when (Tab.entries[page]) {
                        Tab.BT -> BluetoothPage(vm)
                        Tab.NET -> NetworkPage(vm)
                        Tab.ASSETS -> AssetsPage(vm)
                        Tab.CAMERA -> CameraPage(vm)
                        Tab.FILE -> FilePage(vm)
                    }
                }


                if (detail != null && !s.valOpen) detailView(detail)

                if (vm.showFabs) Fabs(vm)
                // Selection bar floats over the bottom of the list, just above the tabs.
                androidx.compose.animation.AnimatedVisibility(
                    vm.selOn,
                    Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) { SelectionDock(vm) }
                // Messages sit above everything in the content area, asset details included.
                androidx.compose.animation.AnimatedVisibility(
                    s.toastOn,
                    Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut(),
                ) { Toast(s.toast) }
            }
            // The tabs stay visible everywhere, asset details included.
            BottomNav(vm)
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

        when (s.sheet) {
            Sheet.PROP -> PropertySheet(vm)
            Sheet.LOC -> LocationPicker(vm.state.prop, vm.propName, s.specSel, vm::closeLoc, vm::pickLoc, tree = vm.treeFor())
            Sheet.NONE -> {}
        }
        s.pv?.let { CapturePreview(it, vm::closePreview) }
        SideMenu(vm)
        if (s.confirmDeleteSel) {
            val n = vm.pickedIds.size
            BackHandler(onBack = vm::cancelDeleteSel)
            ConfirmDialog(
                icon = Icons.Trash, iconBg = Color(0xFF2A1919), iconFg = C.Red,
                title = if (n == 1) "Delete this asset?" else "Delete $n assets?",
                body = (if (n == 1) "The selected asset" else "The $n selected assets") + " will be removed. This can’t be undone.",
                no = "Cancel", yes = "Delete",
                onNo = vm::cancelDeleteSel,
                onYes = vm::deleteSel,
            )
        }
    }
}

@Composable
private fun AssetsPage(vm: AppViewModel) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        assetList(vm.listFor(Tab.ASSETS), vm.pickedIds, { vm.setStatusFilter(Tab.ASSETS, it.status) }, vm::togglePick, vm::openAsset)
    }
}

// ---------------------------------------------------------------- Header

@Composable
private fun Header(vm: AppViewModel) {
    Column(
        Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp)) {
            RoundButton(36.dp, Color.Transparent, vm::openMenu, Modifier.align(Alignment.CenterStart), shape = r(10)) {
                LineIcon(Icons.Menu, 22.dp, C.Text)
            }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.Bottom) {
                Txt("sclera", 21, 400, spacing = -0.4f)
                Txt("go", 21, 800, C.Yellow, Modifier.padding(start = 2.dp), italic = true, spacing = -0.4f)
            }
        }
        LocationTabs(vm)
    }
}

@Composable
private fun LocationTabs(vm: AppViewModel) {
    val s = vm.state
    val path = vm.specPathShown
    Row(Modifier.fillMaxWidth().height(44.dp).topBorder(C.Border).background(C.Strip)) {
        LocCell(
            on = s.loc == LocFilter.ALL, wide = false, icon = Icons.Property, divider = true,
            onPick = { vm.tapLocTab(LocFilter.ALL) }, onChevron = vm::openProp,
        ) { color, weight -> Txt(vm.propName, 14, weight, color) }
        LocCell(
            on = s.loc == LocFilter.SPEC, wide = true, icon = Icons.Pin, divider = true,
            onPick = { vm.tapLocTab(LocFilter.SPEC) }, onChevron = vm::openLocFilter,
        ) { color, weight ->
            Column {
                if (path.size > 1) Txt(path.dropLast(1).joinToString(" / "), 11, 500, C.Muted)
                Txt(path.last(), 14, weight, color)
            }
        }
        LocCell(
            on = s.loc == LocFilter.NONE, wide = false, icon = Icons.Unmapped, divider = false,
            onPick = { vm.tapLocTab(LocFilter.NONE) }, onChevron = null,
        ) { color, weight -> Txt("Unmapped", 14, weight, color) }
    }
}

@Composable
private fun RowScope.LocCell(
    on: Boolean,
    wide: Boolean,
    icon: Ico,
    divider: Boolean,
    onPick: () -> Unit,
    onChevron: (() -> Unit)?,
    label: @Composable (Color, Int) -> Unit,
) {
    val w by animateFloatAsState(if (on) (if (wide) 1.5f else 1.25f) else 1f, tween(350), label = "w")
    val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, tween(300), label = "bg")
    val fg by animateColorAsState(if (on) C.Yellow else C.Soft, tween(300), label = "fg")
    val ic by animateColorAsState(if (on) C.Yellow else C.Muted, tween(300), label = "ic")
    val bar by animateColorAsState(if (on) C.Yellow else Color.Transparent, tween(300), label = "bar")
    Row(
        Modifier.weight(w).fillMaxHeight().background(bg).then(if (divider) Modifier.endBorder(C.Border) else Modifier)
            .bottomBorder(bar, 3.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight().clickable(onClick = onPick)
                .padding(start = 6.dp, end = if (on && onChevron != null) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            LineIcon(icon, 17.dp, ic, 2.1f)
            Box(Modifier.weight(1f, fill = false)) { label(fg, if (on) 700 else 500) }
        }
        if (on && onChevron != null) {
            Box(Modifier.width(34.dp).fillMaxHeight().clickable(onClick = onChevron), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronDown, 20.dp, fg, 2.6f)
            }
        }
    }
}

// ---------------------------------------------------------------- Bottom nav

private data class NavSpec(val tab: Tab, val label: String, val icon: Ico, val iconSize: Dp = 21.dp)

private val NAV = listOf(
    NavSpec(Tab.BT, "Bluetooth", Icons.Bluetooth),
    NavSpec(Tab.NET, "Network", Icons.Wifi),
    NavSpec(Tab.ASSETS, "Assets", Icons.Box),
    NavSpec(Tab.CAMERA, "Camera", Icons.Camera),
    NavSpec(Tab.FILE, "File", Icons.FileUploadBadgeSolid, iconSize = 27.dp),
)

@Composable
private fun BottomNav(vm: AppViewModel) {
    Box(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).navigationBarsPadding().height(58.dp)) {
        Row(Modifier.fillMaxSize()) {
            NAV.forEachIndexed { i, n ->
                NavItem(n, vm.state.tab == n.tab, first = i == 0, last = i == NAV.size - 1) { vm.goTab(n.tab) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(n: NavSpec, on: Boolean, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val w by animateFloatAsState(if (on) 1.6f else 1f, tween(350), label = "navw")
    val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, tween(300), label = "navbg")
    val circ by animateColorAsState(if (on) C.Yellow else Color.Transparent, tween(300), label = "circ")
    val ic by animateColorAsState(if (on) C.OnYellow else C.Muted, tween(300), label = "navic")
    val size by animateDpAsState(if (on) 36.dp else 30.dp, tween(350), label = "size")
    val arrowL by animateDpAsState(if (on && !first) 17.dp else 0.dp, tween(300), label = "al")
    val arrowR by animateDpAsState(if (on && !last) 17.dp else 0.dp, tween(300), label = "ar")
    val labelH by animateDpAsState(if (on) 14.dp else 0.dp, tween(300), label = "lh")
    val labelA by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "la")
    val gap by animateDpAsState(if (on) 3.dp else 0.dp, tween(300), label = "gap")
    Box(
        Modifier.weight(w).fillMaxHeight().background(bg).then(if (!last) Modifier.endBorder(C.Border) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Arrows sit beside the icon; the label is centred under the icon only (not under icon + arrows).
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.height(size), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronLeft, 17.dp, C.Yellow, 2.8f, Modifier.width(arrowL).alpha(arrowL / 17.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(
                    Modifier.size(size).then(if (on) Modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                        .background(circ, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(n.icon, n.iconSize, ic) }
                Box(Modifier.height(labelH).alpha(labelA), contentAlignment = Alignment.Center) {
                    Txt(n.label, 11, 700, C.Yellow)
                }
            }
            Box(Modifier.height(size), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronRight, 17.dp, C.Yellow, 2.8f, Modifier.width(arrowR).alpha(arrowR / 17.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- FABs, search, selection

@Composable
private fun Fabs(vm: AppViewModel) {
    val s = vm.state
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 82.dp).size(54.dp).fabShadow()
                .clip(CircleShape).background(C.Yellow).clickable(onClick = vm::openNew),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 26.dp, C.OnYellow, 2.6f) }

        if (!s.searchOpen) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(54.dp).fabShadow()
                    .clip(CircleShape).background(C.Btn).clickable(onClick = vm::openSearch),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Search, 22.dp, C.Text, 2.2f) }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().height(54.dp)
                    .fabShadow(Capsule).clip(Capsule).background(C.Surface).border(1.5.dp, C.Yellow, Capsule)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Icons.Search, 20.dp, C.Yellow, 2.2f)
                PlainField(s.q, vm::setQ, "Search name, serial no. or tag", Modifier.weight(1f).focusRequester(focus))
                RoundButton(42.dp, C.Btn, vm::closeSearch) { LineIcon(Icons.Close, 16.dp, C.Text, 2.6f) }
            }
        }
    }
}

/** Docked above the tabs while assets are selected: clear, count + select all, then the actions. */
@Composable
private fun SelectionDock(vm: AppViewModel) {
    val n = vm.pickedIds.size
    ActionDock(
        onValidate = vm::validateSel, onAssign = vm::assignSel, onSubmit = vm::submitSel, onDelete = vm::askDeleteSel,
        floating = true,
    ) {
        RoundButton(40.dp, Color.Transparent, vm::clearPick) { LineIcon(Icons.Close, 18.dp, C.Text, 2.4f) }
        Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt("$n selected", 15, 700)
            Text(
                if (vm.allPicked) "Clear all" else "Select all ${vm.curCount}",
                Modifier.clickable(onClick = vm::selAll),
                color = C.Soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline, maxLines = 1,
            )
        }
    }
}
