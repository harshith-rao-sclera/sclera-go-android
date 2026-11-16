package com.sclera.sclerago.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.StoredFile
import com.sclera.sclerago.data.offline.localKey
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The largest file the data sync uploads from the database (10MB); bigger ones stay here. */
private const val UPLOAD_LIMIT = 10L * 1024 * 1024

private const val DIR = "asset-media"

/**
 * Asset media in `filesDir/asset-media` (app-private, excluded from backup like the rest of the app's data). Only
 * content URIs and the app's own files are read; everything else is refused. Deleted on sign-out.
 */
@Singleton
class LocalAssetFileStore @Inject constructor(@ApplicationContext private val context: Context) : AssetFileStore, UserDataCleaner {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    override fun store(media: Media, id: String): StoredFile? = runCatching {
        val uri = Uri.parse(media.uri)
        if (!readable(uri)) return null
        val name = media.fileName ?: displayName(uri)
        val type = context.contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionOf(name))
        val key = "$id.${extensionOf(name).ifEmpty { type?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType).orEmpty() }.ifEmpty { "bin" }}"
        val target = File(dir, key)
        val size = copy(uri, target) ?: return null
        val bounds = if (type?.startsWith("image/") == true) bounds(target) else null
        StoredFile(key, name, type, size, bounds?.first, bounds?.second)
    }.getOrNull()

    override fun bytes(key: String): ByteArray? = runCatching {
        File(dir, safe(key)).takeIf { it.isFile && it.length() <= UPLOAD_LIMIT }?.readBytes()
    }.getOrNull()

    override fun delete(keys: Collection<String>) {
        keys.forEach { runCatching { File(dir, safe(it)).delete() } }
    }

    override fun discardOriginals(media: List<Media>) {
        media.filter { it.owned }.forEach { m -> runCatching { Uri.parse(m.uri).takeIf(::ownFile)?.path?.let { File(it).delete() } } }
    }

    override fun uriFor(objectPath: String): String {
        val file = localKey(objectPath)?.let { File(dir, safe(it)) }?.takeIf { it.isFile }
        return file?.let { Uri.fromFile(it).toString() } ?: objectPath
    }

    override fun clear() {
        dir.deleteRecursively()
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

    /** Copies [uri] to [target]; the size, or null (and no file) when the source is unreadable or over the import cap. */
    private fun copy(uri: Uri, target: File): Long? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        var total = 0L
        input.use { src ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = src.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        target.delete()
                        return null
                    }
                    out.write(buffer, 0, n)
                }
            }
        }
        return total
    }

    private fun bounds(file: File): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    /** The file extension in lower case letters and digits (at most 5), or empty. */
    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }.orEmpty()

    /** A key with no path parts, so it can only name a file inside [dir]. */
    private fun safe(key: String): String = File(key).name

    private companion object {
        const val BUFFER = 16 * 1024
    }
}
