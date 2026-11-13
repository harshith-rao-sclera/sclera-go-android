package com.sclera.sclerago.presentation.launch

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/** One animation step on the launch timeline: starts after [delayMs], runs for [durationMs]. */
class MotionPhase(val delayMs: Int, val durationMs: Int, val easing: Easing = LinearEasing) {
    /** 0 before the phase, 1 after it, eased in between. [timeMs] is the time on the launch timeline. */
    fun progress(timeMs: Float): Float =
        easing.transform(((timeMs - delayMs) / durationMs).coerceIn(0f, 1f))
}

/** Every duration, delay and curve of the launch animation (design "Launch screen"), in one place. */
object LaunchMotion {
    private val Zoom = CubicBezierEasing(0.1f, 0.8f, 0.2f, 1f)
    private val Hero = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    private val Sheet = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)
    private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    // Splash layer
    val SymbolZoom = MotionPhase(0, 1000, Zoom)
    val WordmarkIn = MotionPhase(500, 1000, Zoom)
    val SplashOut = MotionPhase(2000, 500, Ease)

    // Sign-in screen
    val HeroIn = MotionPhase(2000, 1100, Hero)
    val SheetIn = MotionPhase(2100, 900, Sheet)
    val RiseIn = MotionPhase(2400, 600, EaseOut)

    // Dashboard
    val HeaderIn = MotionPhase(2250, 600, EaseOut)
    val ContentIn = MotionPhase(2400, 600, EaseOut)
    val NavIn = MotionPhase(2800, 500, Ease)

    /** The splash is gone from this time on. */
    const val SplashEndMs = 2500

    /** When the last entrance finishes. */
    const val TotalMs = 3300

    const val SymbolStartScale = 5f
    const val SymbolStartBlurPx = 10f
    const val WordmarkStartBlurPx = 4f
    const val HeroStartScale = 1.15f
    const val WordmarkStartSpacingEm = 0.35f
    const val WordmarkEndSpacingEm = -0.03f
    const val RiseOffsetDp = 20f
    const val HeaderOffsetDp = -16f
}
