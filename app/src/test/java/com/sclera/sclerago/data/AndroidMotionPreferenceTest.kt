package com.sclera.sclerago.data

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.local.AndroidMotionPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMotionPreferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun scale(value: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)
    }

    @Test fun zeroScaleMeansReduced() {
        scale(0f)
        assertTrue(AndroidMotionPreference(context).isReduced())
    }

    @Test fun normalScaleIsNotReduced() {
        scale(1f)
        assertFalse(AndroidMotionPreference(context).isReduced())
    }
}
