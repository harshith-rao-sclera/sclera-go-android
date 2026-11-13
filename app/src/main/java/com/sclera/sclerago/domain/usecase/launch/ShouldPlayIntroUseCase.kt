package com.sclera.sclerago.domain.usecase.launch

import com.sclera.sclerago.domain.repository.MotionPreference
import javax.inject.Inject

/** The launch animation plays once per cold start, and never when the user turned animations off. */
class ShouldPlayIntroUseCase @Inject constructor(private val motion: MotionPreference) {
    operator fun invoke(alreadyPlayed: Boolean): Boolean = !alreadyPlayed && !motion.isReduced()
}
