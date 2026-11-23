package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressScale
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.SkeletonList
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.StatusCount
import com.sclera.sclerago.domain.model.ValidationMark
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.icons.assetIco
import com.sclera.sclerago.presentation.model.color
import com.sclera.sclerago.presentation.model.icon
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.model.problemsRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.launch

/** Test tags of the asset list. */
object AssetListTags {
    const val EMPTY = "asset-list-empty"
    const val LOADING = "asset-list-loading"
    const val INFO = "asset-list-info"
    const val CHANGE_REQUESTS = "asset-chip-CHANGE_REQUESTS"
    const val MANAGED = "asset-chip-MANAGED"
    fun row(id: String) = "asset-row-$id"
    fun chip(status: Status?) = "asset-chip-${status?.name ?: "ALL"}"
    fun validation(mark: ValidationMark) = "asset-chip-validation-${mark.name}"

    /** A row's validation mark, when it opens the asset's Validation tab. */
    fun validationMark(id: String) = "asset-row-validation-$id"
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
                StatusChips(data, list)
            }
        }
    }
    items(data.items, key = { it.id }) { a ->
        AssetRow(
            a, a.id in list.picked, list.picked.isNotEmpty(), { list.onToggle(a.id) }, { list.onOpen(a.id) },
            Modifier.animateItem(fadeInSpec = ItemFade, placementSpec = ItemMove, fadeOutSpec = ItemFade).readableWidth().testTag(AssetListTags.row(a.id)),
            onOpenValidation = list.onOpenValidation?.let { open -> { open(a.id) } },
        )
    }
    when {
        // Just after a property is opened its data is still being opened: a loader, not "no assets" that fills suddenly.
        list.loading -> item(key = "loading") {
            SkeletonList(
                stringResource(R.string.assets_loading), Modifier.animateItem(fadeInSpec = ItemFade, placementSpec = null, fadeOutSpec = ItemFade).readableWidth().padding(horizontal = 8.dp).testTag(AssetListTags.LOADING),
                count = 6, trailing = true, gap = 3.dp,
            )
        }
        data.items.isEmpty() -> item(key = "empty") {
            EmptyCard(data.empty, list.query, Modifier.animateItem(fadeInSpec = ItemFade, placementSpec = null, fadeOutSpec = ItemFade).readableWidth())
        }
    }
    item(key = "pad") { Spacer(Modifier.height(160.dp)) }
}

@Composable
private fun StatusChips(data: AssetListData, list: AssetListBinding) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { ChipRow(data, list) }
        // The web's info button beside the list tools: the Managed Assets Summary.
        list.onInfo?.let {
            val label = stringResource(R.string.ms_button)
            RoundButton(30.dp, C.Btn, it, Modifier.padding(end = 10.dp).semantics { contentDescription = label }.testTag(AssetListTags.INFO)) {
                LineIcon(Icons.Info, 15.dp, C.Text, 2.2f)
            }
        }
    }
}

