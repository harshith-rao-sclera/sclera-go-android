package com.sclera.sclerago.data.local

import android.content.Context
import android.provider.Settings
import com.sclera.sclerago.domain.repository.MotionPreference

/** Reads Android's "animator duration scale" (0 = animations removed). */
class AndroidMotionPreference(private val context: Context) : MotionPreference {
    override fun isReduced(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
