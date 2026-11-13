package com.sclera.sclerago.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** The limits for what an .xlsx may unpack to (zip-bomb guard). */
const val MAX_SHEET_ENTRY_BYTES = 50L * 1024 * 1024
const val MAX_SHEET_ZIP_ENTRIES = 2_000

/** A spreadsheet (or something inside it) is bigger than the app will read. */
class SheetTooLargeException(message: String) : IllegalArgumentException(message)

/** All of this stream, or a [SheetTooLargeException] as soon as it passes [max] bytes. */
internal fun InputStream.readBounded(max: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) throw SheetTooLargeException("The file is larger than the allowed -eMB")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/**
 * Reads the first sheet of a spreadsheet the user picked: CSV (comma, semicolon or tab separated)
 * or Excel (.xlsx). Returns its rows as lists of cell texts. Old binary .xls files aren't supported.
 */
fun readSheet(context: Context, uri: Uri): List<List<String>> {
    val cr = context.contentResolver
    val name = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }.orEmpty().lowercase()
    val type = cr.getType(uri).orEmpty()
    val bytes = cr.openInputStream(uri)?.use { it.readBounded(MAX_IMPORT_BYTES) } ?: error("Can't open file")
    val isXlsx = name.endsWith(".xlsx") || type.contains("openxmlformats") ||
        (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
    if (isXlsx) return readXlsx(bytes)
    if (name.endsWith(".xls")) error("Save the sheet as .xlsx or .csv")
    return readCsv(String(bytes, Charsets.UTF_8).removePrefix("﻿"))
}

/** CSV with quoted cells; the separator is whichever of , ; or tab the first line uses most. */
fun readCsv(text: String): List<List<String>> {
    val first = text.lineSequence().firstOrNull().orEmpty()
    val sep = listOf(',', ';', '\t').maxBy { c -> first.count { it == c } }
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            !quoted && ch == sep -> { row += cell.toString(); cell.clear() }
            !quoted && (ch == '\n' || ch == '\r') -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += cell.toString(); cell.clear()
                if (row.any { it.isNotBlank() }) rows += row
                row = mutableListOf()
            }
            else -> cell.append(ch)
        }
        i++
    }
    row += cell.toString()
    if (row.any { it.isNotBlank() }) rows += row
    return rows
}

/** The first worksheet of an .xlsx (a zip of XML files): shared strings + sheet1 cells. */
internal fun readXlsx(
    bytes: ByteArray,
    maxEntryBytes: Long = MAX_SHEET_ENTRY_BYTES,
    maxEntries: Int = MAX_SHEET_ZIP_ENTRIES,
): List<List<String>> {
    var strings = ""
    var sheet = ""
    var entries = 0
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            if (++entries > maxEntries) throw SheetTooLargeException("The spreadsheet has too many parts")
            when (e.name) {
                "xl/sharedStrings.xml" -> strings = zip.readBounded(maxEntryBytes).toString(Charsets.UTF_8)
                "xl/worksheets/sheet1.xml" -> sheet = zip.readBounded(maxEntryBytes).toString(Charsets.UTF_8)
            }
        }
    }
    if (sheet.isEmpty()) error("No sheet in file")
    val shared = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(strings).map { si ->
        Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { xmlText(it.groupValues[1]) }
    }.toList()
    return Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).map { row ->
        val cells = sortedMapOf<Int, String>()
        Regex("<c ([^>]*?)(/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).forEach { c ->
            val attrs = c.groupValues[1]
            val body = c.groupValues[3]
            val ref = Regex("r=\"([A-Z]+)").find(attrs)?.groupValues?.get(1) ?: return@forEach
            val col = ref.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
            val t = Regex("t=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
            val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
            cells[col] = when (t) {
                "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { xmlText(it.groupValues[1]) }
                else -> xmlText(v.orEmpty()).removeSuffix(".0")
            }
        }
        if (cells.isEmpty()) emptyList() else List(cells.lastKey() + 1) { cells[it].orEmpty() }
    }.filter { r -> r.any { it.isNotBlank() } }.toList()
}

private fun xmlText(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
