package com.sclera.sclerago.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Read both timestamp units and ISO dates without shifting a date-only value to a different day. */
fun storedDate(value: String, zone: ZoneId): LocalDate? {
    val text = value.trim()
    text.toDoubleOrNull()?.let { number ->
        if (!number.isFinite()) return null
        return runCatching {
            val millis = if (kotlin.math.abs(number) >= 1e12) number else number * 1000
            require(millis > Long.MIN_VALUE.toDouble() && millis < Long.MAX_VALUE.toDouble())
            Instant.ofEpochMilli(millis.toLong()).atZone(zone).toLocalDate()
        }.getOrNull()
    }
    return runCatching { LocalDate.parse(text) }
        .recoverCatching { Instant.parse(text).atZone(zone).toLocalDate() }
        .recoverCatching { OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDate() }
        .recoverCatching { LocalDateTime.parse(text).toLocalDate() }
        .getOrNull()
}
