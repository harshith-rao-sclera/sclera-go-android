package com.sclera.sclerago.domain.model

/** Id prefixes, so a row can be traced back to the kind of scan that found it. */
const val BLE_ID = "ble:"
const val NET_ID = "ip:"
const val AP_ID = "ap:"

/** Shown (and treated as "no name") when a device advertises nothing better than its address. */
const val UNKNOWN_DEVICE = "Unknown device"


/**
 * What identifies a scanned device on an asset: a Bluetooth tag's name (its MAC when it
 * has none), a network host's IP, an access point's BSSID.
 */
fun scanKey(d: Device): String = when {
    d.id.startsWith(BLE_ID) -> if (d.name == UNKNOWN_DEVICE) d.id.removePrefix(BLE_ID) else d.name
    else -> d.sub.substringBefore(" ·").trim()
}

/**
 * True when [text] mentions [key] as a whole token, so 192.168.1.2 does not match
 * 192.168.1.21 and SCL-041 does not match SCL-0418.
 */
fun mentions(text: String, key: String): Boolean {
    if (key.isBlank()) return false
    return Regex("(?<![\\w.:-])" + Regex.escape(key) + "(?![\\w:-]|\\.\\w)", RegexOption.IGNORE_CASE).containsMatchIn(text)
}
