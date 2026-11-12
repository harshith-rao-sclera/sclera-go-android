package com.sclera.sclerago.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** MASVS-PLATFORM: untrusted spreadsheets can't exhaust memory (size and zip-bomb limits). */
class SheetImportLimitsTest {
    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val sheetXml = """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Hello</t></is></c></row></sheetData></worksheet>"""

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

    @Test
    fun readXlsx_readsASmallSheet() {
        val rows = readXlsx(zip(listOf("xl/worksheets/sheet1.xml" to sheetXml)))
        assertEquals("Hello", rows.first().first())
    }

    @Test
    fun readXlsx_refusesAnEntryThatUnpacksTooBig() {
        val bomb = zip(listOf("xl/worksheets/sheet1.xml" to "x".repeat(5_000)))
        try {
            readXlsx(bomb, maxEntryBytes = 1_000)
            fail("expected SheetTooLargeException")
        } catch (e: SheetTooLargeException) {
            // expected
        }
    }

    @Test
    fun readXlsx_refusesTooManyParts() {
        val many = zip((1..20).map { "junk/$it.txt" to "a" } + ("xl/worksheets/sheet1.xml" to sheetXml))
        try {
            readXlsx(many, maxEntries = 10)
            fail("expected SheetTooLargeException")
        } catch (e: SheetTooLargeException) {
            // expected
        }
    }

    @Test
    fun readCsv_readsRows() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), readCsv("a,b\n1,2"))
    }
}
