package com.sclera.sclerago.presentation.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchMotionTest {
    @Test fun progressIsZeroBeforeTheDelay() = assertEquals(0f, LaunchMotion.HeroIn.progress(1999f), 0f)

    @Test fun progressIsOneAfterTheDuration() = assertEquals(1f, LaunchMotion.HeroIn.progress(3100f), 0f)

    @Test fun progressRisesInBetween() {
        val mid = LaunchMotion.SymbolZoom.progress(500f)
        assertTrue(mid > 0f && mid < 1f)
    }

    @Test fun everyEntranceEndsByTheTimelineEnd() {
        Entrance.entries.forEach {
            assertEquals(1f, it.phase.progress(LaunchMotion.TotalMs.toFloat()), 0f)
        }
        assertEquals(1f, LaunchMotion.SplashOut.progress(LaunchMotion.SplashEndMs.toFloat()), 0f)
    }
}
