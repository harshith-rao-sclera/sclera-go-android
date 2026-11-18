package com.sclera.sclerago.services.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.photos.PhotoReader
import com.sclera.sclerago.domain.photos.PhotoReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads photos on the device with ML Kit's bundled text and barcode models (no connection or download needed). The
 * photo is decoded at most [MAX_SIDE] pixels on its long side, the right way up, and read for text lines and QR/barcodes
 * at the same time. Nothing leaves the device; nothing read is logged.
 */
@Singleton
class MlKitPhotoReader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PhotoReader {
    private val text by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val codes by lazy { BarcodeScanning.getClient() }

    override suspend fun read(uri: String): PhotoReading? {
        val image = withContext(io) { runCatching { decode(Uri.parse(uri)) }.getOrNull() } ?: return null
        return try {
            coroutineScope {
                val lines = async { text.process(image).await().textBlocks.flatMap { b -> b.lines.map { it.text } } }
                val found = async { codes.process(image).await().isNotEmpty() }
                PhotoReading(lines.await(), found.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The photo at [uri] as an [InputImage]: scaled down to at most [MAX_SIDE] and turned per its EXIF orientation. It is
     * read once and decoded from that copy (a photo kept in the offline database comes through a pipe that can't be read
     * again). Reading only the size returns no bitmap, so only the size it fills in is checked.
     */
    private fun decode(uri: Uri): InputImage? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readAtMost(it, MAX_BYTES) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = runCatching { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
        return InputImage.fromBitmap(bitmap, rotation)
    }

    /** All of [input], or null when it is longer than [max] bytes. */
    private fun readAtMost(input: java.io.InputStream, max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        /** Big enough for a nameplate's small print and a label's code; small enough to read quickly. */
        const val MAX_SIDE = 2048

        /** The largest photo read (a camera photo is a few MB). */
        const val MAX_BYTES = 40L * 1024 * 1024
    }
}
