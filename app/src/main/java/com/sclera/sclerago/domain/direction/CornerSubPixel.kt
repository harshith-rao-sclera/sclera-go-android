package com.sclera.sclerago.domain.direction

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/**
 * Sub-pixel refinement of a QR's four corners on the frame's luma, as OpenCV's `cornerSubPix` does it: each corner moves to where the
 * image gradients in an 11x11 window (weighted by a gaussian) are orthogonal to the offsets, for at most 20 iterations or until it moves
 * less than 0.03 px. A corner too near the border to take a window, or one that wanders out of the window, keeps its first position.
 */
internal object CornerSubPixel {
    private const val RADIUS = 5
    private const val MAX_ITERATIONS = 20
    private const val EPSILON = 0.03

    /** The square of the double epsilon, as in OpenCV. */
    private const val MIN_DETERMINANT = 4.93e-32

    private val MASK = Array(2 * RADIUS + 1) { i ->
        DoubleArray(2 * RADIUS + 1) { j ->
            val y = (i - RADIUS).toDouble() / RADIUS
            val x = (j - RADIUS).toDouble() / RADIUS
            exp(-y * y) * exp(-x * x)
        }
    }

    /** The refined corners (x0, y0 .. x3, y3); [pts] itself when any corner is too near the border. */
    fun refine(image: LumaImage, pts: FloatArray): FloatArray {
        for (i in 0 until 4) {
            val x = pts[2 * i]
            val y = pts[2 * i + 1]
            if (x < RADIUS || y < RADIUS || x >= image.width - RADIUS || y >= image.height - RADIUS) return pts
        }
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val (x, y) = refineCorner(image, pts[2 * i].toDouble(), pts[2 * i + 1].toDouble())
            out[2 * i] = x.toFloat()
            out[2 * i + 1] = y.toFloat()
        }
        return out
    }

    private fun refineCorner(image: LumaImage, x0: Double, y0: Double): Pair<Double, Double> {
        var cx = x0
        var cy = y0
        for (iteration in 0 until MAX_ITERATIONS) {
            var a = 0.0
            var b = 0.0
            var c = 0.0
            var bb1 = 0.0
            var bb2 = 0.0
            for (i in 0..2 * RADIUS) {
                val py = (i - RADIUS).toDouble()
                for (j in 0..2 * RADIUS) {
                    val px = (j - RADIUS).toDouble()
                    val m = MASK[i][j]
                    val gx = sample(image, cx + px + 1, cy + py) - sample(image, cx + px - 1, cy + py)
                    val gy = sample(image, cx + px, cy + py + 1) - sample(image, cx + px, cy + py - 1)
                    val gxx = gx * gx * m
                    val gxy = gx * gy * m
                    val gyy = gy * gy * m
                    a += gxx
                    b += gxy
                    c += gyy
                    bb1 += gxx * px + gxy * py
                    bb2 += gxy * px + gyy * py
                }
            }
            val det = a * c - b * b
            if (abs(det) <= MIN_DETERMINANT) break
            val scale = 1.0 / det
            val nx = cx + c * scale * bb1 - b * scale * bb2
            val ny = cy - b * scale * bb1 + a * scale * bb2
            val err = (nx - cx) * (nx - cx) + (ny - cy) * (ny - cy)
            cx = nx
            cy = ny
            if (cx < 0 || cx >= image.width || cy < 0 || cy >= image.height) break
            if (err <= EPSILON * EPSILON) break
        }
        return if (abs(cx - x0) > RADIUS || abs(cy - y0) > RADIUS) Pair(x0, y0) else Pair(cx, cy)
    }

    /** Bilinear brightness at ([x], [y]); the edge pixel repeats beyond the frame. */
    private fun sample(image: LumaImage, x: Double, y: Double): Double {
        val fx = floor(x)
        val fy = floor(y)
        val tx = x - fx
        val ty = y - fy
        val x0 = fx.toInt().coerceIn(0, image.width - 1)
        val x1 = (fx.toInt() + 1).coerceIn(0, image.width - 1)
        val y0 = fy.toInt().coerceIn(0, image.height - 1)
        val y1 = (fy.toInt() + 1).coerceIn(0, image.height - 1)
        val top = image[x0, y0] * (1 - tx) + image[x1, y0] * tx
        val bottom = image[x0, y1] * (1 - tx) + image[x1, y1] * tx
        return top * (1 - ty) + bottom * ty
    }
}
