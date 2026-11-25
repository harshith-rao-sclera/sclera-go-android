package com.sclera.sclerago.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.sclera.sclerago.data.offline.MediaBlobs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Serves asset media from the offline database (`content://<app>.media/<table>/<rowId>`, see [MediaBlobs]): the only
 * copy of each file is the database's. Also serves the documents fetched from the server to be opened
 * (`content://<app>.media/downloads/<id>`, see [DocumentDownloads]) and the media fetched from the server while the
 * database doesn't have it yet (`content://<app>.media/remote/<key>`, see [RemoteMediaFiles]). Not exported; another app (a PDF viewer) can read one
 * only through a single-use grant on the intent that opens it. Read-only.
 *
 * Photos are streamed straight from the database. Players and viewers need to jump around in a video, a voice memo or a
 * document, which a stream can't do, so those are written to a short-lived copy in the cache while they are open; only
 * the last few are kept, and all of them go when the app starts and on sign-out ([LocalAssetFileStore.clear]).
 */
class MediaBlobProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun blobs(): MediaBlobs
    }

    private val blobs: MediaBlobs by lazy {
        EntryPointAccessors.fromApplication(requireNotNull(context).applicationContext, Deps::class.java).blobs()
    }

    override fun onCreate(): Boolean {
        // Short-lived copies from before don't outlive the app.
        context?.let {
            viewDir(it).deleteRecursively()
            DocumentDownloads.dir(it).deleteRecursively()
            RemoteMediaFiles.dir(it).deleteRecursively()
        }
        return true
    }

    override fun getType(uri: Uri): String? {
        download(uri)?.let { return typeOf(it) }
        return target(uri)?.let { (table, id) -> blobs.info(table, id)?.contentType }
    }

    /** Name and size, for the app that opens the file. */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val info = download(uri)?.let { MediaBlobs.Info(typeOf(it), it.name, it.length()) }
            ?: target(uri)?.let { (table, id) -> blobs.info(table, id) }
            ?: return null
        val id = uri.lastPathSegment.orEmpty()
        val columns = projection?.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) info.sizeBytes else info.fileName ?: id })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Media can only be read")
        download(uri)?.let { return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
        val (table, id) = target(uri) ?: throw FileNotFoundException()
        val info = blobs.info(table, id) ?: throw FileNotFoundException()
        // A photo is read front to back: stream it from the database, no copy.
        if (info.contentType?.startsWith("image/") == true) {
            return openPipeHelper(uri, info.contentType, null as Bundle?, Unit) { pipe, _, _, _, _ ->
                // Readers often stop early (a thumbnail reads only the size first), which breaks the pipe. That is
                // expected; left uncaught it would crash the app from the provider's background thread.
                runCatching { FileOutputStream(pipe.fileDescriptor).use { out -> blobs.copy(table, id, out) } }
            }
        }
        return ParcelFileDescriptor.open(viewCopy(table, id, info), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** The short-lived copy a player or viewer can seek in: reused while it is current, else written from the database. */
    private fun viewCopy(table: String, id: String, info: MediaBlobs.Info): File {
        val ctx = requireNotNull(context)
        val dir = viewDir(ctx).apply { mkdirs() }
        val ext = info.contentType?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)?.let { ".$it" }.orEmpty()
        val file = File(dir, "$table-$id$ext")
        if (!(file.isFile && file.length() == info.sizeBytes)) {
            val part = File(dir, file.name + ".part")
            val ok = FileOutputStream(part).use { out: OutputStream -> blobs.copy(table, id, out) }
            if (!ok || !part.renameTo(file)) {
                part.delete()
                throw FileNotFoundException()
            }
        }
        file.setLastModified(System.currentTimeMillis())
        prune(dir, keep = file)
        return file
    }

    /** Keeps only the [KEPT] most recently opened copies. */
    private fun prune(dir: File, keep: File) {
        dir.listFiles()?.filter { it != keep && !it.name.endsWith(".part") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEPT - 1)
            ?.forEach { it.delete() }
    }

    /**
     * The fetched file a `downloads/<id>` URI (a document) or a `remote/<key>` URI (media the database doesn't have yet,
     * see [RemoteMediaFiles]) names, when it is on the device; null for anything else.
     */
    private fun download(uri: Uri): File? {
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        return when (segments[0]) {
            DocumentDownloads.SEGMENT -> DocumentDownloads.file(requireNotNull(context), segments[1])
            RemoteMediaFiles.SEGMENT -> RemoteMediaFiles.file(requireNotNull(context), segments[1])
            else -> null
        }
    }

    /** A fetched document's type, by its name's extension. */
    private fun typeOf(file: File): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

    /** The table and row a URI names; null for anything else (an unknown table, or an id that isn't plain). */
    private fun target(uri: Uri): Pair<String, String>? {
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        val (table, id) = segments
        return if (table in MediaBlobs.TABLES && ID.matches(id)) table to id else null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        /** How many short-lived copies (videos, voice memos, documents) stay at once. */
        private const val KEPT = 3

        /** Row ids are UUIDs (letters, digits and dashes). */
        private val ID = Regex("^[A-Za-z0-9-]{1,64}$")

        /** Where the short-lived copies go (cleared on sign-out with the rest of the media). */
        fun viewDir(context: android.content.Context): File = File(context.cacheDir, "media-view")
    }
}
