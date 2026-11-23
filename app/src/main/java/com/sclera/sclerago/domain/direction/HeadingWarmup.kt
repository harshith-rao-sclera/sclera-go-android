package com.sclera.sclerago.domain.direction

import kotlin.math.abs

/**
 * Waits for the compass to settle before any frame is sampled: the camera's heading must stay within 7 degrees from one frame to the
 * next for 4 frames in a row, at least 0.8 s after the sensor started, with the sensor reporting at least low accuracy.
 */
internal class HeadingWarmup(startNs: Long) {
    private var startedNs = startNs
    private var lastHeading: Double? = null
    private var stableCount = 0

    var isReady = false
        private set

    fun reset(nowNs: Long) {
        startedNs = nowNs
        lastHeading = null
        stableCount = 0
        isReady = false
    }

    /** Takes one frame's pose and the attitude [sample] read for it. */
    fun update(camPose: DoubleArray, sample: TimedAttitude?, accuracy: Int, nowNs: Long) {
        if (sample == null || nowNs - sample.elapsedRealtimeNs > MAX_ATTITUDE_AGE_NS) return unsettle()
        val offset = CameraPose.northOffset(camPose, sample.values)
        if (offset.isNaN()) return unsettle()
        val heading = normalizeDegrees(CameraPose.cameraYaw(camPose) + offset)
        val previous = lastHeading
        lastHeading = heading
        if (previous == null) {
            stableCount = 1
            return
        }
        if (abs(angleDifference(previous, heading)) > STABILITY_DELTA_DEG) {
            stableCount = 1
            return
        }
        stableCount++
        if (!isReady && stableCount >= STABILITY_REQUIRED &&
            (nowNs - startedNs) / NS_PER_MS >= MIN_WARMUP_MS && accuracy >= MIN_SENSOR_ACCURACY
        ) isReady = true
    }

    private fun unsettle() {
        lastHeading = null
        stableCount = 0
    }

    companion object {
        /** A sensor reading older than this (half a second) is not used for a frame. */
        const val MAX_ATTITUDE_AGE_NS = 500_000_000L
        /** `SensorManager.SENSOR_STATUS_ACCURACY_LOW`. */
        const val MIN_SENSOR_ACCURACY = 1
        private const val STABILITY_REQUIRED = 4
        private const val STABILITY_DELTA_DEG = 7.0
        private const val MIN_WARMUP_MS = 800L
        private const val NS_PER_MS = 1_000_000L
    }
}
