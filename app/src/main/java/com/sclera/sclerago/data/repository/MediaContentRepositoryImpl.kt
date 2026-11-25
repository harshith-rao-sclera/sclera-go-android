package com.sclera.sclerago.data.repository

import android.webkit.MimeTypeMap
import com.sclera.sclerago.data.local.RemoteMediaFiles
import com.sclera.sclerago.data.offline.MediaBlobs
import com.sclera.sclerago.data.remote.adc.AdcDocumentsApi
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.MediaContentRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asset media files: the offline database's copy ([MediaBlobs], the `multimedia` table the sync fills), else the
 * server's (`GET /properties/{id}/assets/{asset}/media/{media}/content`, as the web reads them), fetched through the
 * shared ADC client (token in the `Authorization` header) into app-private cache ([RemoteMediaFiles]). Only photos are
 * cached for reuse; a video, voice memo or document is fetched each time it is opened.
 */
@Singleton
class MediaContentRepositoryImpl @Inject constructor(
    private val blobs: MediaBlobs,
    private val db: AdcDatabase,
    private val api: AdcDocumentsApi,
    private val files: RemoteMediaFiles,
    private val properties: PropertyDownloadRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MediaContentRepository {

    /** One fetch per file at a time (a thumbnail and the viewer may ask for the same one together). */
    private val fetching = ConcurrentHashMap<String, Mutex>()

    override suspend fun hasLocalCopy(media: Media): Boolean = withContext(io) {
        // Media that isn't the database's (a capture, a picked file) is a file on the device already.
        media.uri != blobs.uri(TABLE, media.id) || blobs.info(TABLE, media.id) != null
    }

    /**
     * The sync tells the app about the rows it pulls, but not when it has downloaded a row's file afterwards, so the
     * database is also looked at again every [RECHECK_MS] while someone is waiting for a file.
     */
    override fun localChanges(): Flow<Unit> = merge(
        db.observe(MULTIMEDIA, TABLE),
        flow {
            while (true) {
                delay(RECHECK_MS)
                emit(Unit)
            }
        },
    )

    override suspend fun serverCopy(assetId: String, media: Media, preview: Boolean): String {
        if (!ID.matches(media.id) || !ID.matches(assetId)) throw OnlineException(OnlineError.NotFound)
        val key = media.id + if (preview) PREVIEW else FULL
        // Only photos are cached; a video, voice memo or document is fetched again each time it is opened.
        val photo = !media.video && !media.audio && !media.isDoc
        return fetching.getOrPut(key) { Mutex() }.withLock {
            (if (photo) withContext(io) { files.uriOf(key) } else null) ?: run {
                val property = properties.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)
                api.mediaContent(property, assetId, media.id, { body ->
                    val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                    body.byteStream().use { files.keep(key, fileName(media, type), it, cache = photo) }
                }, preview = preview)
            }
        }
    }

    /** A document's or voice memo's own name; a photo or video (no name kept) is named by its id and type. */
    private fun fileName(media: Media, type: String?): String = media.fileName?.takeIf { it.isNotBlank() }
        ?: (media.id + (type?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)?.let { ".$it" } ?: ""))

    private companion object {
        const val TABLE = "asset_media"
        const val MULTIMEDIA = "multimedia"
        const val PREVIEW = "-preview"
        const val FULL = "-full"
        const val RECHECK_MS = 20_000L

        /** The server's ids (UUIDs: letters, digits and dashes). */
        val ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
