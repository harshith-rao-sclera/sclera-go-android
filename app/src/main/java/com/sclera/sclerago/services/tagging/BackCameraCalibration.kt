package com.sclera.sclerago.services.tagging

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.sclera.sclerago.domain.direction.CameraCalibration
import com.sclera.sclerago.domain.direction.CameraIntrinsics
import kotlin.math.abs
import kotlin.math.max

/**
 * The back camera sensor orientation and intrinsics from Camera2: the factory calibration (`LENS_INTRINSIC_CALIBRATION`, with an unset
 * principal point taken as the array centre), else derived from the focal length and sensor size, else a generic 60-degree guess.
 * Intrinsics are given at the active-array resolution and scaled to the frame; a frame cropped from the array is scaled evenly and
 * keeps the optical centre where the crop puts it.
 */
class BackCameraCalibration(private val context: Context) : CameraCalibration {
    private class Base(val fx: Double, val fy: Double, val cx: Double, val cy: Double, val activeWidth: Int, val activeHeight: Int)

    private val loaded: Pair<Int, Base?> by lazy { load() }

    override val sensorOrientationDegrees: Int get() = loaded.first

    override fun intrinsicsFor(width: Int, height: Int): CameraIntrinsics {
        val base = loaded.second
        if (base == null || base.activeWidth <= 0 || base.activeHeight <= 0 || width <= 0 || height <= 0) {
            val f = FALLBACK_FOCAL_FACTOR * max(width, height)
            return CameraIntrinsics(f, f, width / 2.0, height / 2.0)
        }
        val frameAspect = width.toDouble() / height
        val activeAspect = base.activeWidth.toDouble() / base.activeHeight
        return when {
            abs(frameAspect - activeAspect) < ASPECT_TOLERANCE -> CameraIntrinsics(
                base.fx * width / base.activeWidth, base.fy * height / base.activeHeight,
                base.cx * width / base.activeWidth, base.cy * height / base.activeHeight,
            )
            frameAspect > activeAspect -> {
                val s = width.toDouble() / base.activeWidth
                CameraIntrinsics(base.fx * s, base.fy * s, base.cx * s, height / 2.0 + (base.cy - base.activeHeight / 2.0) * s)
            }
            else -> {
                val s = height.toDouble() / base.activeHeight
                CameraIntrinsics(base.fx * s, base.fy * s, width / 2.0 + (base.cx - base.activeWidth / 2.0) * s, base.cy * s)
            }
        }
    }

    private fun load(): Pair<Int, Base?> = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val back = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        val chars = back?.let(manager::getCameraCharacteristics)
        (chars?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: DEFAULT_ORIENTATION) to chars?.let(::baseOf)
    } catch (_: Exception) {
        DEFAULT_ORIENTATION to null
    }

    private fun baseOf(chars: CameraCharacteristics): Base? {
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
        val calibration = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        if (calibration != null && calibration.size >= 4 && calibration[0] > 0f && calibration[1] > 0f) {
            val cx = if (calibration[2] > 0f) calibration[2].toDouble() else active.width() / 2.0
            val cy = if (calibration[3] > 0f) calibration[3].toDouble() else active.height() / 2.0
            return Base(calibration[0].toDouble(), calibration[1].toDouble(), cx, cy, active.width(), active.height())
        }
        val focalMm = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val physical = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixels = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        if (focalMm == null || physical == null || pixels == null || physical.width <= 0f || physical.height <= 0f) return null
        return Base(
            focalMm.toDouble() / physical.width * pixels.width, focalMm.toDouble() / physical.height * pixels.height,
            active.width() / 2.0, active.height() / 2.0, active.width(), active.height(),
        )
    }

    private companion object {
        const val DEFAULT_ORIENTATION = 90
        const val FALLBACK_FOCAL_FACTOR = 0.87
        const val ASPECT_TOLERANCE = 0.01
    }
}
