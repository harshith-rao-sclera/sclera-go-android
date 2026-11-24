package com.sclera.sclerago.presentation.assets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The key of the Other photos card (the kind cards use their kind's `db` value). */
internal const val OTHER_PHOTOS_KEY = "other"

/** How a photo card looks while a photo is dragged over it. */
internal enum class DropLook { NONE, TAKES, FULL }

/**
 * A photo being moved between the photo cards of a new asset's Media tab: tap and hold one to pick it up, drag it over
 * another card, let go to move it there. It lifts and follows the finger, the card under it lights up (red when that kind
 * already has its most photos), and it springs back when let go anywhere else.
 */
@Stable
internal class PhotoDrag(private val scope: CoroutineScope) {
    /** The photo picked up, or null. */
    var media by mutableStateOf<Media?>(null)
        private set

    /** The card it was picked up from. */
    var from by mutableStateOf<String?>(null)
        private set

    /** The photo just moved into another card: it pops into place there. */
    var dropped by mutableStateOf<String?>(null)

    /** Each photo card's place on screen, by its key. */
    val cards = mutableStateMapOf<String, Rect>()

    /** Moves [Media] to the card with that key when it takes it; false sends it back. Set by the Media tab. */
    var accept: (Media, String) -> Boolean = { _, _ -> false }

    private val moved = Animatable(Offset.Zero, Offset.VectorConverter)
    private var start = Offset.Zero

    /** How far it has been dragged from where it was picked up. */
    val offset: Offset get() = moved.value

    /** The card under the finger while a photo is picked up. */
    val over: String? get() = media?.let { val at = start + moved.value; cards.entries.firstOrNull { it.value.contains(at) }?.key }

    fun pickUp(m: Media, card: String, at: Offset) {
        media = m
        from = card
        start = at
        scope.launch { moved.snapTo(Offset.Zero) }
    }

    fun moveBy(by: Offset) {
        scope.launch { moved.snapTo(moved.value + by) }
    }

    /** Lets go: into the card under the finger when it is another one that takes it, else back where it came from. */
    fun drop() {
        val m = media ?: return
        val target = over
        if (target != null && target != from && accept(m, target)) {
            dropped = m.id
            media = null
            from = null
            scope.launch { moved.snapTo(Offset.Zero) }
        } else {
            cancel()
        }
    }

    fun cancel() {
        scope.launch {
            moved.animateTo(Offset.Zero, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            media = null
            from = null
        }
    }
}

/** Records a photo card's place on screen, so a dragged photo knows which card it is over. */
internal fun Modifier.photoDropTarget(drag: PhotoDrag, key: String): Modifier =
    onGloballyPositioned { drag.cards[key] = it.boundsInRoot() }

/** A card's background and edge while a photo is dragged over it (yellow: it takes it; red: it is full). */
@Composable
internal fun dropColors(look: DropLook): Pair<State<Color>, State<Color>> {
    val bg = animateColorAsState(
        when (look) {
            DropLook.TAKES -> C.YTintBg
            DropLook.FULL -> C.DangerBg
            DropLook.NONE -> C.Surface
        },
        label = "drop-bg",
    )
    val edge = animateColorAsState(
        when (look) {
            DropLook.TAKES -> C.Yellow
            DropLook.FULL -> C.Red
            DropLook.NONE -> Color.Transparent
        },
        label = "drop-edge",
    )
    return bg to edge
}

/**
 * A photo tile that can be picked up (tap and hold) and dragged to another card of [drag]: it lifts with a shadow and
 * follows the finger, and pops into place when dropped into another card. A plain tap still opens it. [onPickUp] when it
 * is picked up; then either [onHold] (let go without moving: it was only held) or [onMoved] (it was dragged).
 */
@Composable
internal fun Modifier.draggablePhoto(
    drag: PhotoDrag,
    m: Media,
    card: String,
    onPickUp: () -> Unit = {},
    onHold: () -> Unit = {},
    onMoved: () -> Unit = {},
): Modifier {
    val pickedUp by rememberUpdatedState(onPickUp)
    val held by rememberUpdatedState(onHold)
    val moved by rememberUpdatedState(onMoved)
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    // How far the finger has gone since the photo was picked up: let go without moving, it was only held ([onHold]).
    var travelled by remember { mutableStateOf(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val lifted = drag.media?.id == m.id
    val lift by animateFloatAsState(if (lifted) 1.12f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "lift")
    // Just moved here: it grows into its new place.
    val pop = remember(m.id, m.category) { Animatable(if (drag.dropped == m.id) 0.5f else 1f) }
    LaunchedEffect(pop) {
        if (pop.value < 1f) {
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            if (drag.dropped == m.id) drag.dropped = null
        }
    }
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            val scale = lift * pop.value
            scaleX = scale
            scaleY = scale
            if (lifted) {
                translationX = drag.offset.x
                translationY = drag.offset.y
                shadowElevation = 18.dp.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = true
                alpha = 0.95f
            }
        }
        .pointerInput(m.id, card) {
            detectDragGesturesAfterLongPress(
                onDragStart = { at ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    travelled = 0f
                    pickedUp()
                    drag.pickUp(m, card, origin + at)
                },
                onDrag = { change, by ->
                    change.consume()
                    travelled += by.getDistance()
                    drag.moveBy(by)
                },
                onDragEnd = {
                    if (travelled < slop) {
                        drag.cancel()
                        held()
                    } else {
                        moved()
                        drag.drop()
                    }
                },
                // Let go without moving, the tile's tap takes the finger's release, which ends the drag as cancelled:
                // held still, it was a hold.
                onDragCancel = {
                    drag.cancel()
                    if (travelled < slop) held() else moved()
                },
            )
        }
}
