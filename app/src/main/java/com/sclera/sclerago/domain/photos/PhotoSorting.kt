package com.sclera.sclerago.domain.photos

import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind

/** What reading a photo on the device found: its lines of text, and whether it shows a QR code or barcode. */
data class PhotoReading(val lines: List<String>, val hasCode: Boolean)

/** Reads a photo on the device, with no connection needed. */
interface PhotoReader {
    /** The text and codes on the photo at [uri]; null when it can't be read (missing, not an image, or the reader failed). */
    suspend fun read(uri: String): PhotoReading?
}

/** The kinds of data a nameplate shows; a line can show several. */
enum class NameplateData(val pattern: Regex) {
    POWER("""\d[\d.,]*\s*(kw|hp)\b|\b(power|output)\b"""),
    VOLTAGE("""\d[\d.,/-]*\s*v\b|\bvolts?\b|\bvoltage\b"""),
    FREQUENCY("""\d[\d.,/]*\s*hz\b|\bhz\b|\bfrequency\b"""),
    SPEED("""\d[\d.,]*\s*(rpm|r/min|min-1|1/min)|\brpm\b|\bspeed\b"""),
    CURRENT("""\d[\d.,/-]*\s*a\b|\bamps?\b|\bcurrent\b|\bfla\b"""),
    SERIAL("""\bs/?n\b|\bserial\b|\bser\.?\s*no\b"""),
    IP_RATING("""\bip\s?[0-6x][0-9x]\b"""),
    MODEL("""\bmodel\b|\btype\b|\btyp\b|\bmod\.?\s*no\b|\bcat\.?\s*no\b"""),
    PHASE("""\b[13]\s*(ph|phase)\b|\b[13]\s*~|\bphase\b"""),
    CLASS("""\bins(ul(ation)?)?\.?\s*cl(ass|\.)?\b|\bcl(ass|\.)?\s*[bfh]\b|\bie[1-5]\b|\beff(iciency)?\b"""),
    PRESSURE_FLOW("""\d[\d.,]*\s*(bar|psi|kpa|mpa|m3/h|m³/h|l/min|l/s|gpm|cfm)\b|\b(flow|pressure|head)\b"""),
    ;

    constructor(pattern: String) : this(Regex(pattern, RegexOption.IGNORE_CASE))
}

/** The kinds of nameplate data anywhere in [lines]. */
fun nameplateDataIn(lines: List<String>): Set<NameplateData> =
    NameplateData.entries.filterTo(mutableSetOf()) { kind -> lines.any { kind.pattern.containsMatchIn(it) } }

/**
 * The kind of photo [reading] shows:
 * - a nameplate: at least [NAMEPLATE_LINES] lines of text showing at least [NAMEPLATE_KINDS] kinds of nameplate data;
 * - a label ID: it has a QR code or barcode, unless it is clearly a nameplate ([CODED_NAMEPLATE_KINDS] kinds or more),
 *   as many motor nameplates carry a code;
 * - an asset: anything else (a "DANGER 400V" sticker shows one kind of data, so it stays an asset photo).
 */
fun photoKindOf(reading: PhotoReading): PhotoKind {
    val lines = reading.lines.filter { it.isNotBlank() }
    val kinds = nameplateDataIn(lines).size
    val nameplate = lines.size >= NAMEPLATE_LINES && kinds >= NAMEPLATE_KINDS
    return when {
        reading.hasCode -> if (nameplate && kinds >= CODED_NAMEPLATE_KINDS) PhotoKind.NAMEPLATE else PhotoKind.LABEL_ID
        nameplate -> PhotoKind.NAMEPLATE
        else -> PhotoKind.ASSET
    }
}

/** A photo whose kind can be read off it: a photo (not a video, voice memo or file) with no kind that answers no field. */
fun Media.isSortable(): Boolean = !video && !audio && !isDoc && fieldId == null && category == null

private const val NAMEPLATE_LINES = 3
private const val NAMEPLATE_KINDS = 2
private const val CODED_NAMEPLATE_KINDS = 4
