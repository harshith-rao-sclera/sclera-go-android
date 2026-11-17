package com.sclera.sclerago.presentation.validation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sclera.sclerago.presentation.components.r
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A list of steps that can be dragged into order by their handle (the editor's steps, the Default order): the held
 * step follows the finger, lifted above the rest, the steps it passes slide out of its way, and on release it goes to
 * the nearest place (past half-way counts as the next one) and settles there. [onMove] moves [id] by `by` places.
 * The rows go straight into the caller's column, which must space them [gap] apart. [row] draws one: put `modifier` on
 * the row and `handle` on its drag handle.
 */
@Composable
internal fun ReorderableSteps(
    ids: List<String>,
    onMove: (id: String, by: Int) -> Unit,
    gap: Dp = 8.dp,
    row: @Composable (index: Int, id: String, modifier: Modifier, handle: Modifier) -> Unit,
) {
    var dragId by remember { mutableStateOf<String?>(null) }
    // The finger's travel is applied straight away (not queued), so nothing left over can move the step after release
    // and leave it half-way between two places.
    var dragY by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val current by rememberUpdatedState(ids)

    fun drop(id: String) {
        val list = current
        val f = list.indexOf(id)
        val slot = (heights[id] ?: 0) + gapPx
        if (f < 0 || slot <= 0f) {
            dragId = null
            dragY = 0f
            return
        }
        val t = (f + (dragY / slot).roundToInt()).coerceIn(0, list.lastIndex)
        if (t != f) {
            onMove(id, t - f)
            dragY -= (t - f) * slot
        }
        settle = scope.launch {
            animate(dragY, 0f, animationSpec = tween(200)) { v, _ -> dragY = v }
            dragY = 0f
            dragId = null
        }
    }

    val from = ids.indexOf(dragId)
    val slot = dragId?.let { (heights[it] ?: 0) + gapPx } ?: 0f
    val target = if (from < 0 || slot <= 0f) from else (from + (dragY / slot).roundToInt()).coerceIn(0, ids.lastIndex)
    ids.forEachIndexed { i, id ->
        key(id) {
            val held = id == dragId
            val shift = when {
                from < 0 || held -> 0f
                target > from && i in (from + 1)..target -> -slot
                target < from && i in target until from -> slot
                else -> 0f
            }
            // A fresh animation per order, so a step that just moved doesn't slide back.
            val slide = remember(ids) { Animatable(0f) }
            LaunchedEffect(shift) { slide.animateTo(shift, tween(180)) }
            val lift by animateFloatAsState(if (held) 1f else 0f, tween(150), label = "lift")
            row(
                i, id,
                Modifier
                    .onSizeChanged { heights[id] = it.height }
                    .zIndex(if (held) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (held) dragY else slide.value
                        scaleX = 1f + 0.03f * lift
                        scaleY = 1f + 0.03f * lift
                        shadowElevation = 14.dp.toPx() * lift
                        shape = r(11)
                    },
                Modifier.pointerInput(id) {
                    detectDragGestures(
                        onDragStart = {
                            dragId = id
                            settle?.cancel()
                            dragY = 0f
                        },
                        onDragEnd = { drop(id) },
                        onDragCancel = { drop(id) },
                    ) { change, amount ->
                        change.consume()
                        dragY += amount.y
                    }
                },
            )
        }
    }
}
