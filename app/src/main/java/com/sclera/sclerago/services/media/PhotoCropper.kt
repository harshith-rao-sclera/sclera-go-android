package com.sclera.sclerago.services.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PhotoCropper"

/** The longest side a photo is read at to be cropped (sampled down while reading, so a huge one fits in memory). */
private const val MAX_SIDE = 4096

/** JPEG quality of the cropped copy. */
private const val QUALITY = 92

/**
 * The part of the photo at [source] inside [left], [top], [right] and [bottom] (fractions 0..1 of the upright photo, as
 * it is shown), saved as a new app-private JPEG in the captures folder; the original is left as it is. The photo is
 * turned upright from its EXIF orientation first, so the copy needs none. Null when it can't be read or written.
 */
suspend fun cropPhoto(context: Context, source: Uri, left: Float, top: Float, right: Float, bottom: Float): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val upright = readUpright(context, source) ?: run {
                Log.w(TAG, "Photo could not be read to crop")
                return@runCatching null
            }
            try {
                val x = (left.coerceIn(0f, 1f) * upright.width).toInt()
                val y = (top.coerceIn(0f, 1f) * upright.height).toInt()
                val w = ((right.coerceIn(0f, 1f) - left.coerceIn(0f, 1f)) * upright.width).toInt().coerceIn(1, upright.width - x)
                val h = ((bottom.coerceIn(0f, 1f) - top.coerceIn(0f, 1f)) * upright.height).toInt().coerceIn(1, upright.height - y)
                val cut = Bitmap.createBitmap(upright, x, y, w, h)
                try {
                    File(captureDir(context), "IMG_${stamp()}_crop.jpg").apply {
                        outputStream().use { cut.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
                    }
                } finally {
                    if (cut !== upright) cut.recycle()
                }
            } finally {
                upright.recycle()
            }
        }.onFailure { Log.w(TAG, "Photo could not be cropped: ${it.javaClass.simpleName}") }.getOrNull()
    }

/** [source] at most [MAX_SIDE] on its long side, turned upright from its EXIF orientation. */
private fun readUpright(context: Context, source: Uri): Bitmap? {
    val cr = context.contentResolver
    // Only measured here: a bounds-only read always returns null, so whether it worked is told by the size it found.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = cr.openInputStream(source) ?: return null
    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: return null
    var sample = 1
    while (longest / sample > MAX_SIDE) sample *= 2
    val bmp = cr.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    val degrees = runCatching { cr.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0 }.getOrDefault(0)
    if (degrees == 0) return bmp
    val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (turned !== bmp) bmp.recycle()
    return turned
}
