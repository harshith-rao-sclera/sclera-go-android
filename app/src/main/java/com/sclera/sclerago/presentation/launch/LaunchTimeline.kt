package com.sclera.sclerago.presentation.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** The clock of the launch animation: milliseconds since it started, 0 to [LaunchMotion.TotalMs]. */
@Stable
class LaunchTimeline {
    private val clock = Animatable(0f)

    val timeMs: Float get() = clock.value

    suspend fun play() {
        clock.animateTo(LaunchMotion.TotalMs.toFloat(), tween(LaunchMotion.TotalMs, easing = LinearEasing))
    }
}

/** Set while the launch animation runs; null otherwise, so screens show fully entered. */
val LocalLaunchTimeline = compositionLocalOf<LaunchTimeline?> { null }

/** How an element enters while the launch animation runs. */
enum class Entrance(
    internal val phase: MotionPhase,
    internal val fade: Boolean = true,
    internal val offsetDp: Float = 0f,
    internal val startScale: Float = 1f,
    internal val slidesFromBottom: Boolean = false,
) {
    /** Sign-in photo: zooms out from 1.15 while fading in. */
    Hero(LaunchMotion.HeroIn, startScale = LaunchMotion.HeroStartScale),

    /** Sign-in sheet: slides up from the bottom edge. */
    Sheet(LaunchMotion.SheetIn, fade = false, slidesFromBottom = true),

    /** Brand row, tagline: rise 20dp while fading in. */
    Rise(LaunchMotion.RiseIn, offsetDp = LaunchMotion.RiseOffsetDp),

    DashHeader(LaunchMotion.HeaderIn, offsetDp = LaunchMotion.HeaderOffsetDp),
    DashContent(LaunchMotion.ContentIn, offsetDp = LaunchMotion.RiseOffsetDp),
    DashNav(LaunchMotion.NavIn),
}

/** Animates this element with [entrance] while the launch animation runs; does nothing otherwise. */
@Composable
fun Modifier.launchEntrance(entrance: Entrance): Modifier {
    val timeline = LocalLaunchTimeline.current ?: return this
    return graphicsLayer {
        val p = entrance.phase.progress(timeline.timeMs)
        if (entrance.fade) alpha = p
        val scale = entrance.startScale + (1f - entrance.startScale) * p
        scaleX = scale
        scaleY = scale
        translationY = (if (entrance.slidesFromBottom) size.height else entrance.offsetDp * density) * (1f - p)
    }
}
