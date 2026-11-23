package com.sclera.sclerago.presentation.scan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.presentation.assets.assetList
import com.sclera.sclerago.presentation.components.BlockedCard
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.SegHeader
import com.sclera.sclerago.presentation.components.SignalBars
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.NetMode
import com.sclera.sclerago.presentation.main.Tab
import com.sclera.sclerago.presentation.main.rememberAssetListBinding
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.scan.ScanService

private data class Dot(val x: Float, val y: Float, val r: Float, val c: Color)

private val BT_DOTS = listOf(Dot(142f, 56f, 6f, C.Yellow), Dot(46f, 76f, 6f, C.Green), Dot(136f, 140f, 6f, C.Yellow), Dot(34f, 146f, 5f, C.Dim))
private val NET_DOTS = listOf(Dot(54f, 44f, 6f, C.Green), Dot(150f, 84f, 6f, C.Yellow), Dot(120f, 150f, 6f, C.Yellow), Dot(40f, 128f, 5f, C.Dim))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BluetoothPage(vm: AppViewModel) {
    val s = vm.state
    val list = rememberAssetListBinding(vm, Tab.BT)
    // Only the assets refresh; the scan side finds devices on its own.
    PullRefresh(list.refreshing, list.onRefresh, Modifier.fillMaxSize(), enabled = !s.btScan) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val seg: @Composable () -> Unit = {
                SegHeader(listOf(stringResource(R.string.label_assets), stringResource(R.string.scrt_scan_tab_scan)), if (s.btScan) 1 else 0) { vm.setBtScan(it == 1) }
            }
            if (!s.btScan) {
                assetList(list, header = seg)
            } else {
                stickyHeader(key = "seg") { seg() }
                scanSection(
                    on = { vm.btOn },
                    card = { vm.btCard },
                    coreIcon = Icons.Bluetooth,
                    dots = BT_DOTS,
                    onToggle = vm::toggleBt,
                    devices = vm.btDevices,
                    mono = false,
                    onOpen = vm::openDevice,
                    // Adding needs Collect: without it there is no + and no ticking (a tap says the group can only view).
                    onAdd = if (vm.mayCollect()) vm::addDevice else null,
                    picks = devicePicks(vm),
                    blocked = ScanService.Target.BLUETOOTH.takeIf { vm.scanBlocked(it) },
                    onRetry = { vm.retryScan(ScanService.Target.BLUETOOTH) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkPage(vm: AppViewModel) {
    val s = vm.state
    val list = rememberAssetListBinding(vm, Tab.NET)
    PullRefresh(list.refreshing, list.onRefresh, Modifier.fillMaxSize(), enabled = !s.netScan) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val head: @Composable () -> Unit = {
                Column(Modifier.background(C.Bg)) {
                    Row(Modifier.fillMaxWidth().background(C.Strip).bottomBorder(C.Border)) {
                        NetTab(stringResource(R.string.scrt_scan_wifi_network), Icons.Wifi, s.netMode == NetMode.WIFI, Modifier.weight(1f)) { vm.setNetMode(NetMode.WIFI) }
                        NetTab(stringResource(R.string.scrt_scan_access_points), Icons.AccessPoint, s.netMode == NetMode.AP, Modifier.weight(1f)) { vm.setNetMode(NetMode.AP) }
                    }
                    SegHeader(listOf(stringResource(R.string.label_assets), stringResource(R.string.scrt_scan_tab_scan)), if (s.netScan) 1 else 0, top = 8.dp) { vm.setNetScan(it == 1) }
                }
            }
            if (!s.netScan) {
                assetList(list, header = head)
            } else {
                stickyHeader(key = "head") { head() }
                val ap = s.netMode == NetMode.AP
                scanSection(
                    on = { vm.netOn },
                    card = { vm.netCard },
                    coreIcon = if (ap) Icons.AccessPoint else Icons.Wifi,
                    dots = NET_DOTS,
                    onToggle = vm::toggleNet,
                    devices = vm.netDevices,
                    mono = true,
                    onOpen = vm::openDevice,
                    onAdd = if (vm.mayCollect()) vm::addDevice else null,
                    picks = devicePicks(vm),
                    blocked = vm.netTarget.takeIf { vm.scanBlocked(it) },
                    onRetry = { vm.retryScan(vm.netTarget) },
                )
            }
        }
    }
}

@Composable
private fun NetTab(label: String, icon: Ico, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg by animateColorAsState(if (on) C.Yellow else C.Muted, label = "nettab")
    val bar by animateColorAsState(if (on) C.Yellow else Color.Transparent, label = "netbar")
    Box(modifier.height(44.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LineIcon(icon, 18.dp, fg, 2.1f)
            Txt(label, 13, if (on) 700 else 500, fg)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp).fillMaxWidth().height(3.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(bar)
        )
    }
}

/**
 * The radar card and the devices found. [on] and [card] are read inside the card's own item: the card's
 * progress changes several times a second, and reading it there redraws only the card, not every row.
 */
/** Ticking scanned devices to add several at once (see [AppViewModel.addPickedDevices]). */
private class DevicePicks(
    val picked: Set<String>,
    /** Devices added as assets in this scan: they show a tick and can't be ticked or added again from the list. */
    val added: Set<String>,
    val selecting: Boolean,
    val addable: Int,
    val allPicked: Boolean,
    /** Select all is on: new finds are ticked as they appear. */
    val auto: Boolean,
    val toggle: (String) -> Unit,
    val selectAll: () -> Unit,
)

private fun devicePicks(vm: AppViewModel) = DevicePicks(
    picked = vm.pickedDevices.mapTo(HashSet()) { it.id },
    added = vm.addedDevices,
    selecting = vm.devSelOn,
    addable = vm.addableCount,
    allPicked = vm.allDevicesPicked,
    auto = vm.devAllOn,
    toggle = vm::toggleDevicePick,
    selectAll = vm::selectAllDevices,
)

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.scanSection(
    on: () -> Boolean,
    card: () -> ScanService.Card,
    coreIcon: Ico,
    dots: List<Dot>,
    onToggle: () -> Unit,
    devices: List<Device>,
    mono: Boolean,
    onOpen: (Device) -> Unit,
    /** Adds a device straight away (its + button); null: the user may not add assets, so no +, ticking or Select all. */
    onAdd: ((Device) -> Unit)?,
    picks: DevicePicks,
    blocked: ScanService.Target? = null,
    onRetry: () -> Unit = {},
) {
    item(key = "scan") {
        // A refused permission replaces the radar with how to allow it.
        if (blocked != null) {
            BlockedCard(blocked, onRetry)
            return@item
        }
        val on = on()
        val (title, sub, progress) = card()
        Column(
            Modifier.readableWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp).clip(r(16)).background(C.Surface)
                .border(1.dp, C.Border, r(16)).clickable(onClick = onToggle).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Radar(on, dots, coreIcon)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(title, 15, 700)
                Txt(sub, 11, 400, C.Muted, maxLines = Int.MAX_VALUE, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
            // How far the scan has got, with the percentage done beside it.
            if (on && progress != null) {
                val pct = (progress * 100).toInt()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f).height(6.dp).clip(Capsule).background(C.Raised)) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(Capsule).background(C.Yellow))
                    }
                    Txt(stringResource(R.string.scrt_scan_percent_done, pct), 11, 700, C.Yellow)
                }
            }
        }
    }
    // Without Collect nothing can be added from the list: say so where Select all would be.
    if (onAdd == null && devices.isNotEmpty()) item(key = "viewonly") {
        Txt(
            stringResource(R.string.view_only_no_add), 12, 500, C.Orange,
            Modifier.readableWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), maxLines = Int.MAX_VALUE,
        )
    }
    // How many can still be added, and Select all, while any device is left to add.
    if (onAdd != null && picks.addable > 0) item(key = "selectall") {
        Row(
            Modifier.readableWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(
                    if (picks.selecting) stringResource(R.string.scan_devices_selected, picks.picked.size, picks.addable)
                    else pluralStringResource(R.plurals.scan_devices_to_add, picks.addable, picks.addable),
                    12, 600, C.Muted,
                )
                if (picks.auto) Txt(stringResource(R.string.scrt_scan_new_finds_selected), 11, 500, C.Yellow, maxLines = Int.MAX_VALUE)
            }
            Row(
                Modifier.clip(Capsule).clickable(onClick = picks.selectAll).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(stringResource(if (picks.allPicked) R.string.dash_clear_all else R.string.pi_select_all), 12, 700, C.Yellow)
                CheckBox(picks.allPicked)
            }
        }
    }
    items(devices, key = { it.id }, contentType = { "device" }) { d ->
        val picked = d.id in picks.picked
        val added = d.id in picks.added
        // While ticking, tapping a device not added yet ticks it; long-press starts ticking. Otherwise a tap opens the
        // asset it was made into in this scan, or for one not added yet the add-asset form filled in with what the scan
        // found; its + button adds it straight away as a draft, and a tick replaces the + once it has been added.
        val tap = {
            if (picks.selecting && !added) picks.toggle(d.id) else onOpen(d)
        }
        val longPress = { if (!added && onAdd != null) picks.toggle(d.id) }
        // The same card spacing as the asset rows (AssetRow): 8dp at the sides, 3dp between cards.
        Row(
            Modifier.readableWidth().padding(horizontal = 8.dp, vertical = 1.5.dp).heightIn(min = 54.dp).clip(r(12))
                .background(if (picked) C.PickedRow else C.Surface)
                .border(1.dp, if (picked) C.YTintBd else C.Border, r(12))
                .combinedClickable(onClick = tap, onLongClick = longPress)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceIconTile()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                // The name, then how strong its signal is (how quickly it answered, on the network).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(d.name, 12, 600, C.Text, Modifier.weight(1f, fill = false), maxLines = 1)
                    SignalBars(d.lvl)
                }
                Txt(d.sub, 11, 400, C.Muted, mono = mono)
            }
            when {
                added -> LineIcon(Icons.CheckCircle, 20.dp, C.Green, 2.2f, Modifier.padding(end = 6.dp))
                picks.selecting -> Box(
                    Modifier.size(38.dp).clip(r(10)).clickable { picks.toggle(d.id) },
                    contentAlignment = Alignment.Center,
                ) { CheckBox(picked) }
                onAdd != null -> Box(
                    Modifier.size(38.dp).clip(r(10)).background(C.YTintBg).border(1.dp, C.YTintBd, r(10))
                        .clickable { onAdd(d) },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.PlusSmall, 18.dp, C.Yellow, 2.6f) }
            }
        }
    }
    item(key = "scanpad") { Spacer(Modifier.height(170.dp)) }
}

