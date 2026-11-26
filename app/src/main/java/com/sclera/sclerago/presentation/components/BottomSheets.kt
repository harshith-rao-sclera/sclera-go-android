package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlin.math.roundToInt

// Bottom sheet container and header shared across screens.

/**
 * A bottom sheet over a dimmed backdrop. Back, a tap on the backdrop and a swipe down close it. With [fullHeight] it
 * takes the whole screen under the status bar; with [closeOnOutside] false only Back and the sheet's own close button
 * close it (no swipe, no tap on the backdrop, and no handle).
 */
@Composable
internal fun BottomSheet(
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.92f,
    background: Color = C.SheetBg,
    fullHeight: Boolean = false,
    closeOnOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val shown = rememberAppear()
    val tablet = isTablet()
    Box(
        Modifier.fillMaxSize().scrim(shown).then(if (closeOnOutside) Modifier.noRippleClick(onDismiss) else Modifier.noRippleClick { })
            .blockSwipe().imePadding().then(if (fullHeight) Modifier.statusBarsPadding().padding(top = 8.dp) else Modifier),
    ) {
        Column(
            Modifier.sheetIn(shown, tablet).then(if (closeOnOutside) Modifier.swipeDownToClose(onDismiss) else Modifier)
                .then(sheetFrame(background, heightFraction = if (fullHeight) 1f else maxHeightFraction, fill = fullHeight)),
        ) {
            when {
                isTablet() || !closeOnOutside -> Spacer(Modifier.height(12.dp))
                else -> SheetHandle(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp))
            }
            content()
        }
    }
}

@Composable
internal fun SheetHeader(title: String, sub: String?, onClose: () -> Unit, titleSize: Int = 16) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // A long title (in a narrow sheet, or a long translation) wraps onto a second line rather than being cut off.
            Txt(title, 16, 700, maxLines = 2)
            sub?.let { Txt(it, 12, 400, C.Muted, maxLines = Int.MAX_VALUE) }
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}

/** How far down (part of the sheet's height) a released sheet must be to close. */
private const val CLOSE_FRACTION = 0.25f

/** A downward fling at least this fast closes the sheet wherever it is released. */
private val CloseVelocity = 1000.dp

/**
 * A sheet docked at the bottom (phones) follows a downward drag and closes ([onClose]) when let go far enough down or
 * flung down; otherwise it springs back. A scrolling list inside it scrolls first: once it is at its top, pulling
 * further drags the sheet. Put it first on the sheet so the whole sheet moves. Sheets centred on tablets don't move.
 * [onClose] may keep the sheet open (e.g. asking before discarding changes); it then springs back.
 */
@Composable
fun Modifier.swipeDownToClose(onClose: () -> Unit): Modifier {
    if (isTablet()) return this
    val close by rememberUpdatedState(onClose)
    val flingClose = with(LocalDensity.current) { CloseVelocity.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }

    fun moveBy(dy: Float): Float {
        val before = drag
        drag = (drag + dy).coerceAtLeast(0f)
        return drag - before
    }

    suspend fun settle(velocity: Float) {
        if (drag > 0f && (velocity > flingClose || drag > height * CLOSE_FRACTION)) {
            animate(drag, height.toFloat().coerceAtLeast(drag), velocity) { v, _ -> drag = v }
            close()
        }
        animate(drag, 0f) { v, _ -> drag = v }
    }

    val nested = remember {
        object : NestedScrollConnection {
            // Pulled down, the sheet goes back up before the list scrolls again.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f && drag > 0f) Offset(0f, moveBy(available.y)) else Offset.Zero

            // The list is at its top: the rest of a downward pull drags the sheet.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, moveBy(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    return this
        .offset { IntOffset(0, drag.roundToInt()) }
        .onSizeChanged { height = it.height }
        .nestedScroll(nested)
        .draggable(
            rememberDraggableState { dy -> moveBy(dy) },
            Orientation.Vertical,
            onDragStopped = { v -> settle(v) },
        )
}
