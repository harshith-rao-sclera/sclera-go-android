package com.sclera.sclerago.presentation.common

import java.time.LocalTime
import androidx.compose.ui.platform.LocalConfiguration
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Whether the phone shows times on a 24-hour clock (Settings › Date & time); every time the app shows follows it. */
@Composable
fun is24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

/**
 * The hour letter for a date/time skeleton: "H" for a 24-hour clock, "h" (with AM/PM) for a 12-hour one, or "j" (the
 * language's usual clock) when [use24Hour] is null.
 */
private fun hourLetter(use24Hour: Boolean?): String = when (use24Hour) {
    true -> "H"
    false -> "h"
    null -> "j"
}

/** The pattern of [skeleton] (with the hour letter of [use24Hour]) in [locale]'s order and wording. */
private fun pattern(locale: Locale, skeleton: String): String = DateFormat.getBestDateTimePattern(locale, skeleton)

/**
 * A time of day as the phone shows it: hours and minutes ([seconds]: and seconds) on its 12- or 24-hour clock
 * ([use24Hour]; null: the language's usual one), in [locale]'s form, e.g. "16:57" or "4:57 PM".
 */
fun timeFormatter(locale: Locale, use24Hour: Boolean?, seconds: Boolean = false): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern(locale, hourLetter(use24Hour) + if (seconds) "ms" else "m"), locale)

/**
 * [at] (epoch millis) as the web shows a comment's or history line's time: the short date and the time with seconds in
 * [locale]'s form, on the phone's clock ([use24Hour]), e.g. "13/11/2026 16:57:08" or "11/13/26 4:57:08 PM".
 */
fun formatDateTimeSeconds(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault(), use24Hour: Boolean? = null): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(time) + " " +
        timeFormatter(locale, use24Hour, seconds = true).format(time)
}

/**
 * [at] (epoch millis) as day, month and time in [locale]'s order and wording, on the phone's clock ([use24Hour]), e.g.
 * "5 Nov, 10:42" or "Nov 5, 10:42 AM".
 */
fun formatDayAndTime(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault(), use24Hour: Boolean? = null): String =
    DateTimeFormatter.ofPattern(pattern(locale, "dMMM" + hourLetter(use24Hour) + "m"), locale).format(Instant.ofEpochMilli(at).atZone(zone))

/**
 * [at] (epoch millis) as a day with its year and the time, in [locale]'s order and wording, on the phone's clock
 * ([use24Hour]), as the web's change request line: e.g. "18 Nov 2026, 18:29" or "Nov 18, 2026, 6:29 PM".
 */
fun formatDateAndTime(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault(), use24Hour: Boolean? = null): String =
    DateTimeFormatter.ofPattern(pattern(locale, "dMMMy" + hourLetter(use24Hour) + "m"), locale).format(Instant.ofEpochMilli(at).atZone(zone))

/** [at] (epoch millis) as a day in [locale]'s order and wording, e.g. "17 Nov 2026" or "Nov 17, 2026". */
fun formatDate(at: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "dMMMy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(at).atZone(zone))
}

/**
 * A stored time of day ("HH:mm", as a property's or building's opening hours are kept) as the phone shows times: on its
 * 12- or 24-hour clock, in the device's language ("16:30" or "4:30 PM"); the text itself when it isn't a time.
 */
@Composable
fun storedTimeText(hhmm: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val clock24 = is24Hour()
    return runCatching { LocalTime.parse(hhmm).format(timeFormatter(locale, clock24)) }.getOrDefault(hhmm)
}
