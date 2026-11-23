package com.sclera.sclerago.domain.direction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A square's pose in the camera frame (x right, y down, z forward): [rotation] row-major 3x3, [translation], and how far its corners project from the ones seen. */
internal class PlanarPose(val rotation: DoubleArray, val translation: DoubleArray, val reprojectionErrorPx: Double)

/**
 * The pose of a QR code's square from its four corners (top-left, top-right, bottom-right, bottom-left), in pure Kotlin. It stands
 * in for OpenCV's `solvePnPGeneric(SOLVEPNP_IPPE_SQUARE)`: the pose from the corners' homography, refined by Levenberg-Marquardt
 * on the reprojection error, plus the second pose a flat square always allows (its normal mirrored about the line of sight).
 * The square has side 1 and lies in z = 0, centred on the origin.
 */
internal object QrPoseSolver {
    private const val HALF_SIDE = 0.5
    private const val MAX_ITERATIONS = 40
    private const val STEP = 1e-6
    private const val SAME_NORMAL = 0.99999
    private val SQUARE = arrayOf(
        doubleArrayOf(-HALF_SIDE, HALF_SIDE),
        doubleArrayOf(HALF_SIDE, HALF_SIDE),
        doubleArrayOf(HALF_SIDE, -HALF_SIDE),
        doubleArrayOf(-HALF_SIDE, -HALF_SIDE),
    )

    private class Pose(val r: DoubleArray, val t: DoubleArray)

    private class Camera(val pts: DoubleArray, val fx: Double, val fy: Double, val cx: Double, val cy: Double) {
        /** Reprojection residuals (8), or null when a corner falls behind the camera. */
        fun residuals(p: Pose): DoubleArray? {
            val out = DoubleArray(8)
            for (i in 0 until 4) {
                val x = SQUARE[i][0]
                val y = SQUARE[i][1]
                val z = p.r[6] * x + p.r[7] * y + p.t[2]
                if (z <= 1e-9) return null
                out[2 * i] = fx * (p.r[0] * x + p.r[1] * y + p.t[0]) / z + cx - pts[2 * i]
                out[2 * i + 1] = fy * (p.r[3] * x + p.r[4] * y + p.t[1]) / z + cy - pts[2 * i + 1]
            }
            return out
        }

        fun cost(p: Pose): Double = residuals(p)?.sumOf { it * it } ?: Double.POSITIVE_INFINITY
    }

    /** The poses (one or two) that fit [pts] (x0, y0 .. x3, y3 in pixels) for a camera with the given intrinsics; empty when none does. */
    fun solve(pts: DoubleArray, fx: Double, fy: Double, cx: Double, cy: Double): List<PlanarPose> {
        val camera = Camera(pts, fx, fy, cx, cy)
        val h = homography(camera) ?: return emptyList()
        val start = initialPose(h) ?: return emptyList()
        val first = refine(start, camera) ?: return emptyList()
        val poses = mutableListOf(first)
        flipped(first)?.let { refine(it, camera) }?.takeIf { normalDot(it, first) < SAME_NORMAL }?.let { poses += it }
        return poses.map { PlanarPose(it.r, it.t, sqrt(camera.cost(it) / 4.0)) }
    }

