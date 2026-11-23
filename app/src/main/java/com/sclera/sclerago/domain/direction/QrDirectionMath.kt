package com.sclera.sclerago.domain.direction

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What one frame says about which way the QR faces: compass [yaw], [pitch] and top-edge [roll] in degrees, [confidence] 0..1 and the [method] label. */
internal class QrFrameMeasurement(val yaw: Double, val pitch: Double, val roll: Double, val confidence: Double, val method: String)

/** A square QR pose solved from its corners, in the world's frame. */
private class PnpPoseEstimate(val cornersWorld: Array<DoubleArray>, val normalWorld: DoubleArray, val reprojectionErrorPx: Double)

/**
 * The direction of one QR frame (the old app's `calculateUnifiedQRMeasurement`). The QR's plane comes from a solved pose when the
 * view is oblique (or [forcePnp]) and from the vanishing points otherwise; its corners are placed in the world, and the yaw (the
 * QR's up direction or its face normal, as a compass bearing), pitch (the plane's tilt) and roll (the top edge's bearing) follow.
 */
internal object QrDirectionMath {
    private const val UNIFIED_WALL_FLOOR_THRESHOLD = 0.15
    private const val QR_UP_HORIZONTAL_YAW_THRESHOLD = 0.15
    private const val ROLL_TOP_EDGE_HORIZONTAL_MIN = 0.05
    private const val FLOOR_QR_UP_ENTER_THRESHOLD = 0.85
    const val PNP_SKEW_EDGE_RATIO_THRESHOLD = 0.96
    private const val PNP_MAX_REPROJECTION_ERROR_PX = 8.0
    private const val WALL_HORIZONTAL_LENGTH = 0.70
    private const val ASSUMED_FLOOR_LOOKING_DOWN = -0.3
    private const val PLANE_CONFIDENCE = 0.70
    private const val ASSUMED_PLANE_CONFIDENCE = 0.40

    /** [pts] are the corners (x0, y0 .. x3, y3) in the frame, [camPose] the pose of that frame, [attitude] the sensor reading for the compass and [declination] the magnetic declination if known. Null when the frame gives no direction. */
    fun measure(
        pts: DoubleArray,
        camPose: DoubleArray,
        fx: Double,
        fy: Double,
        cx: Double,
        cy: Double,
        attitude: DoubleArray?,
        declination: Double?,
        forcePnp: Boolean,
    ): QrFrameMeasurement? {
        val camOrigin = doubleArrayOf(camPose[12], camPose[13], camPose[14])
        val camFwd = doubleArrayOf(-camPose[8], -camPose[9], -camPose[10])
        val usePnp = forcePnp || QrFrameGeometry.edgeRatios(pts).toList().min() < PNP_SKEW_EDGE_RATIO_THRESHOLD
        val pnp = if (usePnp) estimatePnp(pts, fx, fy, cx, cy, camPose) ?: return null else null
        val vp = if (pnp == null) QrFrameGeometry.normalViaVanishingPoint(pts, fx, fy, cx, cy, camPose) else null

        var planeNormal: DoubleArray
        val planeSource: String
        when {
            pnp != null -> { planeNormal = pnp.normalWorld.copyOf(); planeSource = "PnP" }
            vp != null -> { planeNormal = vp.normalWorld.copyOf(); planeSource = "VP" }
            camFwd[1] < ASSUMED_FLOOR_LOOKING_DOWN -> { planeNormal = doubleArrayOf(0.0, 1.0, 0.0); planeSource = "assumed-floor" }
            else -> return null
        }
        if (v3Dot(planeNormal, camFwd) < 0.0) planeNormal = v3Scale(planeNormal, -1.0)

        val hx = planeNormal[0]
        val hz = planeNormal[2]
        val horizLen = hypot(hx, hz)
        val planePoint = doubleArrayOf(camOrigin[0] + camFwd[0], camOrigin[1] + camFwd[1], camOrigin[2] + camFwd[2])
        val corners = pnp?.cornersWorld ?: Array(4) { i ->
            val ray = worldRay(pts[2 * i], pts[2 * i + 1], fx, fy, cx, cy, camPose)
            intersectRayPlane(camOrigin, ray, planePoint, planeNormal) ?: return null
        }

        val qrUp = v3Sub(corners[0], corners[3])
        val qrUpHorizontal = hypot(qrUp[0], qrUp[2])
        val surface = when {
            horizLen > WALL_HORIZONTAL_LENGTH -> "WALL"
            qrUpHorizontal >= FLOOR_QR_UP_ENTER_THRESHOLD -> "FLOOR"
            else -> "TILTED"
        }
        val pitch = -Math.toDegrees(asin(planeNormal[1].coerceIn(-1.0, 1.0)))

        val fromQrUp = qrUpHorizontal > QR_UP_HORIZONTAL_YAW_THRESHOLD
        var arYaw: Double
        if (fromQrUp) {
            arYaw = normalizeDegrees(Math.toDegrees(atan2(qrUp[0] / qrUpHorizontal, -qrUp[2] / qrUpHorizontal)))
        } else {
            if (horizLen < 1e-4) return null
            arYaw = normalizeDegrees(Math.toDegrees(atan2(hx / horizLen, -hz / horizLen)))
            if (qrUp[1] < 0.0) arYaw = normalizeDegrees(arYaw + 180.0)
        }

        val northOffset = CameraPose.northOffset(camPose, attitude)
        val correctedOffset = if (!northOffset.isNaN() && declination != null) northOffset + declination else northOffset
        val compassYaw = normalizeDegrees(arYaw + if (correctedOffset.isNaN()) 0.0 else correctedOffset)

        val topEdge = v3Normalize(v3Sub(corners[1], corners[0]))
        val roll = if (fromQrUp) {
            (compassYaw + 90.0) % 360.0
        } else {
            horizontalCompassBearing(topEdge, correctedOffset).takeUnless { it.isNaN() }
                ?: inPlaneRoll(topEdge, planeNormal, correctedOffset) ?: return null
        }

        val baseConfidence = if (planeSource == "VP" || planeSource == "PnP") PLANE_CONFIDENCE else ASSUMED_PLANE_CONFIDENCE
        val verticalConfidence = if (horizLen > UNIFIED_WALL_FLOOR_THRESHOLD) min(horizLen, 1.0) else 1.0
        if (northOffset.isNaN()) return null
        val method = if (surface == "WALL") "unified-tilted" else "unified-${surface.lowercase()}"
        return QrFrameMeasurement(compassYaw, pitch, roll, baseConfidence * verticalConfidence, method)
    }

