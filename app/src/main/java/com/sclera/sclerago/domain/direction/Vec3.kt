package com.sclera.sclerago.domain.direction

import kotlin.math.sqrt

internal fun v3Dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
internal fun v3Len(v: DoubleArray) = sqrt(v3Dot(v, v))
internal fun v3Scale(v: DoubleArray, s: Double) = doubleArrayOf(v[0] * s, v[1] * s, v[2] * s)
internal fun v3Sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
internal fun v3Cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0],
)

/** A unit vector, or zeros for one too short to have a direction. */
internal fun v3Normalize(v: DoubleArray): DoubleArray {
    val l = v3Len(v)
    return if (l < 1e-6) doubleArrayOf(0.0, 0.0, 0.0) else v3Scale(v, 1.0 / l)
}

/** [angle] in 0 until 360 degrees. */
internal fun normalizeDegrees(angle: Double): Double {
    var a = angle % 360.0
    if (a < 0) a += 360.0
    return a
}

/** The signed shortest turn from [a] to [b], in -180..180 degrees. */
internal fun angleDifference(a: Double, b: Double): Double {
    var d = b - a
    while (d > 180) d -= 360.0
    while (d < -180) d += 360.0
    return d
}
