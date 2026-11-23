package com.sclera.sclerago.domain.direction

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** How well a frame's QR corners (top-left, top-right, bottom-right, bottom-left) describe a usable square. */
internal class QrFrameQuality(
    val minEdge: Double,
    val ratioTB: Double,
    val ratioLR: Double,
    val area: Double,
    val maxShift: Double,
    val convex: Boolean,
) {
    /** The smaller of the two opposite-edge ratios: 1 for a square seen head-on, smaller as the view gets more oblique. */
    val skewRatio: Double get() = min(ratioTB, ratioLR)

    /** A frame is taken as a sample only when it is convex, big enough, not too oblique and its corners did not move far when refined. */
    val isAcceptableForSampling: Boolean
        get() = convex && minEdge >= MIN_EDGE_PX && area >= MIN_AREA_PX2 && ratioTB >= MIN_EDGE_RATIO &&
            ratioLR >= MIN_EDGE_RATIO && maxShift <= MAX_SUBPIX_SHIFT_PX

    private companion object {
        const val MIN_EDGE_PX = 20.0
        const val MIN_AREA_PX2 = 400.0
        const val MIN_EDGE_RATIO = 0.3
        const val MAX_SUBPIX_SHIFT_PX = 8.0
    }
}

/** A plane's normal from the QR's vanishing points, in the camera's and the world's frame. */
internal class VpEstimate(val normalCam: DoubleArray, val normalWorld: DoubleArray)

internal object QrFrameGeometry {
    /** Edge lengths top, right, bottom, left of the quad in [p] (x0, y0 .. x3, y3). */
    private fun edges(p: DoubleArray) = doubleArrayOf(
        hypot(p[2] - p[0], p[3] - p[1]),
        hypot(p[4] - p[2], p[5] - p[3]),
        hypot(p[6] - p[4], p[7] - p[5]),
        hypot(p[0] - p[6], p[1] - p[7]),
    )

    /** The ratios (shorter / longer) of the top and bottom edges and of the left and right edges. */
    fun edgeRatios(p: DoubleArray): Pair<Double, Double> {
        val (top, right, bottom, left) = edges(p).toList()
        val maxTB = max(top, bottom)
        val maxLR = max(left, right)
        return Pair(if (maxTB > 0.0) min(top, bottom) / maxTB else 0.0, if (maxLR > 0.0) min(left, right) / maxLR else 0.0)
    }

    /** The quality of [refined] corners; [detected] are the corners as the detector gave them, to see how far refining moved them. */
    fun evaluate(detected: DoubleArray, refined: DoubleArray): QrFrameQuality {
        val (ratioTB, ratioLR) = edgeRatios(refined)
        val area = 0.5 * abs(
            (0 until 4).sumOf { i ->
                val j = (i + 1) % 4
                refined[2 * i] * refined[2 * j + 1] - refined[2 * j] * refined[2 * i + 1]
            },
        )
        var convex = true
        var sign = 0
        for (i in 0 until 4) {
            val a = (2 * i)
            val b = (2 * ((i + 1) % 4))
            val c = (2 * ((i + 2) % 4))
            val cross = (refined[b] - refined[a]) * (refined[c + 1] - refined[b + 1]) - (refined[b + 1] - refined[a + 1]) * (refined[c] - refined[b])
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s != 0) {
                if (sign == 0) sign = s else if (s != sign) {
                    convex = false
                    break
                }
            }
        }
        val maxShift = (0 until 4).maxOf { hypot(refined[2 * it] - detected[2 * it], refined[2 * it + 1] - detected[2 * it + 1]) }
        return QrFrameQuality(edges(refined).min(), ratioTB, ratioLR, area, maxShift, convex)
    }

    /**
     * The plane's normal from the vanishing points of the quad's opposite edges (top/bottom and left/right), or null when an edge
     * pair is parallel in the image or a ray is degenerate. The normal faces the camera.
     */
    fun normalViaVanishingPoint(p: DoubleArray, fx: Double, fy: Double, cx: Double, cy: Double, camPose: DoubleArray): VpEstimate? {
        val tl = doubleArrayOf(p[0], p[1], 1.0)
        val tr = doubleArrayOf(p[2], p[3], 1.0)
        val br = doubleArrayOf(p[4], p[5], 1.0)
        val bl = doubleArrayOf(p[6], p[7], 1.0)
        val vpX = v3Cross(v3Cross(tl, tr), v3Cross(bl, br))
        val vpY = v3Cross(v3Cross(tl, bl), v3Cross(tr, br))
        if (abs(vpX[2]) < 1e-6 || abs(vpY[2]) < 1e-6) return null
        val rayX = toCameraRay(vpX, fx, fy, cx, cy)
        val rayY = toCameraRay(vpY, fx, fy, cx, cy)
        if (v3Len(rayX) < 0.001 || v3Len(rayY) < 0.001) return null
        var normalCam = v3Normalize(v3Cross(rayX, rayY))
        if (normalCam[2] < 0) normalCam = v3Scale(normalCam, -1.0)
        return VpEstimate(normalCam, CameraPose.camToWorld(normalCam, camPose))
    }

    private fun toCameraRay(v: DoubleArray, fx: Double, fy: Double, cx: Double, cy: Double): DoubleArray {
        val w = v[2]
        return v3Normalize(doubleArrayOf((v[0] - w * cx) / fx, -((v[1] - w * cy) / fy), -w))
    }
}
