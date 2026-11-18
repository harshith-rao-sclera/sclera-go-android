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

    /** The photo at [uri] as an [InputImage]: scaled down to at most [MAX_SIDE] and turned per its EXIF orientation. */
    private fun decode(uri: Uri): InputImage? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap: Bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        return InputImage.fromBitmap(bitmap, rotation)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        /** Big enough for a nameplate's small print and a label's code; small enough to read quickly. */
        const val MAX_SIDE = 2048
    }
}
