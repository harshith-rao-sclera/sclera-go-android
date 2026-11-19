package com.sclera.sclerago.domain.tagging

import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TagValueTest {
    private fun assertRefused(error: TaggingError, block: () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: TaggingException) {
            assertEquals(error, e.error)
        }
    }

    @Test
    fun `a Sclera QR link keeps only its id and is a system code`() {
        val tag = TagValue.qr("  https://app.sclera.com/qr?id=abc-123 ")
        assertEquals(TagKind.QR, tag.kind)
        assertEquals("abc-123", tag.id)
        assertTrue(tag.isSystem)
    }

    @Test
    fun `any other QR text is the customer's own code`() {
        val tag = TagValue.qr(" SN-0042 ")
        assertEquals("SN-0042", tag.id)
        assertFalse(tag.isSystem)
    }

    @Test
    fun `empty QR text and a link with no id are refused`() {
        assertRefused(TaggingError.EMPTY_TAG) { TagValue.qr("   ") }
        assertRefused(TaggingError.EMPTY_TAG) { TagValue.qr("https://app.sclera.com/qr?id=") }
    }

    @Test
    fun `a barcode is the trimmed text, never a system code`() {
        val tag = TagValue.barcode(" 5901234123457 ")
        assertEquals(TagKind.BARCODE, tag.kind)
        assertEquals("5901234123457", tag.id)
        assertFalse(tag.isSystem)
        assertRefused(TaggingError.EMPTY_TAG) { TagValue.barcode("") }
    }

    @Test
    fun `an NFC tag needs content that is not a link to the Sclera server`() {
        val prefix = "https://app.sclera.com"
        assertEquals("04:A2:B3", TagValue.nfc(" 04:A2:B3 ", isBlank = false, vdmsPrefix = prefix).id)
        assertRefused(TaggingError.BLANK_NFC) { TagValue.nfc("", isBlank = true, vdmsPrefix = prefix) }
        assertRefused(TaggingError.EMPTY_TAG) { TagValue.nfc("   ", isBlank = false, vdmsPrefix = prefix) }
        assertRefused(TaggingError.TAKEN_BY_VDMS) { TagValue.nfc("$prefix/qr?id=1", isBlank = false, vdmsPrefix = prefix) }
    }

    @Test
    fun `tags compare by content and never print it`() {
        assertEquals(TagValue.qr("A"), TagValue.qr(" A "))
        assertEquals(TagValue.qr("A").hashCode(), TagValue.qr("A").hashCode())
        assertFalse(TagValue.qr("A") == TagValue.barcode("A"))
        assertFalse(TagValue.qr("secret-code").toString().contains("secret-code"))
    }
}
