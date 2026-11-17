package com.sclera.sclerago.presentation.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * How long ago [at] was, worded as the web's `timeAgo`: "just now" (under 45 seconds), "3 min ago", "2 h ago",
 * "4 d ago" (under a week), then the date with its year; "" when [at] is unknown.
 */
@Composable
internal fun agoText(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return ""
    val seconds = ((now - at) / 1000.0).coerceAtLeast(0.0)
    return when {
        seconds < 45 -> stringResource(R.string.val_ago_now)
        seconds < HOUR -> stringResource(R.string.val_ago_min, (seconds / 60).roundToInt())
        seconds < DAY -> stringResource(R.string.val_ago_hours, (seconds / HOUR).roundToInt())
        seconds < WEEK -> stringResource(R.string.val_ago_days, (seconds / DAY).roundToInt())
        else -> {
            val locale = LocalConfiguration.current.locales[0]
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)
                .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        }
    }
}

private const val HOUR = 3600.0
private const val DAY = 86_400.0
private const val WEEK = DAY * 7
