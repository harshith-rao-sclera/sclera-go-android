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
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.data.Device
import com.sclera.sclerago.presentation.assets.assetList
import com.sclera.sclerago.presentation.components.BlockedCard
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.SegHeader
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.DeviceRow
import com.sclera.sclerago.presentation.main.NetMode
import com.sclera.sclerago.presentation.main.Tab
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
    LazyColumn(Modifier.fillMaxSize()) {
        val seg: @Composable () -> Unit = {
            SegHeader(listOf("Assets", "Scan"), if (s.btScan) 1 else 0) { vm.setBtScan(it == 1) }
        }
        if (!s.btScan) {
            assetList(vm.listFor(Tab.BT), vm.pickedIds, { vm.setStatusFilter(Tab.BT, it.status) }, vm::togglePick, vm::openAsset, header = seg)
        } else {
            stickyHeader(key = "seg") { seg() }
            val devs = vm.btDevices
            val card = vm.btCard
            scanSection(
                on = vm.btOn,
                title = card.title,
                sub = card.sub,
                progress = card.progress,
                coreIcon = Icons.Bluetooth,
                dots = BT_DOTS,
                onToggle = vm::toggleBt,
                devices = devs,
                mono = false,
                onOpen = vm::openDevice,
                blocked = ScanService.Target.BLUETOOTH.takeIf { vm.scanBlocked(it) },
                onRetry = { vm.retryScan(ScanService.Target.BLUETOOTH) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetworkPage(vm: AppViewModel) {
    val s = vm.state
    LazyColumn(Modifier.fillMaxSize()) {
        val head: @Composable () -> Unit = {
            Column(Modifier.background(C.Bg)) {
                Row(Modifier.fillMaxWidth().background(C.Strip).bottomBorder(C.Border)) {
                    NetTab("Wi-Fi network", Icons.Wifi, s.netMode == NetMode.WIFI, Modifier.weight(1f)) { vm.setNetMode(NetMode.WIFI) }
                    NetTab("Access points", Icons.AccessPoint, s.netMode == NetMode.AP, Modifier.weight(1f)) { vm.setNetMode(NetMode.AP) }
                }
                SegHeader(listOf("Assets", "Scan"), if (s.netScan) 1 else 0, top = 8.dp) { vm.setNetScan(it == 1) }
            }
        }
        if (!s.netScan) {
            assetList(vm.listFor(Tab.NET), vm.pickedIds, { vm.setStatusFilter(Tab.NET, it.status) }, vm::togglePick, vm::openAsset, header = head)
        } else {
            stickyHeader(key = "head") { head() }
            val ap = s.netMode == NetMode.AP
            val devs = vm.netDevices
            val card = vm.netCard
            scanSection(
                on = vm.netOn,
                title = card.title,
                sub = card.sub,
                progress = card.progress,
                coreIcon = if (ap) Icons.AccessPoint else Icons.Wifi,
                dots = NET_DOTS,
                onToggle = vm::toggleNet,
                devices = devs,
                mono = true,
                onOpen = vm::openDevice,
                blocked = vm.netTarget.takeIf { vm.scanBlocked(it) },
                onRetry = { vm.retryScan(vm.netTarget) },
            )
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
            Txt(label, 14, if (on) 700 else 500, fg)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp).fillMaxWidth().height(3.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(bar)
        )
    }
}

private fun LazyListScope.scanSection(
    on: Boolean,
    title: String,
    sub: String,
    progress: Float?,
    coreIcon: Ico,
    dots: List<Dot>,
    onToggle: () -> Unit,
    devices: List<DeviceRow>,
    mono: Boolean,
    onOpen: (Device) -> Unit,
    blocked: ScanService.Target? = null,
    onRetry: () -> Unit = {},
) {
    item(key = "scan") {
        // A refused permission replaces the radar with how to allow it.
        if (blocked != null) {
            BlockedCard(blocked, onRetry)
            return@item
        }
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().clip(r(16)).background(C.Surface)
                .border(1.dp, C.Border, r(16)).clickable(onClick = onToggle).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Radar(on, dots, coreIcon)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(title, 16, 700)
                Txt(sub, 12, 400, C.Muted, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
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
                    Txt("$pct% done", 12, 700, C.Yellow)
                }
            }
        }
    }
    items(devices, key = { it.device.id }) { d ->
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().clip(r(14)).background(C.Surface)
                .border(1.dp, C.Border, r(14)).clickable { onOpen(d.device) }
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceIconTile(d.device)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(d.device.name, 14, 600)
                Txt(d.device.sub, 12, 400, C.Muted, mono = mono)
            }
            if (d.isNew) {
                Box(
                    Modifier.size(38.dp).clip(r(10)).background(C.YTintBg).border(1.dp, C.YTintBd, r(10))
                        .clickable { onOpen(d.device) },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.PlusSmall, 18.dp, C.Yellow, 2.6f) }
            } else {
                LineIcon(Icons.CheckCircle, 20.dp, C.Green, 2.2f, Modifier.padding(end = 6.dp))
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
