package com.sclera.sclerago.presentation.common

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [at] (epoch millis) as day, month and time in [locale]'s order and wording, e.g. "5 Nov, 10:42" or "Nov 5, 10:42 AM". */
fun formatDayAndTime(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMjm")
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(at).atZone(zone))
}
