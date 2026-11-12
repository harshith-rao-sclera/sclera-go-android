package com.sclera.sclerago.presentation.assets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.data.Asset
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.config.icon
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AssetListData
import com.sclera.sclerago.presentation.main.StatusChip
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.launch

/**
 * The shared asset list: status chips, rows, empty state. Emitted into a LazyColumn.
 * The chips stay pinned at the top while the rows scroll, under the page's own [header] when it has one
 * (one sticky header for both, so they don't push each other off).
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.assetList(
    data: AssetListData,
    picked: Set<Int>,
    onChip: (StatusChip) -> Unit,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    stickyHeader(key = "chips") {
        Column(Modifier.fillMaxWidth().background(C.Bg)) {
            header?.invoke()
            StatusChips(data.chips, onChip)
        }
    }
    items(data.items, key = { it.id }) { a ->
        AssetRow(a, a.id in picked, picked.isNotEmpty(), { onToggle(a.id) }, { onOpen(a.id) })
    }
    if (data.items.isEmpty()) item(key = "empty") { EmptyCard(data.emptyText) }
    item(key = "pad") { Spacer(Modifier.height(160.dp)) }
}

@Composable
private fun StatusChips(chips: List<StatusChip>, onChip: (StatusChip) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 180.dp.toPx() }
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 12.dp, end = 48.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chips, key = { it.label }) { c -> Chip(c) { onChip(c) } }
        }
        if (state.canScrollBackward) {
            EdgeFade(left = true, Modifier.align(Alignment.TopStart)) { scope.launch { state.animateScrollBy(-step) } }
        }
        if (state.canScrollForward) {
            EdgeFade(left = false, Modifier.align(Alignment.TopEnd)) { scope.launch { state.animateScrollBy(step) } }
        }
    }
}

@Composable
private fun EdgeFade(left: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val stops = listOf(C.Bg, C.Bg.copy(alpha = 0.8f), Color.Transparent)
    Box(
        modifier.width(52.dp).height(32.dp)
            .background(Brush.horizontalGradient(if (left) stops else stops.reversed()))
            .padding(horizontal = 6.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        RoundButton(28.dp, C.Btn, onClick) {
            LineIcon(if (left) Icons.ChevronLeft else Icons.ChevronRight, 16.dp, C.Text, 2.8f)
        }
    }
}

@Composable
private fun Chip(c: StatusChip, onClick: () -> Unit) {
    val bg = if (c.selected) C.YTintBg else C.Surface
    val bd = if (c.selected) C.Yellow else C.ChipBd
    val fg = if (c.selected) C.Yellow else C.Soft
    Row(
        Modifier.height(32.dp).clip(Capsule).background(bg).border(1.dp, bd, Capsule).clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        c.status?.let { LineIcon(it.icon, 14.dp, it.color, 2.4f) }
        Txt(c.label, 12, 600, fg)
        Txt(c.count.toString(), 11, 700, fg.copy(alpha = 0.8f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetRow(a: Asset, picked: Boolean, selMode: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    // Long press anywhere on the row selects it (and so starts multi-select).
    val haptics = LocalHapticFeedback.current
    val longPress: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }
    val rowBg by animateColorAsState(if (picked) C.PickedRow else C.Surface, label = "row")
    val icBg by animateColorAsState(if (picked) C.Yellow else C.Raised, label = "icbg")
    val icBd by animateColorAsState(if (picked) C.Yellow else if (selMode) C.RadioOff else C.Raised, label = "icbd")
    val cardBd by animateColorAsState(if (picked) C.YTintBd else C.Border, label = "cardbd")
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp).fillMaxWidth().height(IntrinsicSize.Min)
            .clip(r(8)).background(rowBg).border(1.dp, cardBd, r(8)),
    ) {
        Box(
            Modifier.width(52.dp).fillMaxHeight().combinedClickable(onClick = onToggle, onLongClick = longPress),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier.size(42.dp).clip(r(11)).background(icBg).border(2.dp, icBd, r(11)),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) LineIcon(Icons.Check, 22.dp, C.OnYellow, 3f)
                else LineIcon(a.type.icon, 21.dp, C.Yellow, 1.9f)
            }
        }
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp).combinedClickable(onClick = onOpen, onLongClick = longPress)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(a.name, 15, 600)
                Txt(a.detail, 12, 400, C.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                LineIcon(a.src.icon, 18.dp, a.src.color)
                if (a.loc != null) LineIcon(Icons.Pin, 18.dp, C.Yellow)
                else LineIcon(Icons.Unmapped, 18.dp, C.Orange)
                LineIcon(a.st.icon, 20.dp, a.st.color, 2.2f)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Column(
        Modifier.padding(horizontal = 12.dp).padding(top = 10.dp).fillMaxWidth()
            .dashedBorder(C.Border2, 1.dp, 14.dp).padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt("Nothing here yet", 15, 600)
        Txt(text, 13, 400, C.Muted, maxLines = 3, align = TextAlign.Center)
    }
}
