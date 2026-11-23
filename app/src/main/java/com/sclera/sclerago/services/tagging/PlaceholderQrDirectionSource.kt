package com.sclera.sclerago.services.tagging

import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.repository.QrDirectionSource
import javax.inject.Inject

/**
 * The direction recorded for a QR code scanned on a device that cannot measure one (it has no rotation-vector sensor): fixed values,
 * so the server still gets a well-formed `direction`. A device that can measure sends what [QrDirectionMeter] measured instead.
 */
class PlaceholderQrDirectionSource @Inject constructor() : QrDirectionSource {
    override suspend fun measure() = QrMeasurement(yaw = YAW, pitch = PITCH, roll = ROLL, confidence = CONFIDENCE, method = METHOD)

    private companion object {
        const val YAW = 90.0
        const val PITCH = 0.0
        const val ROLL = 0.0
        const val CONFIDENCE = 1.0
        const val METHOD = "placeholder"
    }
}