@Composable
private fun Radar(on: Boolean, dots: List<Dot>, coreIcon: Ico) {
    val inf = rememberInfiniteTransition(label = "radar")
    val angle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val core by animateColorAsState(if (on) C.Yellow else C.Dim, label = "core")
    Box(Modifier.size(188.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 188f
            val c = Offset(94f * u, 94f * u)
            val hair = 1.dp.toPx()
            drawCircle(C.Border, 90f * u, c, style = Stroke(hair))
            drawCircle(C.Border, 62f * u, c, style = Stroke(hair))
            drawCircle(C.Border2, 34f * u, c, style = Stroke(hair))
            dots.forEach { drawCircle(it.c, it.r * u, Offset(it.x * u, it.y * u)) }
            if (on) {
                rotate(angle, c) {
                    drawArc(
                        C.Yellow.copy(alpha = 0.22f), -90f, 60f, useCenter = true,
                        topLeft = Offset(4f * u, 4f * u), size = Size(180f * u, 180f * u),
                    )
                }
                drawCircle(C.Yellow.copy(alpha = 0.7f * (1f - pulse)), (24f + 66f * pulse) * u, c, style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(48.dp).background(core, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) LineIcon(coreIcon, 22.dp, C.OnYellow, 2.4f) else LineIcon(Icons.Play, 20.dp, C.OnYellow)
        }
    }
}
