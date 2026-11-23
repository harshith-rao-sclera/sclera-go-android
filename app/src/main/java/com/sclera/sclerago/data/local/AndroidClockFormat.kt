package com.sclera.sclerago.data.local

import android.content.Context
import android.text.format.DateFormat
import com.sclera.sclerago.domain.system.ClockFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The phone's 12/24-hour setting (Settings › Date & time), read each time so a change shows straight away. */
class AndroidClockFormat @Inject constructor(@ApplicationContext private val context: Context) : ClockFormat {
    override fun is24Hour(): Boolean = DateFormat.is24HourFormat(context)
}
