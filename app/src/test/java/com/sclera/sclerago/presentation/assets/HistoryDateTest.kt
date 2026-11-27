package com.sclera.sclerago.presentation.assets

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryDateTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun datesAreFormattedAndOtherValuesArePreserved() {
        assertEquals("Nov 27, 2026", historyValue("1795746600", "date", Locale.US, zone))
        assertEquals("Nov 27, 2026", historyValue("1795746600000", "date", Locale.US, zone))
        assertEquals("Nov 27, 2026", historyValue("2026-11-27", "date", Locale.US, zone))
        assertEquals("bad date", historyValue("bad date", "date", Locale.US, zone))
        assertEquals("1795746600", historyValue("1795746600", "number", Locale.US, zone))
        assertNull(historyValue(null, "date", Locale.US, zone))
    }
}
