package com.sclera.sclerago.presentation.components

import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The live camera picture for [controller], filling the frame. A tap focuses there (CameraX's own tap to focus) and a
 * ring shows where: it settles on the spot while the camera focuses, stays yellow once it has, turns white when it
 * couldn't, then fades. Two fingers pinch to zoom, between the lens's least and most, and how far ("2.4×") shows at the
 * top centre, [zoomBadgeTop] down, while pinching and for a moment after. Used by the asset camera and the QR / barcode
 * scanner.
 */
@Composable
fun CameraPreview(
    controller: CameraController,
    modifier: Modifier = Modifier,
    zoomBadgeTop: Dp = 72.dp,
    /** The preview's view once made (to take a frame of what it shows, e.g. for a shot still being saved). */
    onView: (PreviewView) -> Unit = {},
) {
    val owner = LocalLifecycleOwner.current
    // Where the last finger lifted, in the preview's pixels: the spot CameraX focuses on when that lift was a tap.
    var lastTap by remember { mutableStateOf(Offset.Zero) }
    var ring by remember { mutableStateOf<FocusRing?>(null) }
    // The zoom the pinch asked for, shown while [pinching] and for ZOOM_SHOWN_MS after; [pinchesEnded] starts that wait.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pinching by remember { mutableStateOf(false) }
    var zoomShown by remember { mutableStateOf(false) }
    var pinchesEnded by remember { mutableIntStateOf(0) }
    LaunchedEffect(pinchesEnded) {
        if (pinchesEnded == 0) return@LaunchedEffect
        delay(ZOOM_SHOWN_MS)
        if (!pinching) zoomShown = false
    }
    DisposableEffect(controller, owner) {
        // Pinching is done here (below), so CameraX's own pinch doesn't zoom a second time.
        controller.isPinchToZoomEnabled = false
        controller.isTapToFocusEnabled = true
        var taps = 0
        val observer = Observer<Int> { state ->
            when (state) {
                CameraController.TAP_TO_FOCUS_STARTED -> ring = FocusRing(++taps, lastTap, state)
                CameraController.TAP_TO_FOCUS_NOT_STARTED -> Unit
                else -> ring = ring?.copy(state = state)
            }
        }
        controller.tapToFocusState.observe(owner, observer)
        onDispose { controller.tapToFocusState.removeObserver(observer) }
    }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                }.also(onView)
            },
            // One finger is left to the preview (its tap to focus) and to what holds it (the tabs' swipe); only where it
            // lifts is noted. Two fingers are a pinch: it zooms from where the camera was when the gesture began, and
            // takes the touches from then until every finger is up, so a pinch that drifts sideways never swipes the tabs.
            modifier = Modifier.fillMaxSize().pointerInput(controller) {
                awaitEachGesture {
                    var target = controller.zoomState.value?.zoomRatio ?: 1f
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinched = true
                            val change = event.calculateZoom()
                            val range = controller.zoomState.value
                            if (change != 1f && range != null) {
                                target = (target * change.pow(PINCH_SENSITIVITY)).coerceIn(range.minZoomRatio, range.maxZoomRatio)
                                controller.setZoomRatio(target)
                                zoom = target
                                pinching = true
                                zoomShown = true
                            }
                        }
                        event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }?.let { lastTap = it.position }
                        if (pinched) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    if (pinched) {
                        pinching = false
                        pinchesEnded++
                    }
                }
            },
        )
        ring?.let { r -> FocusRingMark(r) { if (ring?.id == r.id) ring = null } }
        ZoomBadge(zoom, zoomShown, Modifier.align(Alignment.TopCenter).padding(top = zoomBadgeTop))
    }
}

/** How far the camera is zoomed ("2.4×"), faded in while [shown]. */
@Composable
private fun ZoomBadge(ratio: Float, shown: Boolean, modifier: Modifier) {
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) RING_IN_MS else RING_OUT_MS), label = "zoom-badge")
    if (alpha == 0f) return
    Box(modifier.alpha(alpha).clip(Capsule).background(BadgeBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Txt(stringResource(R.string.camera_zoom, String.format(Locale.getDefault(), "%.1f", ratio)), 14, 700, C.Yellow)
    }
}

/** Tap [id] at [at] (the preview's pixels) and how its focusing stands ([CameraController]'s TAP_TO_FOCUS_*). */
private data class FocusRing(val id: Int, val at: Offset, val state: Int)

@Composable
private fun FocusRingMark(ring: FocusRing, onGone: () -> Unit) {
    val scale = remember(ring.id) { Animatable(RING_START_SCALE) }
    val alpha = remember(ring.id) { Animatable(0f) }
    LaunchedEffect(ring.id) {
        launch { alpha.animateTo(1f, tween(RING_IN_MS)) }
        scale.animateTo(1f, tween(RING_SETTLE_MS, easing = FastOutSlowInEasing))
    }
    // Held while focusing (at most RING_WAIT_MS, should no answer come), briefly once it is known, then faded away.
    LaunchedEffect(ring.id, ring.state) {
        delay(if (ring.state == CameraController.TAP_TO_FOCUS_STARTED) RING_WAIT_MS else RING_HOLD_MS)
        alpha.animateTo(0f, tween(RING_OUT_MS))
        onGone()
    }
    val missed = ring.state == CameraController.TAP_TO_FOCUS_NOT_FOCUSED || ring.state == CameraController.TAP_TO_FOCUS_FAILED
    val colour by animateColorAsState(if (missed) Color.White else C.Yellow, tween(RING_IN_MS), label = "focus-ring")
    val half = with(LocalDensity.current) { (RingSize / 2).roundToPx() }
    Canvas(
        Modifier
            .offset { IntOffset(ring.at.x.roundToInt() - half, ring.at.y.roundToInt() - half) }
            .size(RingSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
    ) {
        val stroke = 1.5.dp.toPx()
        drawCircle(colour, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        drawCircle(colour, radius = 2.dp.toPx())
    }
}

private val RingSize = 72.dp
private const val RING_START_SCALE = 1.35f
private const val RING_IN_MS = 150
private const val RING_SETTLE_MS = 250
private const val RING_HOLD_MS = 700L
private const val RING_WAIT_MS = 3_000L
private const val RING_OUT_MS = 300
private const val ZOOM_SHOWN_MS = 1_000L

/** How much faster than the fingers' spread the zoom moves: fingers twice as far apart zoom 2^this as far. */
private const val PINCH_SENSITIVITY = 1.5f
private val BadgeBg = Color(0xCC202226)
