package com.sclera.sclerago.domain.direction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One frame's measurement kept for averaging: weighted by its confidence. */
internal class YawSample(val northYaw: Double, val pitch: Double, val roll: Double, val weight: Double, val timestampMs: Long)

/** The robust average of the frames sampled for one QR: outliers are trimmed, then yaw and roll (circular) and pitch are averaged by weight. */
internal object SampleAverager {
    const val REQUIRED_SAMPLES = 6
    private const val ROBUST_MIN_KEPT_SAMPLES = 4
    private const val ROBUST_MAX_TRIMMED_SAMPLES = REQUIRED_SAMPLES - ROBUST_MIN_KEPT_SAMPLES
    private const val ROBUST_TRIM_SCORE_THRESHOLD_DEG = 6.0

    fun weightedMeanPitch(samples: List<YawSample>): Double {
        val total = samples.sumOf { it.weight }
        return if (total < 1e-6) 0.0 else samples.sumOf { it.pitch * it.weight } / total
    }

    fun circularWeightedMeanRoll(samples: List<YawSample>) = circularMean(samples) { it.roll }

    fun circularWeightedMeanNorth(samples: List<YawSample>) = circularMean(samples) { it.northYaw }

    private fun circularMean(samples: List<YawSample>, angle: (YawSample) -> Double): Double {
        val total = samples.sumOf { it.weight }
        if (total < 1e-6) return 0.0
        val s = samples.sumOf { sin(Math.toRadians(angle(it))) * it.weight }
        val c = samples.sumOf { cos(Math.toRadians(angle(it))) * it.weight }
        return normalizeDegrees(Math.toDegrees(atan2(s / total, c / total)))
    }

    /** Drops, at most twice and keeping at least four, the sample farthest from the means while it is 6 degrees or more off in yaw, pitch or roll. */
    fun robustTrim(samples: List<YawSample>): List<YawSample> {
        val kept = ArrayList(samples)
        val maxTrim = min(ROBUST_MAX_TRIMMED_SAMPLES, max(0, kept.size - ROBUST_MIN_KEPT_SAMPLES))
        for (iteration in 0 until maxTrim) {
            if (kept.size <= ROBUST_MIN_KEPT_SAMPLES) break
            val yawMean = circularWeightedMeanNorth(kept)
            val pitchMean = weightedMeanPitch(kept)
            val rollMean = circularWeightedMeanRoll(kept)
            var worstIndex = -1
            var worstScore = 0.0
            for (i in kept.indices) {
                val s = kept[i]
                val score = max(
                    max(abs(angleDifference(yawMean, s.northYaw)), abs(s.pitch - pitchMean)),
                    abs(angleDifference(rollMean, s.roll)),
                )
                if (score > worstScore) {
                    worstIndex = i
                    worstScore = score
                }
            }
            if (worstIndex < 0 || worstScore < ROBUST_TRIM_SCORE_THRESHOLD_DEG) break
            kept.removeAt(worstIndex)
        }
        return kept
    }
}
