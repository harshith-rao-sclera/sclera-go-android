package com.sclera.sclerago.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTextTest {
    @Test fun plainTextIsUnchanged() = assertEquals("Jabra Speak 510", sanitizeScanText("Jabra Speak 510"))

    @Test fun nullAndEmptyGiveEmpty() {
        assertEquals("", sanitizeScanText(null))
        assertEquals("", sanitizeScanText(""))
    }

    @Test fun controlCharactersAreRemoved() = assertEquals("ab", sanitizeScanText("a\u0000\u0007b\u001B"))

    @Test fun lineBreaksBecomeOneSpace() = assertEquals("one two", sanitizeScanText("one\r\n\ttwo"))

    @Test fun bidiOverridesAndZeroWidthMarksAreRemoved() =
        assertEquals("exe.txt", sanitizeScanText("‮exe​.txt⁦"))

    @Test fun blankRunsCollapseAndEdgesAreTrimmed() = assertEquals("a b", sanitizeScanText("   a     b   "))

    @Test fun lengthIsCapped() {
        assertEquals(MAX_SCAN_TEXT, sanitizeScanText("x".repeat(500)).length)
        assertEquals("abc", sanitizeScanText("abcdef", max = 3))
    }

    @Test fun emojiAreNotSplitWhenCapped() {
        val s = sanitizeScanText("😀".repeat(100))
        assertTrue(s.codePointCount(0, s.length) <= MAX_SCAN_TEXT)
        assertTrue(s.all { !Character.isSurrogate(it) } || s.length % 2 == 0)
    }

    @Test fun onlyUnsafeCharactersGiveEmpty() = assertEquals("", sanitizeScanText("\u0000​‮"))
}