    private fun homography(c: Camera): DoubleArray? {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = SQUARE[i][0]
            val y = SQUARE[i][1]
            val u = (c.pts[2 * i] - c.cx) / c.fx
            val v = (c.pts[2 * i + 1] - c.cy) / c.fy
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        val s = solveLinear(a, 8) ?: return null
        return doubleArrayOf(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], 1.0)
    }

    private fun initialPose(h: DoubleArray): Pose? {
        val h1 = doubleArrayOf(h[0], h[3], h[6])
        val h2 = doubleArrayOf(h[1], h[4], h[7])
        val norm = v3Len(h1) + v3Len(h2)
        if (norm < 1e-12) return null
        val lambda = 2.0 / norm
        var r1 = v3Scale(h1, lambda)
        var r2 = v3Scale(h2, lambda)
        var t = v3Scale(doubleArrayOf(h[2], h[5], h[8]), lambda)
        if (t[2] < 0) {
            r1 = v3Scale(r1, -1.0)
            r2 = v3Scale(r2, -1.0)
            t = v3Scale(t, -1.0)
        }
        val c1 = v3Normalize(r1)
        val c2 = v3Normalize(v3Sub(r2, v3Scale(c1, v3Dot(r2, c1))))
        if (v3Len(c1) < 0.5 || v3Len(c2) < 0.5) return null
        val c3 = v3Cross(c1, c2)
        return Pose(doubleArrayOf(c1[0], c2[0], c3[0], c1[1], c2[1], c3[1], c1[2], c2[2], c3[2]), t)
    }

    private fun normal(p: Pose) = doubleArrayOf(p.r[2], p.r[5], p.r[8])

    private fun normalDot(a: Pose, b: Pose) = v3Dot(normal(a), normal(b))

    /** The plane's normal mirrored about the line of sight to the square: the pose a perspective view cannot tell from [p]. */
    private fun flipped(p: Pose): Pose? {
        val n = normal(p)
        val d = v3Normalize(p.t)
        val m = v3Sub(v3Scale(d, 2 * v3Dot(n, d)), n)
        val axis = v3Cross(n, m)
        val s = v3Len(axis)
        if (s < 1e-6) return null
        val q = rotationFromVector(v3Scale(axis, atan2(s, v3Dot(n, m)) / s))
        return Pose(multiply(q, p.r), p.t.copyOf())
    }

    private fun refine(start: Pose, c: Camera): Pose? {
        var pose = start
        var cost = c.cost(pose)
        if (cost.isInfinite()) return null
        var lambda = 1e-3
        for (iteration in 0 until MAX_ITERATIONS) {
            val f = c.residuals(pose) ?: return null
            val jac = Array(6) { k ->
                val fp = c.residuals(apply(pose, DoubleArray(6).also { it[k] = STEP })) ?: return pose
                DoubleArray(8) { (fp[it] - f[it]) / STEP }
            }
            val jtj = Array(6) { i -> DoubleArray(6) { j -> (0 until 8).sumOf { jac[i][it] * jac[j][it] } } }
            val jtf = DoubleArray(6) { i -> (0 until 8).sumOf { jac[i][it] * f[it] } }
            var accepted = false
            for (attempt in 0 until 10) {
                val aug = Array(6) { i ->
                    DoubleArray(7) { j -> if (j == 6) -jtf[i] else jtj[i][j] + if (i == j) lambda * jtj[i][i] + 1e-12 else 0.0 }
                }
                val delta = solveLinear(aug, 6) ?: break
                val candidate = apply(pose, delta)
                val next = c.cost(candidate)
                if (next < cost) {
                    val gain = cost - next
                    pose = candidate
                    cost = next
                    lambda = maxOf(lambda / 10, 1e-12)
                    accepted = true
                    if (gain < 1e-12 || delta.maxOf { abs(it) } < 1e-10) return pose
                    break
                }
                lambda *= 10
            }
            if (!accepted) break
        }
        return pose
    }

    private fun apply(p: Pose, d: DoubleArray) = Pose(
        multiply(rotationFromVector(doubleArrayOf(d[0], d[1], d[2])), p.r),
        doubleArrayOf(p.t[0] + d[3], p.t[1] + d[4], p.t[2] + d[5]),
    )

    /** Rodrigues formula: the row-major rotation by the length of [w] about [w]. */
    private fun rotationFromVector(w: DoubleArray): DoubleArray {
        val theta = v3Len(w)
        if (theta < 1e-12) return doubleArrayOf(1.0, -w[2], w[1], w[2], 1.0, -w[0], -w[1], w[0], 1.0)
        val k = v3Scale(w, 1.0 / theta)
        val c = cos(theta)
        val s = sin(theta)
        val v = 1 - c
        return doubleArrayOf(
            c + k[0] * k[0] * v, k[0] * k[1] * v - k[2] * s, k[0] * k[2] * v + k[1] * s,
            k[1] * k[0] * v + k[2] * s, c + k[1] * k[1] * v, k[1] * k[2] * v - k[0] * s,
            k[2] * k[0] * v - k[1] * s, k[2] * k[1] * v + k[0] * s, c + k[2] * k[2] * v,
        )
    }

    private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { idx ->
        val row = idx / 3
        val col = idx % 3
        a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
    }

    /** Gauss-Jordan on the [n] x ([n] + 1) augmented matrix [a] with partial pivoting; null when singular. */
    private fun solveLinear(a: Array<DoubleArray>, n: Int): DoubleArray? {
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
            if (abs(a[pivot][col]) < 1e-14) return null
            a[col] = a[pivot].also { a[pivot] = a[col] }
            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col] / a[col][col]
                if (factor != 0.0) for (k in col..n) a[row][k] -= factor * a[col][k]
            }
        }
        return DoubleArray(n) { a[it][n] / a[it][it] }
    }
}
