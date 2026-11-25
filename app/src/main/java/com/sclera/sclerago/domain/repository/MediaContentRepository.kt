package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Media
import kotlinx.coroutines.flow.Flow

/**
 * The files of an asset's media: the offline database's copy (the sync brings each file down some time after its row),
 * and the server's, fetched while the database doesn't have it yet.
 */
interface MediaContentRepository {
    /** True when [media]'s file is on the device: in the offline database, or a file of the app's own. */
    suspend fun hasLocalCopy(media: Media): Boolean

    /** Emits once straight away, then whenever the database's media files may have changed (look again then). */
    fun localChanges(): Flow<Unit>

    /**
     * Fetches [media] of asset [assetId] from the server and returns where it is opened. A photo is fetched once (later
     * calls reuse the cached copy); a video, voice memo or document is fetched on every call, never cached.
     * [preview]: a photo's smaller version. Throws [com.sclera.sclerago.domain.model.OnlineException] (Offline without a
     * connection).
     */
    suspend fun serverCopy(assetId: String, media: Media, preview: Boolean): String
}
