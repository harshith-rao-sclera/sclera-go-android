package com.sclera.sclerago.services.media

import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.Media
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "Unknown size"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

fun timeLabel(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at))

fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

fun captureDir(context: Context): File = File(context.filesDir, "captures").apply { mkdirs() }

/** Reads display names, sizes and types for documents returned by the file manager. */
suspend fun describeFiles(context: Context, uris: List<Uri>): List<ImportedFile> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    uris.map { uri ->
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name = uri.lastPathSegment ?: "File"
        var size = -1L
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        ImportedFile(uri.toString(), uri.toString(), name, size, cr.getType(uri))
    }
}

/** Turns photo-picker results into [Media], reading video durations. */
suspend fun describeVisualMedia(context: Context, uris: List<Uri>): List<Media> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    uris.mapIndexed { i, uri ->
        runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val video = cr.getType(uri)?.startsWith("video/") == true
        val dur = if (video) runCatching {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(context, uri)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally { release() }
            }
        }.getOrDefault(0L) else 0L
        Media("pick-${System.currentTimeMillis()}-$i", uri.toString(), video, dur)
    }
}

object MediaLoader {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(uri: String, maxPx: Int): ImageBitmap? = cache.get("$uri@$maxPx")?.asImageBitmap()

    suspend fun load(context: Context, uri: String, video: Boolean, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return@withContext it.asImageBitmap() }
        val bmp = runCatching { if (video) videoFrame(context, Uri.parse(uri), maxPx) else image(context, Uri.parse(uri), maxPx) }
            .getOrNull() ?: return@withContext null
        cache.put(key, bmp)
        bmp.asImageBitmap()
    }

    /** Web photos are downloaded once into the app cache, then read from there (works offline afterwards). */
    private fun remoteFile(context: Context, url: String): File? {
        val dir = File(context.cacheDir, "web-photos").apply { mkdirs() }
        val f = File(dir, Integer.toHexString(url.hashCode()) + "-" + url.length)
        if (f.length() > 0) return f
        val tmp = File(dir, f.name + ".part")
        return runCatching {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("User-Agent", "ScleraGo/1.0 (Android; asset onboarding demo)")
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            try {
                if (c.responseCode !in 200..299) return@runCatching null
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                c.disconnect()
            }
            if (tmp.renameTo(f)) f else null
        }.getOrNull()
    }

    private fun open(context: Context, uri: Uri): java.io.InputStream? =
        if (uri.scheme == "http" || uri.scheme == "https") remoteFile(context, uri.toString())?.inputStream()
        else context.contentResolver.openInputStream(uri)

    private fun image(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = open(context, uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching { open(context, uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 }.getOrDefault(0)
        return if (rotation == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }

    private fun videoFrame(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val frame = r.getFrameAtTime(0) ?: return null
            val scale = maxPx.toFloat() / maxOf(frame.width, frame.height)
            return if (scale >= 1f) frame
            else Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
        } finally {
            r.release()
        }
    }
}

/** Files picked in the system file manager, as attachments: photos/videos stay media, anything else a document. */
suspend fun describeAttachments(context: Context, uris: List<Uri>): List<Media> {
    val files = describeFiles(context, uris)
    val stamp = System.currentTimeMillis()
    return files.mapIndexed { i, f ->
        val mime = f.mime ?: ""
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            describeVisualMedia(context, listOf(Uri.parse(f.uri))).first().copy(id = "file-$stamp-$i")
        } else {
            Media("file-$stamp-$i", f.uri, video = false, fileName = f.name)
        }
    }
}