    /** The roll from the top edge's angle in the plane against north (or world up) projected into it; null when no reference is long enough, NaN when the angle is undefined. */
    private fun inPlaneRoll(topEdge: DoubleArray, planeNormal: DoubleArray, offset: Double): Double? {
        var reference = DoubleArray(3)
        var length = 0.0
        if (!offset.isNaN()) {
            val northYaw = Math.toRadians(-offset)
            val arNorth = doubleArrayOf(sin(northYaw), 0.0, -cos(northYaw))
            reference = projectOntoPlane(arNorth, planeNormal)
            length = v3Len(reference)
        }
        if (length < 0.01) {
            reference = projectOntoPlane(doubleArrayOf(0.0, 1.0, 0.0), planeNormal)
            length = v3Len(reference)
        }
        if (length < 0.01) return null
        return inPlaneAngle(topEdge, reference, planeNormal)
    }

    private fun estimatePnp(pts: DoubleArray, fx: Double, fy: Double, cx: Double, cy: Double, camPose: DoubleArray): PnpPoseEstimate? {
        val square = arrayOf(doubleArrayOf(-0.5, 0.5, 0.0), doubleArrayOf(0.5, 0.5, 0.0), doubleArrayOf(0.5, -0.5, 0.0), doubleArrayOf(-0.5, -0.5, 0.0))
        val best = QrPoseSolver.solve(pts, fx, fy, cx, cy).mapNotNull { pose ->
            val r = pose.rotation
            val t = pose.translation
            var minDepth = Double.POSITIVE_INFINITY
            val corners = Array(4) { i ->
                val p = square[i]
                val xCv = r[0] * p[0] + r[1] * p[1] + r[2] * p[2] + t[0]
                val yCv = r[3] * p[0] + r[4] * p[1] + r[5] * p[2] + t[1]
                val zCv = r[6] * p[0] + r[7] * p[1] + r[8] * p[2] + t[2]
                minDepth = min(minDepth, zCv)
                CameraPose.camToWorld(doubleArrayOf(xCv, -yCv, -zCv), camPose)
            }
            if (minDepth <= 0.0 || minDepth.isNaN()) return@mapNotNull null
            val normalWorld = v3Normalize(CameraPose.camToWorld(doubleArrayOf(r[2], -r[5], -r[8]), camPose))
            PnpPoseEstimate(corners, normalWorld, pose.reprojectionErrorPx)
        }.minByOrNull { it.reprojectionErrorPx }
        return best?.takeIf { it.reprojectionErrorPx <= PNP_MAX_REPROJECTION_ERROR_PX }
    }

    private fun worldRay(x: Double, y: Double, fx: Double, fy: Double, cx: Double, cy: Double, camPose: DoubleArray): DoubleArray {
        val camDir = v3Normalize(doubleArrayOf((x - cx) / fx, -(y - cy) / fy, -1.0))
        return v3Normalize(CameraPose.camToWorld(camDir, camPose))
    }

    private fun intersectRayPlane(origin: DoubleArray, dir: DoubleArray, planePoint: DoubleArray, normal: DoubleArray): DoubleArray? {
        val denom = v3Dot(dir, normal)
        if (abs(denom) < 1e-5) return null
        val t = v3Dot(v3Sub(planePoint, origin), normal) / denom
        if (t <= 0.0) return null
        return doubleArrayOf(origin[0] + dir[0] * t, origin[1] + dir[1] * t, origin[2] + dir[2] * t)
    }

    private fun projectOntoPlane(v: DoubleArray, normal: DoubleArray): DoubleArray {
        val d = v3Dot(v, normal)
        return doubleArrayOf(v[0] - normal[0] * d, v[1] - normal[1] * d, v[2] - normal[2] * d)
    }

    private fun inPlaneAngle(direction: DoubleArray, reference: DoubleArray, normal: DoubleArray): Double {
        val refDir = v3Normalize(reference)
        val refPerp = v3Normalize(v3Cross(normal, refDir))
        if (v3Len(refDir) < 1e-4 || v3Len(refPerp) < 1e-4) return Double.NaN
        return normalizeDegrees(Math.toDegrees(atan2(v3Dot(direction, refPerp), v3Dot(direction, refDir))))
    }

    /** The compass bearing of a horizontal-enough [direction], or NaN. */
    private fun horizontalCompassBearing(direction: DoubleArray, northOffset: Double): Double {
        if (northOffset.isNaN()) return Double.NaN
        val horizontal = hypot(direction[0], direction[2])
        if (horizontal < ROLL_TOP_EDGE_HORIZONTAL_MIN) return Double.NaN
        return normalizeDegrees(Math.toDegrees(atan2(direction[0] / horizontal, -direction[2] / horizontal)) + northOffset)
    }
}
