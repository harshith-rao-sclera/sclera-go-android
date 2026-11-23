package com.sclera.sclerago.services.tagging

import androidx.camera.core.ImageProxy
import com.sclera.sclerago.domain.direction.LumaImage
import java.nio.ByteBuffer

/** The brightness plane of a camera frame, read in place: valid only until the frame is closed. */
class ImageProxyLuma(image: ImageProxy) : LumaImage {
    private val plane = image.planes[0]
    private val buffer: ByteBuffer = plane.buffer
    private val rowStride = plane.rowStride
    private val pixelStride = plane.pixelStride
    override val width = image.width
    override val height = image.height

    override fun get(x: Int, y: Int): Int = buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
}
