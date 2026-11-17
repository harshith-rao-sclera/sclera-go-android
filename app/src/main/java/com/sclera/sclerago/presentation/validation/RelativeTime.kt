package com.sclera.sclerago.presentation.validation

/** How long ago [at] was: "just now", "5 min ago", "3 h ago" or "4 d ago". */
/** How long something took or has left, as the web's job page says it ("40s", "12 min", "1.5 h"); blank when unknown. */
fun duration(seconds: Double): String = when {
    !seconds.isFinite() || seconds < 0 -> ""
    seconds < 60 -> "${maxOf(1, Math.round(seconds).toInt())}s"
    seconds < 3600 -> "${Math.round(seconds / 60)} min"
    else -> String.format(java.util.Locale.ROOT, "%.1f h", seconds / 3600)
}

/** Seconds a job has run: from its start to when it finished (or [now] while it runs); null without a start. */
fun elapsedSeconds(startedAt: Long?, finishedAt: Long?, now: Long = System.currentTimeMillis()): Double? =
    startedAt?.let { ((finishedAt ?: now) - it) / 1000.0 }

fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - at) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 48 * 3600 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}
