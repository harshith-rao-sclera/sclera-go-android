package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C

// Tablet layout basics: phones are unchanged; on tablets reading content keeps a comfortable
// width and sheets open in the middle of the screen at phone width.

/** Shortest screen side from which the app uses its tablet layout (portrait and landscape). */
val TabletMinSide = 600.dp

/** Widest a sheet gets; on tablets it opens centred on the screen at this width. */
val SheetMaxWidth = 640.dp

/** Widest single-column page content (forms, settings, validation) gets. */
val ReadableWidth = 760.dp

/** True on tablets (shortest side at least [TabletMinSide]), in either orientation. */
@Composable
fun isTablet(): Boolean {
    val c = LocalConfiguration.current
    return minOf(c.screenWidthDp, c.screenHeightDp) >= TabletMinSide.value
}

/** Side margin that centres content of at most [maxContent] on wide screens (0 on phones). */
@Composable
fun pageGutter(maxContent: Dp = ReadableWidth): Dp {
    val w = LocalConfiguration.current.screenWidthDp.dp
    return ((w - maxContent) / 2).coerceAtLeast(0.dp)
}

/**
 * How a sheet sits in its full-screen scrim box: docked to the bottom with its top corners rounded on
 * phones, or in the middle of the screen at [SheetMaxWidth] with every corner rounded on tablets.
 * [heightFraction] caps its height (it wraps its content below that); [fill] makes it that tall.
 */
@Composable
fun BoxScope.sheetFrame(background: Color, radius: Dp = 22.dp, heightFraction: Float? = null, fill: Boolean = false): Modifier {
    val tablet = isTablet()
    val shape = if (tablet) RoundedCornerShape(radius) else RoundedCornerShape(topStart = radius, topEnd = radius)
    val height = when {
        heightFraction == null -> Modifier
        fill -> Modifier.fillMaxHeight(heightFraction)
        else -> Modifier.fillMaxHeight(heightFraction).wrapContentHeight(if (tablet) Alignment.CenterVertically else Alignment.Bottom)
    }
    val edge = if (tablet) Modifier.border(1.dp, C.Border, shape) else Modifier.topBorder(C.Border)
    return Modifier.align(if (tablet) Alignment.Center else Alignment.BottomCenter).widthIn(max = SheetMaxWidth).fillMaxWidth()
        .then(height).clip(shape).background(background).then(edge).noRippleClick { }
        .then(if (tablet) Modifier else Modifier.navigationBarsPadding())
}

/** Content of a page: as wide as the screen up to [ReadableWidth], centred inside a column that centres its children. */
fun Modifier.readableWidth(): Modifier = widthIn(max = ReadableWidth).fillMaxWidth()
