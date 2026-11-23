package com.sclera.sclerago.domain.direction

/** One reading of the rotation-vector sensor: the vector's 3 or 4 components and when it was taken (elapsed-realtime nanoseconds). */
class TimedAttitude(val elapsedRealtimeNs: Long, val values: DoubleArray)

/** The device's attitude: the latest rotation-vector reading and how far the sensor says it can be trusted. */
interface AttitudeSource {
    fun latest(): TimedAttitude?

    /** `SensorManager.SENSOR_STATUS_*`: 0 unreliable, 1 low, 2 medium, 3 high. */
    val accuracy: Int
}

/** The camera's focal length and principal point, in pixels of the frame it applies to. */
class CameraIntrinsics(val fx: Double, val fy: Double, val cx: Double, val cy: Double)

/** The back camera: how its sensor is turned in the device and its intrinsics for a frame of a given size. */
interface CameraCalibration {
    val sensorOrientationDegrees: Int
    fun intrinsicsFor(width: Int, height: Int): CameraIntrinsics
}

/** The magnetic declination in degrees at the device, or null when it is not known (no location permission or no last known fix). */
fun interface DeclinationSource {
    fun degrees(): Double?
}

/** A monotonic clock in nanoseconds, on the same base as [TimedAttitude.elapsedRealtimeNs]. */
fun interface ElapsedClock {
    fun nowNs(): Long
}

/** Pixel brightness (0-255) of a camera frame's luma plane. */
interface LumaImage {
    val width: Int
    val height: Int
    operator fun get(x: Int, y: Int): Int
}
