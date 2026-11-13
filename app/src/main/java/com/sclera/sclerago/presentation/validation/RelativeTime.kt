package com.sclera.sclerago.presentation.validation

/** How long ago [at] was: "just now", "5 min ago", "3 h ago" or "4 d ago". */
fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - at) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 48 * 3600 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}
