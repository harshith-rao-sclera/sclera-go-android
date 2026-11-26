package com.sclera.sclerago.presentation.assets

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.MediaLoader
import com.sclera.sclerago.services.media.cropPhoto
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Test tags of the crop screen. */
object CropTags {
    const val SCREEN = "crop-screen"
    const val DONE = "crop-done"
    const val RESET = "crop-reset"
}

/** The part of the photo kept: its edges as fractions (0..1) of the upright photo. */
private data class CropFrame(val l: Float, val t: Float, val r: Float, val b: Float)

/** The whole photo: nothing cut off. */
private val WHOLE = CropFrame(0f, 0f, 1f, 1f)

/** The smallest the frame gets, as a fraction of each side. */
private const val MIN_SIDE = 0.1f

/** How far from a corner or side a touch still takes hold of it. */
private val TOUCH_REACH = 44.dp

/** How far past the photo the frame's touch area reaches (a handle sits half outside the photo). */
private val TOUCH_MARGIN = 40.dp

/** How wide the photo is read to be shown here. */
private const val PREVIEW_PX = 1600

/** What a drag holds: one of the frame's corners, or the whole frame. */
private enum class Grab { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END, START, END, TOP, BOTTOM, MOVE }

/**
 * Crops a shot of the camera's strip: Cancel, Reset and Done along the top, then the photo upright with a frame over it
 * (thirds lines, a yellow dot at each corner and a bar on each side) that a corner or side drag resizes and a drag inside moves (the rest dimmed). Done saves the framed part as a new photo and hands it to
 * [onCropped] (the original is left as it is); Done with the whole photo framed, Cancel or back just closes.
 */
@Composable
fun CropShotScreen(shot: Media, onCancel: () -> Unit, onCropped: (Media) -> Unit) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onCancel)
    val picture by produceState<ImageBitmap?>(null, shot.uri) {
        value = MediaLoader.load(context, shot.uri, video = false, maxPx = PREVIEW_PX)
    }
    var frame by remember(shot.id) { mutableStateOf(WHOLE) }
    var busy by remember { mutableStateOf(false) }

    fun done() {
        if (busy) return
        if (frame == WHOLE) return onCancel()
        busy = true
        val f = frame
        scope.launch {
            val file = cropPhoto(context, Uri.parse(shot.uri), f.l, f.t, f.r, f.b)
            busy = false
            if (file == null) toaster.show(UiText.Res(R.string.crop_failed), ToastKind.PROBLEM)
            else onCropped(Media("c-${file.name}", Uri.fromFile(file).toString(), video = false, owned = true))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().testTag(CropTags.SCREEN)) {
        // Cancel and Done as dark rounded buttons with yellow text, Reset (greyed until the frame moves) between them.
        Row(
            Modifier.fillMaxWidth().background(C.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(stringResource(R.string.action_cancel), onCancel, bg = C.Btn, fg = C.Yellow, height = 46.dp, hPad = 22.dp, size = 16)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val resettable = frame != WHOLE && !busy
                Txt(
                    stringResource(R.string.filter_reset), 16, 500, if (resettable) C.Soft else C.Dim,
                    Modifier.pressClickable(shape = r(8), enabled = resettable) { frame = WHOLE }.padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag(CropTags.RESET),
                )
            }
            Pill(
                stringResource(R.string.action_done), ::done, Modifier.alpha(if (busy) 0.5f else 1f).testTag(CropTags.DONE),
                bg = C.Btn, fg = C.Yellow, height = 46.dp, hPad = 22.dp, size = 16, weight = 700,
            )
        }
        // Room at the sides, so a corner or side is dragged clear of the screen's edge (where a swipe means Back).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            val pic = picture
            if (pic == null) {
                CircularProgressIndicator(color = C.Yellow)
                return@BoxWithConstraints
            }
            // The photo as large as fits, its own shape: the frame's fractions are of what is shown.
            val ratio = pic.width.toFloat() / pic.height
            val wide = ratio > maxWidth.value / maxHeight.value
            val w = if (wide) maxWidth else maxHeight * ratio
            val h = if (wide) maxWidth / ratio else maxHeight
            Box(Modifier.size(w, h)) {
                Image(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                // Larger than the photo by the touch margin, so the half of each handle outside the photo answers too.
                CropOverlay(frame, { frame = it }, Modifier.requiredSize(w + TOUCH_MARGIN * 2, h + TOUCH_MARGIN * 2))
                if (busy) Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.Yellow)
                }
            }
        }
    }
}

/**
 * The frame over the photo: the outside dimmed, a white edge with thirds lines, a yellow dot at each corner and a bar on
 * each side. It reaches [TOUCH_MARGIN] past the photo on every side, so a handle on the photo's edge can be caught from
 * outside it too. A drag follows the finger from the moment it touches (no slop), and every movement counts, however
 * many arrive between two frames drawn.
 */
@Composable
private fun CropOverlay(frame: CropFrame, onChange: (CropFrame) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(frame)
    val density = LocalDensity.current
    val reach = with(density) { TOUCH_REACH.toPx() }
    val inset = with(density) { TOUCH_MARGIN.toPx() }
    Canvas(
        // Drags on the frame are never taken as the system's Back swipe.
        modifier.systemGestureExclusion().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width - inset * 2
                val h = size.height - inset * 2
                val g = grabAt(current, down.position - Offset(inset, inset), w, h, reach) ?: return@awaitEachGesture
                down.consume()
                // The frame as this drag has made it so far: each movement builds on the last, not on the frame drawn.
                var moved = current
                drag(down.id) { change ->
                    val d = change.positionChange()
                    change.consume()
                    moved = moved.dragged(g, d.x / w, d.y / h)
                    onChange(moved)
                }
            }
        },
    ) {
        translate(inset, inset) { drawFrame(current, size.width - inset * 2, size.height - inset * 2) }
    }
}

