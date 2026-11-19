package com.sclera.sclerago.presentation.assets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.assetIco
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.ToggleChip
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.StatusCount
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.color
import com.sclera.sclerago.presentation.model.icon
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.launch

/** Test tags of the asset list. */
object AssetListTags {
    const val EMPTY = "asset-list-empty"
    const val LOADING = "asset-list-loading"
    const val INFO = "asset-list-info"
    const val RECOLLECT = "asset-chip-RECOLLECT"
    const val CHANGE_REQUESTS = "asset-chip-CHANGE_REQUESTS"
    fun row(id: String) = "asset-row-$id"
    fun chip(status: Status?) = "asset-chip-${status?.name ?: "ALL"}"
}

/**
 * The shared asset list: status chips, rows, empty state. Emitted into a LazyColumn whose items are
 * centred (`horizontalAlignment = CenterHorizontally`), at most [com.sclera.sclerago.presentation.components.ReadableWidth] wide.
 * The chips stay pinned at the top while the rows scroll, under the page own [header] when it has one
 * (one sticky header for both, so they do not push each other off).
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.assetList(list: AssetListBinding, header: (@Composable () -> Unit)? = null) {
    val data = list.data
    stickyHeader(key = "chips") {
        Column(Modifier.fillMaxWidth().background(C.Bg)) {
            Column(Modifier.align(Alignment.CenterHorizontally).readableWidth()) {
                header?.invoke()
                StatusChips(data.chips, list.onChip, data.recollect, list.onRecollect, data.changeRequests, list.onChangeRequests, list.onInfo)
            }
        }
    }
    items(data.items, key = { it.id }) { a ->
        AssetRow(
            a, a.id in list.picked, list.picked.isNotEmpty(), { list.onToggle(a.id) }, { list.onOpen(a.id) },
            Modifier.readableWidth().testTag(AssetListTags.row(a.id)),
        )
    }
    when {
        // Just after a property is opened its data is still being opened: a loader, not "no assets" that fills suddenly.
        list.loading -> item(key = "loading") {
            ListLoader(stringResource(R.string.assets_loading), Modifier.readableWidth().testTag(AssetListTags.LOADING))
        }
        data.items.isEmpty() -> item(key = "empty") { EmptyCard(data.empty, list.query, Modifier.readableWidth()) }
    }
    item(key = "pad") { Spacer(Modifier.height(160.dp)) }
}

@Composable
private fun StatusChips(
    chips: List<StatusCount>,
    onChip: (StatusCount) -> Unit,
    recollect: ToggleChip,
    onRecollect: () -> Unit,
    changeRequests: ToggleChip?,
    onChangeRequests: () -> Unit,
    onInfo: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { ChipRow(chips, onChip, recollect, onRecollect, changeRequests, onChangeRequests) }
        // The web's info button beside the list tools: the Managed Assets Summary.
        onInfo?.let {
            val label = stringResource(R.string.ms_button)
            RoundButton(30.dp, C.Btn, it, Modifier.padding(end = 10.dp).semantics { contentDescription = label }.testTag(AssetListTags.INFO)) {
                LineIcon(Icons.Info, 15.dp, C.Text, 2.2f)
            }
        }
    }
}

@Composable
private fun ChipRow(
    chips: List<StatusCount>,
    onChip: (StatusCount) -> Unit,
    recollect: ToggleChip,
    onRecollect: () -> Unit,
    changeRequests: ToggleChip?,
    onChangeRequests: () -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 180.dp.toPx() }
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 12.dp, end = 48.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chips, key = { it.status?.name ?: "ALL" }) { c -> Chip(c) { onChip(c) } }
            item(key = "RECOLLECT") {
                ChipBox(stringResource(R.string.dash_chip_recollect), recollect.count, recollect.selected, AssetListTags.RECOLLECT, onRecollect) {
                    LineIcon(Icons.Refresh, 11.dp, C.Orange, 2.8f)
                }
            }
            // The web's Change requests tab: submitted assets someone asked an admin to reopen.
            changeRequests?.let { cr ->
                item(key = "CHANGE_REQUESTS") {
                    ChipBox(stringResource(R.string.dash_chip_change_requests), cr.count, cr.selected, AssetListTags.CHANGE_REQUESTS, onChangeRequests) {
                        LineIcon(Icons.Alert, 11.dp, C.Orange, 2.8f)
                    }
                }
            }
        }
        if (state.canScrollBackward) {
            EdgeFade(left = true, stringResource(R.string.dash_chips_back), Modifier.align(Alignment.TopStart)) {
                scope.launch { state.animateScrollBy(-step) }
            }
        }
        if (state.canScrollForward) {
            EdgeFade(left = false, stringResource(R.string.dash_chips_more), Modifier.align(Alignment.TopEnd)) {
                scope.launch { state.animateScrollBy(step) }
            }
        }
    }
}

@Composable
private fun EdgeFade(left: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    val stops = listOf(C.Bg, C.Bg.copy(alpha = 0.8f), Color.Transparent)
    Box(
        modifier.width(52.dp).height(30.dp)
            .background(Brush.horizontalGradient(if (left) stops else stops.reversed()))
            .padding(horizontal = 6.dp),
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        RoundButton(28.dp, C.Btn, onClick, Modifier.semantics { contentDescription = description }) {
            LineIcon(if (left) Icons.ChevronLeft else Icons.ChevronRight, 12.dp, C.Text, 3.2f)
        }
    }
}

@Composable
private fun Chip(c: StatusCount, onClick: () -> Unit) {
    ChipBox(stringResource(c.status?.labelRes ?: R.string.dash_chip_all), c.count, c.selected, AssetListTags.chip(c.status), onClick) {
        c.status?.let { LineIcon(it.icon, 11.dp, it.color, 2.8f) }
    }
}

/** One filter chip: optional icon, label and count; yellow while [selected]. */
@Composable
private fun ChipBox(label: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val bg = if (selected) C.YTintBg else C.Surface
    val bd = if (selected) C.Yellow else C.ChipBd
    val fg = if (selected) C.Yellow else C.Soft
    Row(
        Modifier.testTag(tag).height(28.dp).clip(Capsule).background(bg).border(1.dp, bd, Capsule)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon()
        Txt(label, 10, 600, fg)
        Txt(count.toString(), 9, 700, fg.copy(alpha = 0.8f))
    }
}

