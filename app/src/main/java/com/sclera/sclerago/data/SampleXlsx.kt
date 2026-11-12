package com.sclera.sclerago.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Columns of the property import file, in order (the fields of Add New Property). */
val PROPERTY_IMPORT_COLUMNS = listOf(
    "Name", "Type", "Address line 1", "Address line 2", "Country", "State", "Region", "City",
    "Contact name", "Contact email", "Contact phone", "Notes", "Start time", "End time", "Access code",
)

private val SAMPLE_ROWS = listOf(
    listOf("Harbour Point Office Tower", "Office", "1 Harbour Point", "", "Singapore", "", "Central", "Singapore", "", "", "", "", "00:01", "23:59", ""),
    listOf("Northgate Logistics Hub", "Warehouse", "Northgate Industrial Estate", "Unit 4", "United Kingdom", "England", "North", "Manchester", "", "", "", "Loading bay access only", "07:00", "19:00", ""),
)

/** Writes the sample property import file (a minimal .xlsx with a header row and two examples) to [out]. */
fun writeSamplePropertyXlsx(out: OutputStream) {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun col(i: Int) = ('A' + i).toString()
    val rows = (listOf(PROPERTY_IMPORT_COLUMNS) + SAMPLE_ROWS).mapIndexed { r, cells ->
        cells.mapIndexed { c, v -> """<c r="${col(c)}${r + 1}" t="inlineStr"><is><t>${esc(v)}</t></is></c>""" }
            .joinToString("", """<row r="${r + 1}">""", "</row>")
    }.joinToString("")

    val files = linkedMapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Properties" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
        "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>""",
    )
    ZipOutputStream(out).use { zip ->
        files.forEach { (name, xml) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