@Composable
private fun ChipRow(data: AssetListData, list: AssetListBinding) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 180.dp.toPx() }
    // While the property's assets are being read, every chip's count is a placeholder (not a 0 that jumps).
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        CompositionLocalProvider(LocalCountsLoading provides list.loading) { LazyRow(
            state = state,
            contentPadding = PaddingValues(start = 12.dp, end = 48.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // As the web's status tabs: All, Draft, Submitted, Exception, Validated, Change requests, AI suggestions.
            items(data.chips.filter { it.status != Status.AI }, key = { it.status?.name ?: "ALL" }) { c -> Chip(c, Modifier.animateItem(ItemFade, ItemMove, ItemFade)) { list.onChip(c) } }
            // Submitted assets someone asked an admin to reopen.
            data.changeRequests?.let { cr ->
                item(key = "CHANGE_REQUESTS") {
                    CountChip(stringResource(R.string.dash_chip_change_requests), cr.count, cr.selected, AssetListTags.CHANGE_REQUESTS, list.onChangeRequests, Modifier.animateItem(ItemFade, ItemMove, ItemFade)) {
                        LineIcon(Icons.ChangeRequest, 11.dp, C.Orange, 2.8f)
                    }
                }
            }
            items(data.chips.filter { it.status == Status.AI }, key = { it.status?.name ?: "ALL" }) { c -> Chip(c, Modifier.animateItem(ItemFade, ItemMove, ItemFade)) { list.onChip(c) } }
            // The assets moved to Managed Assets (the web's optional Managed assets tab), read-only.
            data.managed?.let { m ->
                item(key = "MANAGED") {
                    CountChip(stringResource(R.string.managed_chip), m.count, m.selected, AssetListTags.MANAGED, list.onManaged, Modifier.animateItem(ItemFade, ItemMove, ItemFade)) {
                        LineIcon(Icons.Managed, 12.dp, C.Yellow, 2.4f)
                    }
                }
            }
            // The web card's validation icon, as filters: a step failed, or one needs a look.
            data.validation.forEach { (mark, chip) ->
                item(key = "VALIDATION_${mark.name}") {
                    CountChip(stringResource(mark.labelRes), chip.count, chip.selected, AssetListTags.validation(mark), { list.onValidation(mark) }, Modifier.animateItem(ItemFade, ItemMove, ItemFade)) {
                        LineIcon(mark.icon, 12.dp, mark.color, 2.4f)
                    }
                }
            }
        } }
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
private fun Chip(c: StatusCount, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CountChip(stringResource(c.status?.labelRes ?: R.string.dash_chip_all), c.count, c.selected, AssetListTags.chip(c.status), onClick, modifier) {
        LineIcon(c.status?.icon ?: Icons.StatusAll, 11.dp, c.status?.color ?: C.Yellow, 2.8f)
    }
}

/**
 * One filter chip of the asset list (the status, change request, Managed Assets and validation chips): an optional icon,
 * its label and how many it holds; yellow while [selected].
 */
@Composable
private fun CountChip(
    label: String,
    count: Int,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val bg by animateColorAsState(if (selected) C.YTintBg else C.Surface, label = "chip-bg")
    val bd by animateColorAsState(if (selected) C.Yellow else C.ChipBd, label = "chip-bd")
    val fg by animateColorAsState(if (selected) C.Yellow else C.Soft, label = "chip-fg")
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.testTag(tag).pressScale(press).height(28.dp).clip(Capsule).background(bg).border(1.dp, bd, Capsule)
            .semantics { this.selected = selected }
            .clickable(press, LocalIndication.current, role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.invoke()
        Txt(label, 10, 600, fg)
        if (LocalCountsLoading.current) SkeletonBlock(Modifier.width(12.dp).height(8.dp), r(3))
        else RollingCount(count, fg.copy(alpha = 0.8f))
    }
}

/** A chip's count: a new number rolls in from below when it grows and from above when it shrinks. */
@Composable
private fun RollingCount(count: Int, color: Color) {
    AnimatedContent(
        count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(220)) { h -> if (up) h else -h } + fadeIn(tween(220)))
                .togetherWith(slideOutVertically(tween(220)) { h -> if (up) -h else h } + fadeOut(tween(160)))
                .using(SizeTransform(clip = true))
        },
        label = "chip-count",
    ) { Txt(it.toString(), 9, 700, color) }
}

/** How rows and chips fade as they come and go, and how they slide to a new place. */
private val ItemFade = tween<Float>(220)
private val ItemMove = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/** A row's icon swapping (ticked or not, a new status): the new one fades in as it grows. */
private val IconSwap: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)).togetherWith(fadeOut(tween(120)))
}

/** The chips' counts aren't known yet (the property's assets are still being read). */
private val LocalCountsLoading = staticCompositionLocalOf { false }

