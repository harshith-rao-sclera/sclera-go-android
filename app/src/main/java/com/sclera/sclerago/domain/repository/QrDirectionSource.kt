package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.QrMeasurement

/** Measures which way a scanned QR code faces. */
interface QrDirectionSource {
    suspend fun measure(): QrMeasurement
}
