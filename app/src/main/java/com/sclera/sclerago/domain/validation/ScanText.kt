package com.sclera.sclerago.domain.validation

/** Longest text taken from a scan result (a name, SSID or host name). */
const val MAX_SCAN_TEXT = 64

/**
 * Makes text that came off the air or the network safe to show and store. Bluetooth names, SSIDs and host
 * names are chosen by whoever owns the device, so they are untrusted: control characters, line breaks and
 * invisible direction/format marks (which can reorder or hide what the user reads) are dropped, runs of
 * blanks become one space, and the result is at most [max] characters.
 */
fun sanitizeScanText(raw: String?, max: Int = MAX_SCAN_TEXT): String {
    if (raw.isNullOrEmpty()) return ""
    val out = StringBuilder()
    var lastBlank = true
    var count = 0
    var i = 0
    while (i < raw.length && count < max) {
        val cp = raw.codePointAt(i)
        i += Character.charCount(cp)
        val type = Character.getType(cp)
        val dropped = Character.isISOControl(cp) && !Character.isWhitespace(cp) ||
            type == Character.FORMAT.toInt() || type == Character.PRIVATE_USE.toInt() || type == Character.UNASSIGNED.toInt() ||
            type == Character.SURROGATE.toInt()
        if (dropped) continue
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            if (!lastBlank) { out.append(' '); count++ }
            lastBlank = true
            continue
        }
        out.appendCodePoint(cp)
        count++
        lastBlank = false
    }
    return out.toString().trimEnd()
}
