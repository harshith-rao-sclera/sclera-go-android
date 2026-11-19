package com.sclera.sclerago.services.tagging

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import java.io.ByteArrayOutputStream

/**
 * Reads a QR code or a barcode from the camera's frames with ML Kit's bundled model. A code counts once it is read the same
 * [STEADY_FRAMES] frames in a row, so a half-seen code is not taken. A QR code also keeps the frame it was read from as a JPEG.
 * Nothing read is logged. [onScan] is called once, on the analysis thread.
 */
class TagFrameAnalyzer(private val kind: TagKind, private val onScan: (TagScan) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(if (kind == TagKind.QR) QR_FORMATS else BARCODE_FORMATS).build(),
    )
    private var last: String? = null
    private var steady = 0
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) return image.close()
        val media = image.image ?: return image.close()
        scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> onFrame(codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }, image) }
            .addOnCompleteListener { image.close() }
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
        const val QR_FORMATS = Barcode.FORMAT_QR_CODE
        const val BARCODE_FORMATS = Barcode.FORMAT_EAN_13 or Barcode.FORMAT_EAN_8 or Barcode.FORMAT_UPC_A or Barcode.FORMAT_UPC_E or
            Barcode.FORMAT_CODE_128 or Barcode.FORMAT_CODE_39 or Barcode.FORMAT_CODE_93 or Barcode.FORMAT_ITF or Barcode.FORMAT_CODABAR
    }
}
