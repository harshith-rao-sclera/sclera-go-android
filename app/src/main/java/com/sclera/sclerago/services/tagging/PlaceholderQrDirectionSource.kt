package com.sclera.sclerago.services.tagging

import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.repository.QrDirectionSource
import javax.inject.Inject

/**
 * The direction recorded for a scanned QR code until the AR measurement is built for Android: fixed values, so the server
 * still gets a well-formed `direction`. Replace this one binding (see `TaggingModule`) with the AR engine's source.
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
