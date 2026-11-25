package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.data.offline.MediaBlobs
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.services.media.MediaLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies of asset media fetched from the server because the offline database doesn't have the file yet (the sync hasn't
 * brought it down). Each goes in app-private cache as `<dir>/<area>/<key>/<file name>` and is opened through the app's
 * media provider (`content://<app>.media/remote/<key>`), so another app can read one only through the grant on the intent
 * that opens it. Never on external storage.
 *
 * Only photos are cached: they are kept and reused (the oldest go once they pass [MAX_TOTAL_BYTES]). A video, voice memo or
 * document is fetched each time it is opened and isn't reused; only the last [KEPT_OPEN] stay, so the one on screen can
 * still be played or saved. All of them go when the app starts and on sign-out (with the pictures decoded from them).
 */
@Singleton
class RemoteMediaFiles @Inject constructor(@ApplicationContext private val context: Context) : UserDataCleaner {

    /** Where the cached photo kept as [key] is opened (it then counts as just used), or null when there is none. */
    fun uriOf(key: String): String? = fileIn(cacheDir(context), key)?.let {
        it.parentFile?.setLastModified(System.currentTimeMillis())
        uri(key)
    }

    /**
     * Writes [input] as [key] named [filename] (made safe for a file name) and returns where it is opened. [cache]: a photo,
     * kept for reuse ([uriOf]); anything else stays only while it is among the last [KEPT_OPEN] opened. A file over
     * [MAX_FILE_BYTES] is refused; a partial file never stays (it is written aside and moved into place once complete).
     */
    fun keep(key: String, filename: String, input: InputStream, cache: Boolean): String {
        if (!KEY.matches(key)) throw FileNotFoundException("not a media key")
        val root = (if (cache) cacheDir(context) else openDir(context)).apply { mkdirs() }
        val folder = File(root, key)
        val part = File(root, "$key.part")
        try {
            part.outputStream().use { out ->
                val buf = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    // Refused as the server's fault (not a dropped connection), so it isn't fetched again.
                    if (total > MAX_FILE_BYTES) throw OnlineException(OnlineError.Server)
                    out.write(buf, 0, n)
                }
            }
            folder.deleteRecursively()
            folder.mkdirs()
            if (!part.renameTo(File(folder, DocumentDownloads.safeName(filename)))) throw FileNotFoundException()
        } catch (e: Exception) {
            part.delete()
            folder.deleteRecursively()
            throw e
        }
        folder.setLastModified(System.currentTimeMillis())
        if (cache) prune(root, keep = folder) else pruneOpen(root, keep = folder)
        return uri(key)
    }

    override fun clear() {
        dir(context).deleteRecursively()
        // The pictures read from these copies are held in memory too.
        MediaLoader.clear()
    }

    private fun uri(key: String) = "content://${MediaBlobs.authority(context)}/$SEGMENT/$key"

    /** Keeps the most recent photos, dropping the oldest while all of them together pass [MAX_TOTAL_BYTES]. */
    private fun prune(root: File, keep: File) {
        var total = 0L
        root.listFiles()?.filter { it.isDirectory }
            ?.sortedByDescending { if (it == keep) Long.MAX_VALUE else it.lastModified() }
            ?.forEach { folder ->
                total += folder.listFiles()?.sumOf { it.length() } ?: 0L
                if (total > MAX_TOTAL_BYTES && folder != keep) folder.deleteRecursively()
            }
    }

    /** Keeps only the [KEPT_OPEN] most recently opened videos, voice memos and documents. */
    private fun pruneOpen(root: File, keep: File) {
        root.listFiles()?.filter { it.isDirectory && it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEPT_OPEN - 1)
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** The provider path the copies are opened under. */
        const val SEGMENT = "remote"

        /** The largest file fetched (the app's own import limit). */
        private const val MAX_FILE_BYTES = MAX_IMPORT_BYTES

        /** How much the cached photos may take together before the oldest go. */
        private const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

        /** How many fetched videos, voice memos and documents stay at once (the one on screen, and the one before it). */
        private const val KEPT_OPEN = 2

        private const val BUFFER = 64 * 1024

        /** A media id (UUID) and which version of it: letters, digits and dashes. */
        private val KEY = Regex("^[A-Za-z0-9-]{1,80}$")

        /** Where the copies go (both areas). */
        fun dir(context: Context): File = File(context.cacheDir, "media-remote")

        /** The photos, kept for reuse. */
        private fun cacheDir(context: Context) = File(dir(context), "images")

        /** The videos, voice memos and documents being opened. */
        private fun openDir(context: Context) = File(dir(context), "open")

        /** The file kept as [key] in either area, or null when it isn't on the device (or [key] isn't plain). */
        fun file(context: Context, key: String): File? = fileIn(cacheDir(context), key) ?: fileIn(openDir(context), key)

        private fun fileIn(root: File, key: String): File? =
            if (!KEY.matches(key)) null else File(root, key).listFiles()?.firstOrNull { it.isFile }
    }
}
