package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.domain.model.QrMeasurement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The JSON the server stores for a QR code's direction: `{"direction":{"yaw":..,"pitch":..,"roll":..,"confidence":..,"method":".."}}`. */
fun directionJson(m: QrMeasurement): String = buildJsonObject {
    put("direction", buildJsonObject {
        put("yaw", m.yaw)
        put("pitch", m.pitch)
        put("roll", m.roll)
        put("confidence", m.confidence)
        put("method", m.method)
    })
}.toString()
