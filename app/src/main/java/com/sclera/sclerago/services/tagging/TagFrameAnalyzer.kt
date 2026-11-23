package com.sclera.sclerago.services.tagging

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sclera.sclerago.domain.direction.CornerSubPixel
import com.sclera.sclerago.domain.direction.QrDirectionSession
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

/**
 * Reads a QR code or a barcode from the camera frames with the bundled model of ML Kit. A barcode counts once it is read the same
 * [STEADY_FRAMES] frames in a row, so a half-seen code is not taken. A QR code also keeps the frame it was read from as a JPEG.
 *
 * With a [meter] that can measure (the device has a rotation-vector sensor) a QR code counts once its direction has been measured
 * over several frames: each frame is read as the sensor delivered it (no rotation, so the corners are in the frame pixels), the
 * corners are refined to sub-pixel and handed to the meter session, and the scan carries the averaged direction. [zoomRatio] is the
 * camera zoom, which narrows the field of view the intrinsics describe. Without a meter, a QR code is read like a barcode.
 * Nothing read is logged. [onScan] is called once, on a detector thread.
 */
class TagFrameAnalyzer(
    private val kind: TagKind,
    private val meter: QrDirectionMeter? = null,
    private val zoomRatio: () -> Float = { 1f },
    private val onScan: (TagScan) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(if (kind == TagKind.QR) QR_FORMATS else BARCODE_FORMATS).build(),
    )
    private val measuring = kind == TagKind.QR && meter?.isAvailable == true
    private var last: String? = null
    private var steady = 0

    @Volatile
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) return image.close()
        val media = image.image ?: return image.close()
        val session = if (measuring) meter?.session else null
        val frame = session?.beginFrame(image.width, image.height, zoomRatio())
        if (session != null && frame == null) return image.close()
        val rotation = if (session != null) 0 else image.imageInfo.rotationDegrees
        scanner.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener(DIRECT) { codes ->
                if (session != null && frame != null) onMeasuredFrame(session, frame, codes, image)
                else onFrame(codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }, image)
            }
            .addOnCompleteListener(DIRECT) { image.close() }
    }

    private fun onMeasuredFrame(session: QrDirectionSession, frame: QrDirectionSession.Frame, codes: List<Barcode>, image: ImageProxy) {
        val code = codes.firstOrNull { !it.rawValue.isNullOrBlank() && it.cornerPoints?.size == CORNERS }
        val text = code?.rawValue
        val points = code?.cornerPoints
        if (text == null || points == null) return session.onNoQr()
        val detected = FloatArray(CORNERS * 2) { if (it % 2 == 0) points[it / 2].x.toFloat() else points[it / 2].y.toFloat() }
        val refined = CornerSubPixel.refine(ImageProxyLuma(image), detected)
        val direction = session.onQr(frame, text, detected, refined) ?: return
        if (done) return
        done = true
        onScan(TagScan(text, jpeg(image), direction))
    }

    private fun onFrame(text: String?, image: ImageProxy) {
        if (text == null || text != last) {
            last = text
            steady = if (text == null) 0 else 1
            return
        }
        if (++steady < STEADY_FRAMES || done) return
        done = true
        onScan(TagScan(text, if (kind == TagKind.QR) jpeg(image) else null))
    }

    private fun jpeg(image: ImageProxy): ByteArray? = runCatching {
        val upright = Bitmap.createBitmap(
            image.toBitmap(), 0, 0, image.width, image.height, Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true,
        )
        ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
    }.getOrNull()

    fun close() = scanner.close()

    private companion object {
        const val STEADY_FRAMES = 3
        const val JPEG_QUALITY = 80
        const val CORNERS = 4
        val DIRECT = Executor { it.run() }
        const val QR_FORMATS = Barcode.FORMAT_QR_CODE
        const val BARCODE_FORMATS = Barcode.FORMAT_EAN_13 or Barcode.FORMAT_EAN_8 or Barcode.FORMAT_UPC_A or Barcode.FORMAT_UPC_E or
            Barcode.FORMAT_CODE_128 or Barcode.FORMAT_CODE_39 or Barcode.FORMAT_CODE_93 or Barcode.FORMAT_ITF or Barcode.FORMAT_CODABAR
    }
}
