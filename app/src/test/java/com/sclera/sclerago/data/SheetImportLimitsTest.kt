package com.sclera.sclerago.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/** MASVS-PLATFORM: an untrusted spreadsheet can't exhaust memory (it is read up to the size limit only). */
class SheetImportLimitsTest {
    @Test
    fun readBounded_returnsAllBytesUnderTheLimit() {
        val data = ByteArray(1_000) { it.toByte() }
        assertEquals(data.toList(), ByteArrayInputStream(data).readBounded(1_000).toList())
    }

    @Test
    fun readBounded_stopsAtTheLimit() {
        try {
            ByteArrayInputStream(ByteArray(2_000)).readBounded(1_000)
            fail("expected SheetTooLargeException")
        } catch (e: SheetTooLargeException) {
            assertTrue(e.message!!.contains("larger"))
        }
    }
}
