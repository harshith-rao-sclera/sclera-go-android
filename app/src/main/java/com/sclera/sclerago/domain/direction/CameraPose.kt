package com.sclera.sclerago.domain.direction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The camera's pose from the rotation-vector sensor. A pose is a 4x4 column-major matrix whose columns are the camera's X, Y and
 * Z axes in a Y-up world (X east, Y up, Z south); the camera looks along -Z, as in OpenGL. The camera sits at the origin.
 */
internal object CameraPose {
    /** What `SensorManager.getRotationMatrixFromVector` gives: a row-major 3x3 matrix. */
    fun rotationMatrixFromVector(v: DoubleArray): DoubleArray {
        val q1 = v[0]
        val q2 = v[1]
        val q3 = v[2]
        val q0 = if (v.size >= 4) v[3] else (1.0 - q1 * q1 - q2 * q2 - q3 * q3).let { if (it > 0) sqrt(it) else 0.0 }
        val sq1 = 2 * q1 * q1
        val sq2 = 2 * q2 * q2
        val sq3 = 2 * q3 * q3
        val q1q2 = 2 * q1 * q2
        val q3q0 = 2 * q3 * q0
        val q1q3 = 2 * q1 * q3
        val q2q0 = 2 * q2 * q0
        val q2q3 = 2 * q2 * q3
        val q1q0 = 2 * q1 * q0
        return doubleArrayOf(
            1 - sq2 - sq3, q1q2 - q3q0, q1q3 + q2q0,
            q1q2 + q3q0, 1 - sq1 - sq3, q2q3 - q1q0,
            q1q3 - q2q0, q2q3 + q1q0, 1 - sq1 - sq2,
        )
    }

    /**
     * The pose of the back camera for [attitude] (a rotation-vector reading) when the sensor is turned [sensorOrientationDeg] in the
     * device. Device axes go to east-north-up through the sensor's matrix, then to the Y-up world (X east, Y up, Z -north), so a
     * bearing taken from the pose is a magnetic-compass bearing and only the declination is left to add.
     */
    fun fromAttitude(attitude: DoubleArray, sensorOrientationDeg: Int): DoubleArray {
        val r = rotationMatrixFromVector(attitude)
        val so = Math.toRadians(sensorOrientationDeg.toDouble())
        val cs = cos(so)
        val sn = sin(so)
        fun toWorld(x: Double, y: Double, z: Double) = doubleArrayOf(
            r[0] * x + r[1] * y + r[2] * z,
            r[6] * x + r[7] * y + r[8] * z,
            -(r[3] * x + r[4] * y + r[5] * z),
        )
        val colX = toWorld(cs, -sn, 0.0)
        val colY = toWorld(sn, cs, 0.0)
        val colZ = toWorld(0.0, 0.0, 1.0)
        return doubleArrayOf(
            colX[0], colX[1], colX[2], 0.0,
            colY[0], colY[1], colY[2], 0.0,
            colZ[0], colZ[1], colZ[2], 0.0,
            0.0, 0.0, 0.0, 1.0,
        )
    }

    /** [v] in the camera's frame turned into the world's. */
    fun camToWorld(v: DoubleArray, m: DoubleArray) = doubleArrayOf(
        m[0] * v[0] + m[4] * v[1] + m[8] * v[2],
        m[1] * v[0] + m[5] * v[1] + m[9] * v[2],
        m[2] * v[0] + m[6] * v[1] + m[10] * v[2],
    )

    /** The camera's bearing (degrees) in the pose's own frame: 0 along -Z, turning toward +X. */
    fun cameraYaw(camPose: DoubleArray): Double = normalizeDegrees(Math.toDegrees(atan2(-camPose[8], camPose[10])))

    /** Degrees to add to a bearing in the pose's frame to get a compass bearing, or NaN when [attitude] is missing or points straight up or down. */
    fun northOffset(camPose: DoubleArray, attitude: DoubleArray?): Double {
        if (attitude == null) return Double.NaN
        val r = rotationMatrixFromVector(attitude)
        val east = -r[2]
        val north = -r[5]
        if (hypot(east, north) < 1e-5) return Double.NaN
        val heading = normalizeDegrees(Math.toDegrees(atan2(east, north)))
        return angleDifference(cameraYaw(camPose), heading)
    }
}
