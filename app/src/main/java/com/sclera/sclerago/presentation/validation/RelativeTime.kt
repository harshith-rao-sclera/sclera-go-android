package com.sclera.sclerago.presentation.validation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R

/** How long ago [at] was: "just now", "5 min ago", "3 h ago" or "4 d ago". */
/** How long something took or has left, as the web's job page says it ("40s", "12 min", "1.5 h"); blank when unknown. */
@Composable
fun duration(seconds: Double): String = when {
    !seconds.isFinite() || seconds < 0 -> ""
    seconds < 60 -> stringResource(R.string.valt_duration_seconds, maxOf(1, Math.round(seconds).toInt()))
    seconds < 3600 -> stringResource(R.string.valt_duration_minutes, Math.round(seconds / 60).toInt())
    else -> stringResource(R.string.valt_duration_hours, seconds / 3600)
}

/** Seconds a job has run: from its start to when it finished (or [now] while it runs); null without a start. */
fun elapsedSeconds(startedAt: Long?, finishedAt: Long?, now: Long = System.currentTimeMillis()): Double? =
    startedAt?.let { ((finishedAt ?: now) - it) / 1000.0 }

@Composable
fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - at) / 1000
    return when {
        s < 60 -> stringResource(R.string.val_ago_now)
        s < 3600 -> stringResource(R.string.val_ago_min, (s / 60).toInt())
        s < 48 * 3600 -> stringResource(R.string.val_ago_hours, (s / 3600).toInt())
        else -> stringResource(R.string.val_ago_days, (s / 86400).toInt())
    }
}
