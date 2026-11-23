package com.sclera.sclerago.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.sclera.sclerago.presentation.theme.C

// How sheets and dialogs come in: the dimmed background fades in while the sheet slides up (phones) or the dialog grows
// in (tablets and centred dialogs). Short and without a bounce, so it stays subtle.

/** Goes from 0 to 1 once, when the sheet or dialog first shows. */
@Composable
fun rememberAppear(): State<Float> {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(APPEAR_MS, easing = FastOutSlowInEasing)) }
    return shown.asState()
}

/** The dimmed background behind a sheet or dialog, fading in with [shown]. In place of `background(C.Scrim)`. */
fun Modifier.scrim(shown: State<Float>): Modifier = drawBehind { drawRect(C.Scrim.copy(alpha = C.Scrim.alpha * shown.value)) }

/** A centred dialog or sheet: it fades in as it grows from a little smaller. */
fun Modifier.popIn(shown: State<Float>): Modifier = graphicsLayer {
    val p = shown.value
    alpha = p
    scaleX = POP_FROM + (1f - POP_FROM) * p
    scaleY = scaleX
}

/** A sheet: docked at the bottom ([tablet] false) it slides up from below; centred on a tablet it grows in ([popIn]). */
fun Modifier.sheetIn(shown: State<Float>, tablet: Boolean): Modifier =
    if (tablet) popIn(shown) else graphicsLayer { translationY = (1f - shown.value) * size.height }

private const val APPEAR_MS = 240
private const val POP_FROM = 0.92f
