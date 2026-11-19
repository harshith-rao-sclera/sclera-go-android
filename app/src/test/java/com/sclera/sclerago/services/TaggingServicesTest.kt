package com.sclera.sclerago.services

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.mapper.directionJson
import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.services.tagging.NfcTagReader
import com.sclera.sclerago.services.tagging.PlaceholderQrDirectionSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaggingServicesTest {
    @Test
    fun `the placeholder direction is fixed and well formed`() = runTest {
        val m = PlaceholderQrDirectionSource().measure()
        assertEquals(PlaceholderQrDirectionSource().measure(), m)
        assertTrue(m.yaw in 0.0..360.0 && m.pitch in -90.0..90.0 && m.roll in 0.0..360.0)
        assertEquals("placeholder", m.method)
    }

    @Test
    fun `the direction goes to the server as nested json`() {
        assertEquals(
            """{"direction":{"yaw":1.5,"pitch":-2.0,"roll":3.0,"confidence":0.5,"method":"m"}}""",
            directionJson(QrMeasurement(1.5, -2.0, 3.0, 0.5, "m")),
        )
        assertNull(TagPlacement().direction)
    }

    @Test
    fun `an NFC link record reads as its address`() {
        val message = NdefMessage(NdefRecord.createUri("https://example.com/tag/1"))
        assertEquals("https://example.com/tag/1", NfcTagReader.textOf(message))
    }

    @Test
    fun `an NFC text record reads as its text without the language code`() {
        val message = NdefMessage(NdefRecord.createTextRecord("en", "pump-17"))
        assertEquals("pump-17", NfcTagReader.textOf(message))
    }

    @Test
    fun `a record with nothing readable gives no text`() {
        assertNull(NfcTagReader.textOf(NdefMessage(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null))))
    }
}