/**
 * What a drag starting at [p] holds: a corner within [reach], else a side within [reach] of it (along its length), else
 * the frame when inside it, else nothing.
 */
private fun grabAt(f: CropFrame, p: Offset, w: Float, h: Float, reach: Float): Grab? {
    fun near(x: Float, y: Float) = (p.x - x * w).let { it * it } + (p.y - y * h).let { it * it } <= reach * reach
    // Level with a side: between the frame's top and bottom (for its start and end sides), or its start and end.
    val alongY = p.y in f.t * h..f.b * h
    val alongX = p.x in f.l * w..f.r * w
    return when {
        near(f.l, f.t) -> Grab.TOP_START
        near(f.r, f.t) -> Grab.TOP_END
        near(f.l, f.b) -> Grab.BOTTOM_START
        near(f.r, f.b) -> Grab.BOTTOM_END
        alongY && abs(p.x - f.l * w) <= reach -> Grab.START
        alongY && abs(p.x - f.r * w) <= reach -> Grab.END
        alongX && abs(p.y - f.t * h) <= reach -> Grab.TOP
        alongX && abs(p.y - f.b * h) <= reach -> Grab.BOTTOM
        p.x in f.l * w..f.r * w && p.y in f.t * h..f.b * h -> Grab.MOVE
        else -> null
    }
}

/** The frame after a drag of [dx], [dy] (fractions of the photo) on [g], kept on the photo and no smaller than [MIN_SIDE]. */
private fun CropFrame.dragged(g: Grab, dx: Float, dy: Float): CropFrame = when (g) {
    Grab.MOVE -> {
        val mx = dx.coerceIn(-l, 1f - r)
        val my = dy.coerceIn(-t, 1f - b)
        CropFrame(l + mx, t + my, r + mx, b + my)
    }
    Grab.TOP_START -> copy(l = (l + dx).coerceIn(0f, r - MIN_SIDE), t = (t + dy).coerceIn(0f, b - MIN_SIDE))
    Grab.TOP_END -> copy(r = (r + dx).coerceIn(l + MIN_SIDE, 1f), t = (t + dy).coerceIn(0f, b - MIN_SIDE))
    Grab.BOTTOM_START -> copy(l = (l + dx).coerceIn(0f, r - MIN_SIDE), b = (b + dy).coerceIn(t + MIN_SIDE, 1f))
    Grab.BOTTOM_END -> copy(r = (r + dx).coerceIn(l + MIN_SIDE, 1f), b = (b + dy).coerceIn(t + MIN_SIDE, 1f))
    Grab.START -> copy(l = (l + dx).coerceIn(0f, r - MIN_SIDE))
    Grab.END -> copy(r = (r + dx).coerceIn(l + MIN_SIDE, 1f))
    Grab.TOP -> copy(t = (t + dy).coerceIn(0f, b - MIN_SIDE))
    Grab.BOTTOM -> copy(b = (b + dy).coerceIn(t + MIN_SIDE, 1f))
}

private fun DrawScope.drawFrame(f: CropFrame, w: Float, h: Float) {
    val left = f.l * w
    val top = f.t * h
    val right = f.r * w
    val bottom = f.b * h
    val dim = Color(0x99000000)
    drawRect(dim, Offset.Zero, Size(w, top))
    drawRect(dim, Offset(0f, bottom), Size(w, h - bottom))
    drawRect(dim, Offset(0f, top), Size(left, bottom - top))
    drawRect(dim, Offset(right, top), Size(w - right, bottom - top))
    // Thirds, to line things up.
    val grid = Color.White.copy(alpha = 0.35f)
    val thin = 1.dp.toPx()
    for (i in 1..2) {
        val x = left + (right - left) * i / 3f
        val y = top + (bottom - top) * i / 3f
        drawLine(grid, Offset(x, top), Offset(x, bottom), thin)
        drawLine(grid, Offset(left, y), Offset(right, y), thin)
    }
    drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(1.5.dp.toPx()))
    // The sides can be dragged too: a short yellow bar in the middle of each.
    val bar = 22.dp.toPx()
    val barWidth = 4.dp.toPx()
    val midX = (left + right) / 2
    val midY = (top + bottom) / 2
    drawLine(C.Yellow, Offset(midX - bar / 2, top), Offset(midX + bar / 2, top), barWidth, StrokeCap.Round)
    drawLine(C.Yellow, Offset(midX - bar / 2, bottom), Offset(midX + bar / 2, bottom), barWidth, StrokeCap.Round)
    drawLine(C.Yellow, Offset(left, midY - bar / 2), Offset(left, midY + bar / 2), barWidth, StrokeCap.Round)
    drawLine(C.Yellow, Offset(right, midY - bar / 2), Offset(right, midY + bar / 2), barWidth, StrokeCap.Round)
    // The corners to drag: a yellow dot on each.
    val dot = 8.5.dp.toPx()
    listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { drawCircle(C.Yellow, dot, it) }
}
