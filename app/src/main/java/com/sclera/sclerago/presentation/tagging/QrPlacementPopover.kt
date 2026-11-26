package com.sclera.sclerago.presentation.tagging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// The design's measurements: a 212dp card (wider only for a longer translation) with 14dp corners and a 12 x 6dp caret pointing at the QR icon, its end edge
// 100dp past the icon's centre; "QR code placement" over two 44dp rows split by a full-width hairline.
private val PopoverWidth = 212.dp
/** Longer translations widen the card up to this. */
private val PopoverMaxWidth = 320.dp
private val Corner = 14.dp
private val CaretWidth = 12.dp
private val CaretHeight = 6.dp
/** From the anchor's bottom (the Tags bar's bottom edge) to the caret's tip. */
private val GapBelowAnchor = 6.dp
/** From the icon's centre to the popover's end edge. */
private val EndPastAnchor = 100.dp
/** The closest the popover comes to the window's edges. */
private val WindowMargin = 8.dp
private val RowHeight = 44.dp

/**
 * The QR icon's popover (the design's): "QR code placement", then Movable and Fixed. It hangs below the icon it is
 * placed in, its caret on the icon's centre. Tapping outside or Back closes it ([TagsActions.onDismissPlacement]).
 */
@Composable
internal fun QrPlacementPopover(actions: TagsActions) {
    val density = LocalDensity.current
    // Where the caret sits along the card: set when the popup is placed, read only when drawing.
    val caretX = remember { mutableFloatStateOf(Float.NaN) }
    val position = remember(density) { PlacementPosition(density, caretX) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = actions.onDismissPlacement,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(IntrinsicSize.Max).widthIn(min = PopoverWidth, max = PopoverMaxWidth).caretCard(caretX)
                .padding(top = CaretHeight).clip(RoundedCornerShape(Corner)),
        ) {
            Txt(
                stringResource(R.string.tag_placement_title), 14, 500, C.Muted,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 1.dp),
            )
            PlacementRow(Icons.Move, stringResource(R.string.tag_placement_movable), TagTags.PLACEMENT_MOVABLE) { actions.onPlacement(true) }
            Box(Modifier.fillMaxWidth().height(1.dp).drawBehind { drawRect(C.PopoverLine) })
            PlacementRow(Icons.Pin, stringResource(R.string.tag_placement_fixed), TagTags.PLACEMENT_FIXED) { actions.onPlacement(false) }
            Box(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlacementRow(icon: Ico, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clickable(role = Role.Button, onClick = onClick).testTag(tag)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LineIcon(icon, 18.dp, C.Yellow, 1.8f)
        Txt(label, 16, 600, C.Text)
    }
}

/** The card's fill and 1dp outline, as one shape with the caret on top at [caretX] (its centre, in px from the start). */
private fun Modifier.caretCard(caretX: FloatState): Modifier = drawBehind {
    val caretH = CaretHeight.toPx()
    val halfCaret = CaretWidth.toPx() / 2
    val corner = Corner.toPx()
    val x = caretX.floatValue.takeUnless { it.isNaN() } ?: (size.width / 2)
    val tipX = x.coerceIn(corner + halfCaret, size.width - corner - halfCaret)
    val path = Path().apply {
        addRoundRect(RoundRect(0f, caretH, size.width, size.height, CornerRadius(corner)))
    }
    val caret = Path().apply {
        moveTo(tipX - halfCaret, caretH + 1f)
        lineTo(tipX, 0f)
        lineTo(tipX + halfCaret, caretH + 1f)
        close()
    }
    val outline = Path.combine(PathOperation.Union, path, caret)
    drawPath(outline, C.PopoverBg)
    drawPath(outline, C.PopoverLine, style = Stroke(width = 1.dp.toPx()))
}

/**
 * Below the anchor (the QR icon), its end edge [EndPastAnchor] past the icon's centre (the design's), kept inside the
 * window; mirrored for right-to-left. Records where the caret falls in [caretX].
 */
private class PlacementPosition(private val density: Density, private val caretX: MutableFloatState) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        fun Dp.px() = with(density) { roundToPx() }
        val centre = anchorBounds.center.x
        val width = popupContentSize.width
        val wanted = if (layoutDirection == LayoutDirection.Ltr) centre + EndPastAnchor.px() - width else centre - EndPastAnchor.px()
        val margin = WindowMargin.px()
        val x = wanted.coerceIn(margin, (windowSize.width - width - margin).coerceAtLeast(margin))
        caretX.floatValue = (centre - x).toFloat()
        return IntOffset(x, anchorBounds.bottom + GapBelowAnchor.px())
    }
}
