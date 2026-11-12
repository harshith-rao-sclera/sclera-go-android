package com.sclera.sclerago.domain.model

/** A photo or video taken with the in-app camera or picked from the gallery. */
data class Media(
    val id: String,
    val uri: String,
    val video: Boolean,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** True when the file lives in this app's own storage (taken with the in-app camera). */
    val owned: Boolean = false,
    /** Set for a document (PDF, spreadsheet, …) uploaded from files: its display name. */
    val fileName: String? = null,
    /** Attribution for a reference photo from the internet (author · licence · source). */
    val credit: String? = null,
) {
    val durationText: String get() = formatDuration(durationMs)
    val isDoc: Boolean get() = fileName != null
    val ext: String get() = fileName?.substringAfterLast('.', "")?.uppercase()?.take(4)?.ifEmpty { "FILE" } ?: ""
}

/** A document chosen in the system file manager for import. */
data class ImportedFile(val id: String, val uri: String, val name: String, val size: Long, val mime: String?) {
    val ext: String get() = name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
    val baseName: String get() = name.substringBeforeLast('.')
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Wall-clock time of [at] as "HH:mm:ss". */
fun timeLabel(at: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at))

/** The name a draft asset gets from a camera shot, e.g. "Asset from photo 14:02:31". */
fun captureName(m: Media): String = (if (m.video) "Asset from video " else "Asset from photo ") + timeLabel(m.createdAt)
