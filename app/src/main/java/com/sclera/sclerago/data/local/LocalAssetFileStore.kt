package com.sclera.sclerago.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.PhotoFile
import com.sclera.sclerago.data.offline.PickedContent
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.services.media.PhotoShrinker
import com.sclera.sclerago.services.media.VideoCompressor
import com.sclera.sclerago.domain.limits.MAX_CLIP_MS
import com.sclera.sclerago.domain.limits.MAX_MEDIA_BYTES
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Where older versions of the app kept their own copy of each asset file (now only the database keeps them). */
private const val LEGACY_DIR = "asset-media"

/**
 * Reads the files added to assets for the offline database, which keeps the only copy of each. Only content URIs and
 * the app's own captures are read; everything else is refused. On sign-out it deletes what is left on the device: the
 * copies older versions kept, the camera and voice memo files not yet attached, and the media provider's short-lived
 * copies.
 */
@Singleton
class LocalAssetFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videos: VideoCompressor,
    private val photos: PhotoShrinker,
) : AssetFileStore, UserDataCleaner {
    private val legacyDir: File get() = File(context.filesDir, LEGACY_DIR)

    /**
     * What an asset keeps: videos re-encoded smaller (720p, about 2.5 Mbit/s) and cut to [MAX_CLIP_MS]; photos over
     * [MAX_MEDIA_BYTES] made smaller; any other file over [MAX_MEDIA_BYTES] refused ([DataError.FILE_TOO_LARGE]), as is a
     * video longer than [MAX_CLIP_MS] that can't be cut ([DataError.CLIP_TOO_LONG]). When one is refused, the copies made
     * for the others are deleted.
     */
    override suspend fun prepare(media: List<Media>): List<Media> {
        val made = mutableListOf<Media>()
        try {
            return media.map { m -> (if (m.video) video(m) else fileOrPhoto(m)).also { if (it !== m) made += it } }
        } catch (e: Exception) {
            discardOriginals(made)
            throw e
        }
    }

    private suspend fun video(m: Media): Media {
        val uri = Uri.parse(m.uri)
        if (!readable(uri)) return m
        val length = videos.durationMs(uri) ?: m.durationMs
        val small = videos.compress(uri, MAX_CLIP_MS)
        if (small == null) {
            // Not compressed: kept as it is, unless it is longer than an asset keeps (a little over is the camera's stop).
            if (length > MAX_CLIP_MS + CLIP_SLACK_MS) throw DataException(DataError.CLIP_TOO_LONG)
            return m
        }
        val cut = small.durationMs < length
        val before = sizeOf(uri)
        if (!cut && before != null && small.file.length() >= before) {
            small.file.delete()
            return m
        }
        return m.copy(
            uri = Uri.fromFile(small.file).toString(), owned = true, sizeBytes = small.file.length(), durationMs = small.durationMs,
            fileName = m.fileName?.substringBeforeLast('.')?.let { "$it.mp4" },
        )
    }

    private suspend fun fileOrPhoto(m: Media): Media {
        val uri = Uri.parse(m.uri)
        if (!readable(uri)) return m
        val size = sizeOf(uri) ?: return m
        if (size <= MAX_MEDIA_BYTES) return m
        val type = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(m.fileName ?: displayName(uri)))
        if (type?.startsWith("image/") != true) throw DataException(DataError.FILE_TOO_LARGE)
        val small = photos.shrink(uri, MAX_MEDIA_BYTES) ?: throw DataException(DataError.FILE_TOO_LARGE)
        return m.copy(
            uri = Uri.fromFile(small).toString(), owned = true, sizeBytes = small.length(),
            fileName = m.fileName?.substringBeforeLast('.')?.let { "$it.jpg" },
        )
    }

    /** The size of the file behind [uri] in bytes, or null when it isn't known. */
    private fun sizeOf(uri: Uri): Long? = runCatching {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).length()
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    override fun read(media: Media): PickedContent? = runCatching {
        val uri = Uri.parse(media.uri)
        if (!readable(uri)) return null
        val name = media.fileName ?: displayName(uri)
        val type = context.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name))
        val bytes = readAtMost(uri, minOf(MAX_IMPORT_BYTES, memoryBudget())) ?: return null
        val bounds = if (type?.startsWith("image/") == true) bounds(bytes) else null
        PickedContent(name, type, bytes.size.toLong(), bounds?.first, bounds?.second, bytes)
    }.getOrNull()

    /** Half of the memory the app can still take, leaving room for the rest of the app (the file is read whole). */
    private fun memoryBudget(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / 2
    }

    override fun discardOriginals(media: List<Media>) {
        media.filter { it.owned }.forEach { m -> runCatching { Uri.parse(m.uri).takeIf(::ownFile)?.path?.let { File(it).delete() } } }
    }

    override fun dropLegacyCopies(held: (rowId: String) -> Boolean) {
        runCatching {
            legacyDir.listFiles { f -> f.isFile }?.forEach { f -> if (held(f.name.substringBefore('.'))) f.delete() }
        }
    }

    override fun forget(rowIds: Collection<String>) {
        if (rowIds.isEmpty()) return
        val ids = rowIds.map { File(it).name }.toSet()
        runCatching { legacyDir.listFiles { f -> f.isFile && f.name.substringBefore('.') in ids }?.forEach { it.delete() } }
    }

    override fun photo(media: Media, maxBytes: Long): PhotoFile? = runCatching {
        val uri = Uri.parse(media.uri)
        if (media.video || media.audio || media.isDoc || !readable(uri)) return null
        val name = displayName(uri)
        val type = context.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name))
        if (type?.startsWith("image/") != true) return null
        val bytes = readAtMost(uri, maxBytes) ?: return null
        PhotoFile(name, type, bytes)
    }.getOrNull()

    /** The content of [uri], or null when it can't be opened or is larger than [max] bytes. */
    private fun readAtMost(uri: Uri, max: Long): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val out = ByteArrayOutputStream()
        input.use { src ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = src.read(buffer)
                if (n < 0) break
                if (out.size() + n > max) return null
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }

    override fun clear() {
        legacyDir.deleteRecursively()
        MediaBlobProvider.viewDir(context).deleteRecursively()
    }

    private fun readable(uri: Uri): Boolean = uri.scheme == "content" || ownFile(uri)

    /** A `file:` URI inside the app's own storage. */
    private fun ownFile(uri: Uri): Boolean =
        uri.scheme == "file" && uri.path?.let { File(it).canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator) } == true

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull() ?: "media"
    }

    private fun bounds(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    /** The file extension in lower case letters and digits (at most 5), or empty. */
    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }.orEmpty()

    private companion object {
        const val BUFFER = 16 * 1024

        /** A recording the camera stopped at the limit can run a little over it. */
        const val CLIP_SLACK_MS = 500L
    }
}
