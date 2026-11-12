package com.sclera.sclerago.services.scan

import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.services.scan.model.BleDevice
import com.sclera.sclerago.services.scan.model.DeviceCategory
import com.sclera.sclerago.services.scan.model.NetworkDevice
import com.sclera.sclerago.services.scan.model.WifiAccessPoint
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Turns real scan results (from the scanners in core/) into the Device rows the
 * Bluetooth and Network scan pages already show, in the same "name / detail line"
 * shape the pages were designed around.
 */

/** Id prefixes, so a row can be traced back to the kind of scan that found it. */
const val BLE_ID = "ble:"
const val NET_ID = "ip:"
const val AP_ID = "ap:"

/** Shown (and treated as "no name") when a device advertises nothing better than its address. */
const val UNKNOWN_DEVICE = "Unknown device"

/** −52 dBm, with a real minus sign as in the rest of the app. */
private fun dbm(v: Int) = "${v.toString().replace('-', '−')} dBm"

fun BleDevice.toRow(): Device {
    // Rough distance from signal strength: path-loss model against the strength at 1 m.
    // An advertised TX power is the radio's output (≈41 dB above the 1 m reading); without
    // one, −59 dBm at 1 m is the usual assumption for phones, tags and beacons.
    val atOneMetre = txPowerDbm?.let { it - 41 } ?: -59
    val metres = 10.0.pow((atOneMetre - rssiDbm) / 20.0)
    val dist = when {
        metres < 1.5 -> "~1 m"
        metres < 30 -> "~${metres.roundToInt()} m"
        else -> "30 m+"
    }
    val kind = manufacturerName ?: if (connectable) "BLE device" else "BLE beacon"
    return Device(
        id = BLE_ID + address,
        name = if (isUnnamed) UNKNOWN_DEVICE else displayName,
        sub = listOf(kind, dbm(rssiDbm), dist).joinToString(" · "),
        lvl = signalBars.coerceIn(1, 4),
        known = false,
        // A Bluetooth advertisement only says what it is through its name ("TH-22 sensor", "Jabra Speak").
        type = typeOf(name),
    )
}

fun NetworkDevice.toRow(): Device {
    // No radio signal for a host on the LAN; show how quickly it answered instead.
    val rttMs = roundTripTime?.times(1000)
    val lvl = when {
        rttMs == null -> 2
        rttMs < 10 -> 4
        rttMs < 50 -> 3
        rttMs < 200 -> 2
        else -> 1
    }
    return Device(
        id = NET_ID + ip,
        name = if (isUnnamed) UNKNOWN_DEVICE else displayName,
        // The IP leads the line: it is what an asset's "IP address" field is matched on.
        sub = "$ip · $subtitle",
        lvl = lvl,
        known = false,
        type = assetType(category).takeIf { it != AssetType.GENERIC } ?: typeOf(listOfNotNull(displayName, modelName).joinToString(" ")),
    )
}

/** The ScleraGo asset type closest to what the network scan identified a host as. */
private fun assetType(c: DeviceCategory): AssetType = when (c) {
    DeviceCategory.ROUTER, DeviceCategory.ACCESS_POINT -> AssetType.AP
    DeviceCategory.COMPUTER, DeviceCategory.SERVER, DeviceCategory.VIRTUAL_MACHINE, DeviceCategory.NAS -> AssetType.COMPUTER
    DeviceCategory.PHONE, DeviceCategory.TABLET, DeviceCategory.WEARABLE -> AssetType.PHONE
    DeviceCategory.TV, DeviceCategory.SPEAKER -> AssetType.SPEAKER
    DeviceCategory.CAMERA -> AssetType.CCTV
    DeviceCategory.SMART_HOME -> AssetType.SENSOR
    DeviceCategory.PRINTER, DeviceCategory.GAME_CONSOLE, DeviceCategory.UNKNOWN -> AssetType.GENERIC
}

fun WifiAccessPoint.toRow(): Device = Device(
    id = AP_ID + bssid,
    name = displayName,
    // The BSSID leads the line: it identifies the access point even when the SSID is shared or hidden.
    // Signal strength goes on the line now that the icon tile has taken the signal bars' place.
    sub = listOfNotNull(bssid, channel?.let { "ch $it" }, band, dbm(rssiDbm), if (isCurrentNetwork) "connected" else null).joinToString(" · "),
    lvl = signalBars.coerceIn(1, 4),
    known = false,
    type = AssetType.AP,
)

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
