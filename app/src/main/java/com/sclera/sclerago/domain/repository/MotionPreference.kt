package com.sclera.sclerago.domain.repository

/** The user's system-wide choice about animation. */
interface MotionPreference {
    /** True when the user turned animations off (accessibility "remove animations"). */
    fun isReduced(): Boolean
}
