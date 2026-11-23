package com.sclera.sclerago.domain.direction

import com.sclera.sclerago.domain.model.QrMeasurement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Measures which way a QR code faces from the camera's frames (the old app's secure QR scanner), over one scan. For each frame the
 * scanner calls [beginFrame] when the frame arrives (it takes the camera pose from the rotation-vector sensor and the intrinsics),
 * then [onQr] with the code read from it, or [onNoQr] when none was. Frames that are too small, too oblique, taken before the compass
 * has settled or whose yaw jumps are skipped; six good frames of the same code are trimmed and averaged, and that is returned.
 * Nothing here logs: the payload and the angles stay in memory. Safe to call from the camera and detector threads.
 */
class QrDirectionSession(
    private val attitude: AttitudeSource,
    private val calibration: CameraCalibration,
    private val declination: DeclinationSource,
    private val clock: ElapsedClock,
) {
    /** What is known when a frame arrives: its pose and intrinsics. */
    class Frame internal constructor(
        internal val camPose: DoubleArray,
        internal val fx: Double,
        internal val fy: Double,
        internal val cx: Double,
        internal val cy: Double,
        internal val acquireNs: Long,
    )

    private val warmup = HeadingWarmup(clock.nowNs())
    private val samples = ArrayList<YawSample>()
    private val recentYaws = ArrayDeque<Double>()
    private var payload: String? = null
    private var lockedMethod: String? = null
    private var lastSampleMs = 0L
    private var lastQrSeenMs = 0L
    private var latchPayload: String? = null
    private var latchUntilMs = 0L

    /** The frame's pose and intrinsics (scaled by [zoom], the camera's zoom ratio), or null while the sensor has no fresh reading. Also lets the compass warm up. */
    @Synchronized
    fun beginFrame(width: Int, height: Int, zoom: Float): Frame? {
        val nowNs = clock.nowNs()
        val sample = attitude.latest()
        if (sample == null || nowNs - sample.elapsedRealtimeNs > HeadingWarmup.MAX_ATTITUDE_AGE_NS) return null
        val pose = runCatching { CameraPose.fromAttitude(sample.values, calibration.sensorOrientationDegrees) }.getOrNull() ?: return null
        warmup.update(pose, sample, attitude.accuracy, nowNs)
        val k = calibration.intrinsicsFor(width, height)
        val z = if (zoom.isNaN() || zoom.isInfinite()) 1.0 else max(zoom.toDouble(), 1.0)
        return Frame(pose, k.fx * z, k.fy * z, k.cx, k.cy, nowNs)
    }

    /**
     * A code [payload] was read from [frame]: [detected] are its corners (top-left, top-right, bottom-right, bottom-left; x0, y0 ..)
     * as the detector gave them and [refined] the same after sub-pixel refinement. Returns the direction once enough frames agree.
     */
    @Synchronized
    fun onQr(frame: Frame, payload: String, detected: FloatArray, refined: FloatArray): QrMeasurement? {
        val nowMs = clock.nowNs() / NS_PER_MS
        lastQrSeenMs = nowMs
        val corners = DoubleArray(8) { refined[it].toDouble() }
        val quality = QrFrameGeometry.evaluate(DoubleArray(8) { detected[it].toDouble() }, corners)
        val sample = attitude.latest()?.takeIf { abs(frame.acquireNs - it.elapsedRealtimeNs) <= HeadingWarmup.MAX_ATTITUDE_AGE_NS }
        val measurement = runCatching {
            QrDirectionMath.measure(
                corners, frame.camPose, frame.fx, frame.fy, frame.cx, frame.cy,
                sample?.values, if (sample != null) declination.degrees() else null, usePnpForFrame(payload, quality, nowMs),
            )
        }.getOrNull()

        if (payload != this.payload) startSession(payload)
        if (!warmup.isReady || !quality.isAcceptableForSampling || !isYawContinuous(measurement?.yaw ?: Double.NaN)) {
            dropStalePartialSamples(nowMs)
            return null
        }
        measurement?.yaw?.takeUnless { it.isNaN() }?.let { rememberYaw(it) }
        if (sample == null || measurement == null) {
            dropStalePartialSamples(nowMs)
            return null
        }
        return addSample(measurement, nowMs)
    }

    /** No code was read from the latest frame: sampling is given up once none has been seen for 3.5 s. */
    @Synchronized
    fun onNoQr() {
        if (payload != null && clock.nowNs() / NS_PER_MS - lastQrSeenMs > QR_LOST_TIMEOUT_MS) clearSession()
    }

    /** Forgets everything and lets the compass warm up again (the sensor was stopped). */
    @Synchronized
    fun reset() {
        clearSession()
        latchPayload = null
        latchUntilMs = 0L
        warmup.reset(clock.nowNs())
    }

    private fun startSession(newPayload: String) {
        clearSession()
        payload = newPayload
    }

    private fun clearSession() {
        payload = null
        lockedMethod = null
        samples.clear()
        recentYaws.clear()
        lastSampleMs = 0L
    }

    /** A frame is solved with the pose (not the vanishing points) once it is oblique, and keeps being for 3 s until it is nearly head-on again. */
    private fun usePnpForFrame(payload: String, quality: QrFrameQuality, nowMs: Long): Boolean {
        val latched = latchPayload == payload && nowMs <= latchUntilMs
        val use = quality.skewRatio < QrDirectionMath.PNP_SKEW_EDGE_RATIO_THRESHOLD || (latched && quality.skewRatio < PNP_SKEW_LATCH_RELEASE_RATIO)
        if (use) {
            latchPayload = payload
            latchUntilMs = nowMs + PNP_SKEW_LATCH_MS
        } else if (latched || (latchUntilMs > 0L && nowMs > latchUntilMs)) {
            latchPayload = null
            latchUntilMs = 0L
        }
        return use
    }

    /** A yaw that jumps more than 45 degrees from the circular mean of the last five is a glitch. */
    private fun isYawContinuous(yaw: Double): Boolean {
        if (yaw.isNaN() || recentYaws.isEmpty()) return true
        val s = recentYaws.sumOf { sin(Math.toRadians(it)) } / recentYaws.size
        val c = recentYaws.sumOf { cos(Math.toRadians(it)) } / recentYaws.size
        val mean = normalizeDegrees(Math.toDegrees(atan2(s, c)))
        return abs(angleDifference(mean, yaw)) <= YAW_SPIKE_THRESHOLD_DEG
    }

    private fun rememberYaw(yaw: Double) {
        recentYaws.addLast(yaw)
        while (recentYaws.size > YAW_HISTORY_SIZE) recentYaws.removeFirst()
    }

    /** Samples older than 5 s are dropped; with none left the next frame starts afresh. */
    private fun dropStalePartialSamples(nowMs: Long) {
        if (samples.isEmpty() || lastSampleMs <= 0L) return
        samples.removeAll { nowMs - it.timestampMs > MAX_SAMPLE_AGE_MS }
        if (samples.isEmpty()) {
            lastSampleMs = 0L
            lockedMethod = null
        }
    }

    private fun addSample(m: QrFrameMeasurement, nowMs: Long): QrMeasurement? {
        if (m.confidence < MIN_SAMPLE_WEIGHT || m.roll.isNaN()) {
            dropStalePartialSamples(nowMs)
            return null
        }
        if (lockedMethod == null) lockedMethod = m.method
        samples.removeAll { nowMs - it.timestampMs > MAX_SAMPLE_AGE_MS }
        lastSampleMs = nowMs
        samples.add(YawSample(m.yaw, m.pitch, normalizeDegrees(m.roll), m.confidence, nowMs))
        if (samples.size < SampleAverager.REQUIRED_SAMPLES) return null

        val kept = SampleAverager.robustTrim(samples)
        val result = QrMeasurement(
            yaw = SampleAverager.circularWeightedMeanNorth(kept),
            pitch = SampleAverager.weightedMeanPitch(kept),
            roll = normalizeDegrees(SampleAverager.circularWeightedMeanRoll(kept)),
            confidence = kept.sumOf { it.weight } / kept.size,
            method = lockedMethod ?: m.method,
        )
        clearSession()
        return result
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
        const val MIN_SAMPLE_WEIGHT = 0.05
        const val MAX_SAMPLE_AGE_MS = 5000L
        const val QR_LOST_TIMEOUT_MS = 3500L
        const val YAW_HISTORY_SIZE = 5
        const val YAW_SPIKE_THRESHOLD_DEG = 45.0
        const val PNP_SKEW_LATCH_RELEASE_RATIO = 0.985
        const val PNP_SKEW_LATCH_MS = 3_000L
    }
}
