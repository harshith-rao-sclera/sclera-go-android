package com.sclera.sclerago.domain.usecase.validation

import java.time.LocalDate
import java.time.ZoneId
import com.sclera.sclerago.domain.model.storedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewDateTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun readsTimestampUnitsAndIsoDates() {
        val day = LocalDate.of(2026, 11, 27)
        assertEquals(day, storedDate("1795746600", zone))
        assertEquals(day, storedDate("1795746600000", zone))
        assertEquals(day, storedDate("2026-11-27", zone))
        assertEquals(day, storedDate("2026-11-26T20:00:00Z", zone))
        assertEquals(day, storedDate("2026-11-27T12:00:00", zone))
    }

    @Test
    fun invalidDatesAreNotInterpreted() {
        assertNull(storedDate("not a date", zone))
        assertNull(storedDate("NaN", zone))
        assertNull(storedDate("999999999999999999999999", zone))
    }
}
