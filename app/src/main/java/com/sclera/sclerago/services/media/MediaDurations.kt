package com.sclera.sclerago.services.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How long a video or voice memo runs, read from its file, for media whose row doesn't say (media added on iOS saves no
 * `duration_seconds`). Remembered per file: reading it can make the media provider copy the file out of the database.
 */
object MediaDurations {
    private val known = LruCache<String, Long>(64)

    /** The length of the file at [uri] in milliseconds, or null when it can't be read (not on this device, not media). */
    suspend fun of(context: Context, uri: String): Long? {
        known.get(uri)?.let { return it }
        val ms = withContext(Dispatchers.IO) {
            // Released by hand: MediaMetadataRetriever is only AutoCloseable from Android 10 (the app runs from Android 8).
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, Uri.parse(uri))
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (_: Exception) {
                null
            } finally {
                r.release()
            }
        }?.takeIf { it > 0 }
        if (ms != null) known.put(uri, ms)
        return ms
    }
}