/** One asset: its type icon, name, manufacturer and model, and how it was onboarded, whether it has a location, and its status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetRow(
    a: Asset,
    picked: Boolean,
    selMode: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Long press anywhere on the row selects it (and so starts multi-select).
    val haptics = LocalHapticFeedback.current
    val longPress: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }
    val recollectLabel = stringResource(R.string.dash_recollect_badge)
    val changeRequestedLabel = stringResource(R.string.dash_change_requested_badge)
    val rowBg by animateColorAsState(if (picked) C.PickedRow else C.Surface, label = "row")
    val icBg by animateColorAsState(if (picked) C.Yellow else C.Raised, label = "icbg")
    val icBd by animateColorAsState(if (picked) C.Yellow else if (selMode) C.RadioOff else C.Raised, label = "icbd")
    val cardBd by animateColorAsState(if (picked) C.YTintBd else C.Border, label = "cardbd")
    // The icon (select) and the rest (open) are pressed apart but show one press shade over the whole card.
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 1.5.dp).height(IntrinsicSize.Min)
            .clip(r(12)).background(rowBg).border(1.dp, cardBd, r(12)).indication(press, LocalIndication.current),
    ) {
        Box(
            Modifier.width(56.dp).fillMaxHeight()
                .combinedClickable(interactionSource = press, indication = null, onClick = onToggle, onLongClick = longPress),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier.size(42.dp).clip(r(11)).background(icBg).border(2.dp, icBd, r(11)),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) LineIcon(Icons.Check, 22.dp, C.OnYellow, 3f)
                else LineIcon(assetIco(a), 20.dp, C.Yellow, 1.9f)
            }
        }
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .combinedClickable(interactionSource = press, indication = null, onClick = onOpen, onLongClick = longPress)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(a.name, 14, 600)
                // Manufacturer and model, when either is filled in.
                if (a.makeModel.isNotEmpty()) Txt(a.makeModel, 12, 400, C.Text, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                // The web's "Recollect" badge: sent back to be collected again.
                if (a.recollect) LineIcon(Icons.Refresh, 17.dp, C.Orange, 2.1f, Modifier.semantics { contentDescription = recollectLabel })
                // The web card's "Change requested" badge: someone asked an admin to reopen it.
                if (a.changeRequest != null) {
                    LineIcon(Icons.Alert, 17.dp, C.Orange, 2.1f, Modifier.semantics { contentDescription = changeRequestedLabel })
                }
                LineIcon(a.src.icon, 18.dp, a.src.color, 2f)
                if (a.loc != null) LineIcon(Icons.Pin, 18.dp, C.Yellow, 2.1f)
                else LineIcon(Icons.Unmapped, 18.dp, C.Orange, 2.1f)
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { LineIcon(a.st.icon, 17.dp, a.st.color, 2f) }
            }
        }
    }
}

@Composable
private fun EmptyCard(reason: EmptyReason, query: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 12.dp).padding(top = 10.dp)
            .dashedBorder(C.Border2, 1.dp, 14.dp).padding(horizontal = 16.dp, vertical = 28.dp).testTag(AssetListTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt(stringResource(R.string.dash_empty_title), 14, 600)
        val text = when (reason) {
            EmptyReason.SEARCH -> stringResource(R.string.dash_empty_search, query.trim())
            EmptyReason.STATUS -> stringResource(R.string.dash_empty_status)
            EmptyReason.ASSETS_TAB -> stringResource(R.string.dash_empty_assets)
            EmptyReason.SOURCE_TAB -> stringResource(R.string.dash_empty_source)
        }
        Txt(text, 12, 400, C.Muted, maxLines = 3, align = TextAlign.Center)
    }
}

/** What the list needs from outside: the data, the selection and the actions on it. */
class AssetListBinding(
    val data: AssetListData,
    val picked: Set<String>,
    val query: String,
    val onChip: (StatusCount) -> Unit,
    val onToggle: (String) -> Unit,
    val onOpen: (String) -> Unit,
    /** The property's data is still being opened: a loader shows instead of the rows or the empty card. */
    val loading: Boolean = false,
    /** A pull to refresh is syncing the property's data. */
    val refreshing: Boolean = false,
    val onRefresh: () -> Unit = {},
    /** Opens the Managed Assets Summary; null hides the info button. */
    val onInfo: (() -> Unit)? = null,
    /** The Recollect chip: narrows the list to assets sent back to be collected again, or turns that off. */
    val onRecollect: () -> Unit = {},
    /** The Change requests chip: narrows the list to assets someone asked an admin to reopen, or turns that off. */
    val onChangeRequests: () -> Unit = {},
)
