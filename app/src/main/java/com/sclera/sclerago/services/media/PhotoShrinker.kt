package com.sclera.sclerago.services.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes a photo fit the size an asset keeps: scaled down (at most [MAX_SIDE] pixels on its long side to start with),
 * turned the right way up from its EXIF orientation, and saved as JPEG at lower quality and then smaller sizes until
 * it is under the limit.
 */
@Singleton
class PhotoShrinker @Inject constructor(@ApplicationContext private val context: Context) {

    /** [source] as an app-private JPEG of at most [maxBytes], or null when it can't be read or made that small. */
    suspend fun shrink(source: Uri, maxBytes: Long): File? = withContext(Dispatchers.IO) {
        runCatching {
            var bitmap = upright(decode(source) ?: return@runCatching null, rotation(source))
            try {
                repeat(ROUNDS) {
                    for (quality in QUALITIES) {
                        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                        if (bytes.size <= maxBytes) {
                            return@runCatching File(captureDir(context), "IMG_${stamp()}_small.jpg").apply { writeBytes(bytes) }
                        }
                    }
                    val next = Bitmap.createScaledBitmap(bitmap, (bitmap.width * STEP).toInt().coerceAtLeast(1), (bitmap.height * STEP).toInt().coerceAtLeast(1), true)
                    if (next !== bitmap) bitmap.recycle()
                    bitmap = next
                }
                null
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }

    /** [source] decoded at about [MAX_SIDE] on its long side (sampled down while reading, so a huge photo fits in memory). */
    private fun decode(source: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longest = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: return null
        var sample = 1
        while (longest / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val side = maxOf(decoded.width, decoded.height)
        if (side <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / side
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** How far [source] has to turn to stand upright, from its EXIF orientation (0 when it has none). */
    private fun rotation(source: Uri): Float = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    private fun upright(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private companion object {
        /** Long side to start from: plenty to read a nameplate. */
        const val MAX_SIDE = 4096

        /** JPEG qualities tried at each size, best first. */
        val QUALITIES = listOf(90, 80, 70)

        /** Each further round scales the photo to this share of its size. */
        const val STEP = 0.75f

        const val ROUNDS = 5
    }
}
