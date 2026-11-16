package com.sclera.sclerago.presentation.common

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * [at] (epoch millis) as the web shows a comment's or history line's time: the short date and the time with seconds in
 * [locale]'s form, e.g. "13/11/2026 16:57:08" or "11/13/26 4:57:08 PM".
 */
fun formatDateTimeSeconds(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(time) + " " +
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).format(time)
}

/** [at] (epoch millis) as day, month and time in [locale]'s order and wording, e.g. "5 Nov, 10:42" or "Nov 5, 10:42 AM". */
fun formatDayAndTime(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMjm")
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(at).atZone(zone))
}
