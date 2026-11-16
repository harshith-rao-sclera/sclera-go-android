package com.sclera.sclerago.domain.model

/** Where one property's offline download is. */
sealed interface PropertyDownload {
    /** Downloading: [fraction] 0–1 of [totalMb] (0 until the server says how big it is). */
    data class Running(val fraction: Float, val downloadedMb: Double, val totalMb: Double) : PropertyDownload

    /** Just finished; shown briefly as a tick before the property counts as downloaded. */
    data object Finished : PropertyDownload

    /** Didn't finish; it can be started again. */
    data object Failed : PropertyDownload
}

/**
 * The offline data on this device: the property whose data is downloaded ([downloadedId]; the sync
 * manager holds one property at a time) and the downloads under way or just ended ([active]).
 */
data class PropertyDownloads(
    val downloadedId: String? = null,
    val active: Map<String, PropertyDownload> = emptyMap(),
)
