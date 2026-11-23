package com.sclera.sclerago.domain.system

/** Whether times show on a 24-hour clock (the phone's own 12/24-hour setting); injected so formatting is testable. */
fun interface ClockFormat {
    fun is24Hour(): Boolean
}
