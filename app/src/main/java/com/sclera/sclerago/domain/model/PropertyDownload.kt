package com.sclera.sclerago.domain.model

/** Where one property's offline download is. */
sealed interface PropertyDownload {
    /** Downloading: [fraction] 0–1 of [totalMb] (0 until the server says how big it is). */
    data class Running(val fraction: Float, val downloadedMb: Double, val totalMb: Double) : PropertyDownload

    /** Downloaded; the database is being unzipped and set up (no progress to show). */
    data object Unpacking : PropertyDownload

    /** Just finished; shown briefly as a tick before the property counts as downloaded. */
    data object Finished : PropertyDownload

    /** Didn't finish; it can be started again. */
    data object Failed : PropertyDownload
}

/** Still under way: downloading or unpacking. */
val PropertyDownload?.inProgress: Boolean get() = this is PropertyDownload.Running || this is PropertyDownload.Unpacking

/**
 * The offline data on this device: the recently used property ([downloadedId]: the last one downloaded, which the
 * app opens on; the sync manager holds one property at a time) and the downloads under way or just ended ([active]).
 */
data class PropertyDownloads(
    val downloadedId: String? = null,
    val active: Map<String, PropertyDownload> = emptyMap(),
    /** False until the saved recent property has been read, so the start screen isn't chosen too early. */
    val restored: Boolean = false,
)