/**
 * One asset: its type icon, name, manufacturer and model, whether it is a copy (Cloned) and whether its validation failed
 * or needs a look, how it was onboarded, and its status. One in Managed Assets ([Asset.managed]) is read-only: it can't be
 * ticked (its icon and a long press open it, as a tap does), and it shows "In Managed Assets" in place of its status and
 * validation mark. [onOpenValidation]: pressing the validation mark opens the asset on its Validation tab (the web's
 * badge); null leaves the mark part of the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetRow(
    a: Asset,
    picked: Boolean,
    selMode: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenValidation: (() -> Unit)? = null,
) {
    // Long press anywhere on the row selects it (and so starts multi-select); one in Managed Assets is never selected.
    val haptics = LocalHapticFeedback.current
    val selectable = !a.managed
    val longPress: (() -> Unit)? = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }.takeIf { selectable }
    val managedLabel = stringResource(R.string.managed_status)
    val recollectLabel = stringResource(R.string.dash_recollect_badge)
    val changeRequestedLabel = stringResource(R.string.dash_change_requested_badge)
    val clonedLabel = stringResource(R.string.asset_cloned_badge)
    val openValidationLabel = stringResource(R.string.asset_open_validation)
    val rowBg by animateColorAsState(if (picked) C.PickedRow else C.Surface, label = "row")
    val icBg by animateColorAsState(if (picked) C.Yellow else C.Raised, label = "icbg")
    val icBd by animateColorAsState(if (picked) C.Yellow else if (selMode) C.RadioOff else C.Raised, label = "icbd")
    val cardBd by animateColorAsState(if (picked) C.YTintBd else C.Border, label = "cardbd")
    // The icon (select) and the rest (open) are pressed apart but show one press shade over the whole card.
    val press = remember { MutableInteractionSource() }
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 1.5.dp).pressScale(press, PRESSED_CARD).height(IntrinsicSize.Min)
            .clip(r(12)).background(rowBg).border(1.dp, cardBd, r(12)).indication(press, LocalIndication.current),
    ) {
        Box(
            Modifier.width(48.dp).fillMaxHeight()
                .combinedClickable(interactionSource = press, indication = null, onClick = if (selectable) onToggle else onOpen, onLongClick = longPress),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier.size(34.dp).clip(r(9)).background(icBg).border(1.5.dp, icBd, r(9)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(picked, transitionSpec = IconSwap, label = "row-tick") { on ->
                    if (on) LineIcon(Icons.Check, 17.dp, C.OnYellow, 3f)
                    else LineIcon(assetIco(a), 16.dp, C.Yellow, 1.9f)
                }
            }
        }
        Row(
            Modifier.weight(1f).heightIn(min = 54.dp)
                .combinedClickable(interactionSource = press, indication = null, onClick = onOpen, onLongClick = longPress)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(a.name, 13, 600)
                // Manufacturer and model, when either is filled in.
                if (a.makeModel.isNotEmpty()) Txt(a.makeModel, 11, 400, C.Text, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                // The web's "Recollect" badge: sent back to be collected again.
                if (a.recollect) LineIcon(Icons.Recollect, 14.dp, C.Orange, 2.1f, Modifier.semantics { contentDescription = recollectLabel })
                // The web card's "Change requested" badge: someone asked an admin to reopen it.
                if (a.changeRequest != null) {
                    LineIcon(Icons.ChangeRequest, 14.dp, C.Orange, 2.1f, Modifier.semantics { contentDescription = changeRequestedLabel })
                }
                // The web card's "Cloned" badge: a copy made with Clone.
                if (a.clonedFrom != null) LineIcon(Icons.Clone, 14.dp, C.Soft, 2.1f, Modifier.semantics { contentDescription = clonedLabel })
                // The web card's validation icon, when a step failed or needs a look (not for one in Managed Assets). Pressed,
                // it opens the asset on its Validation tab: 48dp wide, and as tall as the row's content (the row keeps its height).
                a.validationMark?.takeIf { !a.managed }?.let { mark ->
                    val described = pluralStringResource(mark.problemsRes, a.validationProblems, a.validationProblems)
                    if (onOpenValidation == null) {
                        ValidationMarkIcon(mark, a.validationProblems, Modifier.semantics { contentDescription = described })
                    } else {
                        Box(
                            Modifier.widthIn(min = 48.dp).heightIn(min = 38.dp)
                                .pressClickable(shape = r(10), onClickLabel = openValidationLabel, role = Role.Button, onClick = onOpenValidation)
                                .semantics { contentDescription = described }.testTag(AssetListTags.validationMark(a.id)),
                            contentAlignment = Alignment.Center,
                        ) { ValidationMarkIcon(mark, a.validationProblems) }
                    }
                }
                LineIcon(a.src.icon, 15.dp, a.src.color, 2f)
                Box(Modifier.width(17.dp), contentAlignment = Alignment.Center) {
                    // In Managed Assets: that is its status here (the web card's "In Managed Assets").
                    AnimatedContent(if (a.managed) null else a.st, transitionSpec = IconSwap, label = "row-status") { st ->
                        if (st == null) LineIcon(Icons.Managed, 15.dp, C.Yellow, 2f, Modifier.semantics { contentDescription = managedLabel })
                        else LineIcon(st.icon, 14.dp, st.color, 2f)
                    }
                }
            }
        }
    }
}

/** A row's validation mark: its icon in its colour, with how many steps need attention in a small badge at its corner. */
@Composable
private fun ValidationMarkIcon(mark: ValidationMark, problems: Int, modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 4.dp, end = 6.dp)) {
        LineIcon(mark.icon, 15.dp, mark.color, 2f)
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-5).dp).heightIn(min = 13.dp).widthIn(min = 13.dp)
                .background(mark.color, Capsule).padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) { Txt(problems.toString(), 9, 700, C.Bg) }
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
            EmptyReason.FILTER -> stringResource(R.string.dash_empty_filter)
            EmptyReason.ASSETS_TAB -> stringResource(R.string.dash_empty_assets)
            EmptyReason.SOURCE_TAB -> stringResource(R.string.dash_empty_source)
        }
        Txt(text, 12, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
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
    /** The Change requests chip: narrows the list to assets someone asked an admin to reopen (it stays on when tapped again). */
    val onChangeRequests: () -> Unit = {},
    /** The Failed validation / Needs a look chips: narrow the list to assets with that mark (it stays on when tapped again). */
    val onValidation: (ValidationMark) -> Unit = {},
    /** The Managed assets chip: lists only the assets in Managed Assets, read-only (it stays on when tapped again). */
    val onManaged: () -> Unit = {},
    /**
     * A row's validation mark (failed or needs a look), pressed: opens that asset on its Validation tab, as the web's
     * badge. Null leaves the mark part of the row (a tap opens the asset as usual).
     */
    val onOpenValidation: ((String) -> Unit)? = null,
)
