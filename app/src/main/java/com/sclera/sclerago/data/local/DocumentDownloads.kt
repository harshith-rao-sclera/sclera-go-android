package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.data.offline.MediaBlobs
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-lived copies of an asset's documents fetched from the server to be opened (documents are not synced, so the
 * device keeps none of its own). Each goes in app-private cache as `<dir>/<id>/<file name>` and is opened through the
 * app's media provider (`content://<app>.media/downloads/<id>`), so another app can read it only through the grant on
 * the intent that opens it. Only the last few stay; all go when the app starts and on sign-out.
 */
@Singleton
class DocumentDownloads @Inject constructor(@ApplicationContext private val context: Context) : UserDataCleaner {

    /**
     * Writes [input] as document [id] named [filename] (made safe for a file name) and returns where it is opened.
     * A partial file never stays: it is written aside and moved into place only once complete.
     */
    fun keep(id: String, filename: String, input: InputStream): String {
        if (!ID.matches(id)) throw FileNotFoundException("not a document id")
        val root = dir(context).apply { mkdirs() }
        val folder = File(root, id)
        folder.deleteRecursively()
        folder.mkdirs()
        val file = File(folder, safeName(filename))
        val part = File(root, "$id.part")
        try {
            part.outputStream().use { out -> input.copyTo(out) }
            if (!part.renameTo(file)) throw FileNotFoundException()
        } catch (e: Exception) {
            part.delete()
            folder.deleteRecursively()
            throw e
        }
        folder.setLastModified(System.currentTimeMillis())
        prune(root, keep = folder)
        return "content://${MediaBlobs.authority(context)}/$SEGMENT/$id"
    }

    override fun clear() {
        dir(context).deleteRecursively()
    }

    /** Keeps only the [KEPT] most recent documents. */
    private fun prune(root: File, keep: File) {
        root.listFiles()?.filter { it.isDirectory && it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEPT - 1)
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** The provider path the copies are opened under. */
        const val SEGMENT = "downloads"

        /** How many fetched documents stay at once. */
        private const val KEPT = 3

        /** The server's ids (UUIDs: letters, digits and dashes). */
        private val ID = Regex("^[A-Za-z0-9-]{1,64}$")

        /** At most this many characters of the server's file name are kept. */
        private const val MAX_NAME = 120

        /** Where the copies go. */
        fun dir(context: Context): File = File(context.cacheDir, "document-view")

        /** The file of document [id], or null when it isn't on the device (or [id] isn't a plain id). */
        fun file(context: Context, id: String): File? =
            if (!ID.matches(id)) null else File(dir(context), id).listFiles()?.firstOrNull { it.isFile }

        /** The server's file name as a plain name: no folders, no unusual characters, not too long. */
        internal fun safeName(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\').replace(UNSAFE, "_").trim('.', ' ').take(MAX_NAME).ifBlank { "document" }

        private val UNSAFE = Regex("[^A-Za-z0-9._ ()-]")
    }
}
