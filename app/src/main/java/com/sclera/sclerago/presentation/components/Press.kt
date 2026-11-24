package com.sclera.sclerago.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** How small a button gets while pressed; a large card ([PRESSED_CARD]) shrinks less, so it doesn't look like it jumps. */
const val PRESSED_BUTTON = 0.95f
const val PRESSED_CARD = 0.985f

/**
 * Shrinks a little while [interaction] is pressed and springs back when let go, so a tap feels answered. Put it first on
 * the button (before `clip` and `background`) and hand the same [interaction] to its `clickable`.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = PRESSED_BUTTON): Modifier {
    val down by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (down) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * `clickable` that also shrinks a little while pressed ([pressScale]). Put it where the button's `clip` would go (before
 * its `background` and `border`, so they shrink with it), giving it that [shape] so the ripple stays inside the corners.
 * A full-width row passes [PRESSED_CARD] as [pressed].
 */
@Composable
fun Modifier.pressClickable(
    shape: Shape? = null,
    pressed: Float = PRESSED_BUTTON,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    /** Tap and hold; [onLongClickLabel] is read out for it. Null: holding does nothing more than a tap. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val press = remember { MutableInteractionSource() }
    val shaped = pressScale(press, pressed).then(if (shape != null) Modifier.clip(shape) else Modifier)
    return if (onLongClick == null) {
        shaped.clickable(press, LocalIndication.current, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
    } else {
        shaped.combinedClickable(
            interactionSource = press, indication = LocalIndication.current, enabled = enabled, onClickLabel = onClickLabel,
            role = role, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick,
        )
    }
}
