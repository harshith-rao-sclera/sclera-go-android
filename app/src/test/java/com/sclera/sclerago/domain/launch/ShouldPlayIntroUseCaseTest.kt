package com.sclera.sclerago.domain.launch

import com.sclera.sclerago.domain.repository.MotionPreference
import com.sclera.sclerago.domain.usecase.launch.ShouldPlayIntroUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldPlayIntroUseCaseTest {
    private fun useCase(reduced: Boolean) = ShouldPlayIntroUseCase(object : MotionPreference {
        override fun isReduced() = reduced
    })

    @Test fun playsOnColdStartWithAnimations() = assertTrue(useCase(reduced = false)(alreadyPlayed = false))

    @Test fun skippedWhenAnimationsAreOff() = assertFalse(useCase(reduced = true)(alreadyPlayed = false))

    @Test fun neverPlaysTwice() = assertFalse(useCase(reduced = false)(alreadyPlayed = true))

    @Test fun skippedWhenBothApply() = assertFalse(useCase(reduced = true)(alreadyPlayed = true))
}
