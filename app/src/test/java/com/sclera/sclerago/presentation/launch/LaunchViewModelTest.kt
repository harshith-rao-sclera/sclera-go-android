package com.sclera.sclerago.presentation.launch

import com.sclera.sclerago.domain.repository.MotionPreference
import com.sclera.sclerago.domain.usecase.launch.ShouldPlayIntroUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchViewModelTest {
    private fun vm(reduced: Boolean) = LaunchViewModel(
        ShouldPlayIntroUseCase(object : MotionPreference {
            override fun isReduced() = reduced
        }),
    )

    @Test fun playsUntilFinished() {
        val vm = vm(reduced = false)
        assertTrue(vm.playIntro.value)
        vm.onIntroFinished()
        assertFalse(vm.playIntro.value)
    }

    @Test fun reducedMotionSkipsIntro() = assertFalse(vm(reduced = true).playIntro.value)
}
