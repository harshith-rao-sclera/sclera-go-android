package com.sclera.sclerago.services.scan

import com.sclera.sclerago.domain.model.AP_ID
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.BLE_ID
import com.sclera.sclerago.domain.model.Device
import com.sclera.sclerago.domain.model.NET_ID
import com.sclera.sclerago.domain.model.ScanDetail
import com.sclera.sclerago.domain.model.ScanFacts
import com.sclera.sclerago.domain.model.UNKNOWN_DEVICE
import com.sclera.sclerago.domain.model.typeOf
import com.sclera.sclerago.domain.validation.sanitizeScanText
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
    // "Microsoft · −67 dBm · ~3 m", or how it advertises when it names no maker.
    val kind = sanitizeScanText(manufacturerName).ifEmpty { null } ?: if (connectable) "Connectable" else "Broadcast only"
    val services = serviceUuids.mapNotNull(::bleServiceName).distinct()
    return Device(
        id = BLE_ID + address,
        // Its Bluetooth name when it advertises one, else its Bluetooth address, so each row can be told apart.
        name = sanitizeScanText(name).ifEmpty { sanitizeScanText(address) }.ifEmpty { UNKNOWN_DEVICE },
        // The Bluetooth address leads the line (as the IP does on the network list), unless it is already the name.
        sub = (listOfNotNull(sanitizeScanText(address).takeIf { it.isNotEmpty() && !text(name).isNullOrEmpty() }) + listOf(kind, dbm(rssiDbm), dist))
            .joinToString(" · "),
        lvl = signalBars.coerceIn(1, 4),
        known = false,
        // A Bluetooth advertisement only says what it is through its name ("TH-22 sensor", "Jabra Speak").
        type = typeOf(sanitizeScanText(name)),
        facts = ScanFacts(
            make = text(manufacturerName),
            details = details {
                add("Found by", "Bluetooth LE scan")
                add("Kind", if (connectable) "Bluetooth device" else "Bluetooth beacon")
                add("Device name", name)
                add("Manufacturer", manufacturerName)
                add("Bluetooth address", address + if (addressIsRandom) " (random)" else "")
                val best = bestRssiDbm ?: rssiDbm
                add("Signal", "$rssiDbm dBm" + if (best != rssiDbm) " (best $best dBm)" else "")
                add("Transmit power", txPowerDbm?.let { "$it dBm" })
                add("Distance", dist)
                add("Connectable", if (connectable) "Yes" else "No")
                add("Services", services.joinToString(", "))
            },
        ),
    )
}

/** The name of a standard Bluetooth service (its 16-bit UUID), or null for a maker's own one. */
private fun bleServiceName(uuid: String): String? {
    val short = uuid.lowercase().takeIf { it.endsWith(BLE_BASE_UUID) }?.substring(4, 8) ?: uuid.lowercase().takeIf { it.length == 4 }
    return short?.let(BLE_SERVICES::get)
}

private const val BLE_BASE_UUID = "-0000-1000-8000-00805f9b34fb"

/** Standard Bluetooth services that say what a device is (the Bluetooth SIG's assigned numbers). */
private val BLE_SERVICES = mapOf(
    "1800" to "Generic Access", "180a" to "Device Information", "180f" to "Battery", "180d" to "Heart Rate",
    "1809" to "Health Thermometer", "1810" to "Blood Pressure", "1808" to "Glucose", "1816" to "Cycling Speed and Cadence",
    "1818" to "Cycling Power", "1814" to "Running Speed and Cadence", "181a" to "Environmental Sensing",
    "181b" to "Body Composition", "181d" to "Weight Scale", "1812" to "Human Interface Device", "1802" to "Immediate Alert",
    "1803" to "Link Loss", "1804" to "Tx Power", "1805" to "Current Time", "1819" to "Location and Navigation",
    "181c" to "User Data", "1826" to "Fitness Machine", "1827" to "Mesh Provisioning", "1828" to "Mesh Proxy",
    "183b" to "Binary Sensor", "184e" to "Audio Stream Control", "1844" to "Volume Control", "184f" to "Broadcast Audio Scan",
    "feaa" to "Eddystone beacon", "fd6f" to "Exposure Notification", "fe2c" to "Google Fast Pair",
)

/** Scan text as a fact, or null when there is none. */
private fun text(raw: String?): String? = sanitizeScanText(raw).ifEmpty { null }

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
    // Nothing identifies it: the line says "Unidentified" rather than how it answered.
    val identified = modelName != null || vendor != null || operatingSystem != null || category != DeviceCategory.UNKNOWN
    val services = (bonjourServices.mapNotNull { serviceName(it.friendlyProtocolName) } + openPorts.mapNotNull { text(it.serviceName) }).distinct()
    return Device(
        id = NET_ID + ip,
        // A host that gives no name is listed by its IP address.
        name = if (isUnnamed) ip.toString() else sanitizeScanText(displayName).ifEmpty { ip.toString() },
        sub = "$ip · ${sanitizeScanText(if (identified) subtitle else category.label)}",
        lvl = lvl,
        known = false,
        type = assetType(category).takeIf { it != AssetType.GENERIC } ?: typeOf(listOfNotNull(sanitizeScanText(displayName), sanitizeScanText(modelName)).joinToString(" ")),
        facts = ScanFacts(
            make = text(vendor),
            model = text(modelName) ?: text(modelIdentifier),
            details = details {
                add("Found by", "Wi-Fi network scan")
                add("Kind", category.label)
                add("Manufacturer", vendor)
                add("Model", modelName)
                add("Model identifier", modelIdentifier?.takeIf { it != modelName })
                add("UPnP name", upnp?.friendlyName)
                add("UPnP type", upnp?.deviceType)
                add("IP address", ip.toString())
                add("MAC address", macAddress?.toString())
                add("Host name", hostnames.joinToString(", "))
                add("Windows name", netbios?.computerName)
                add("Workgroup", netbios?.workgroup)
                add("Role", if (isGateway) "Router / gateway" else null)
                add("Operating system", operatingSystem)
                add("Services", services.joinToString(", "))
                add("Open ports", openPorts.joinToString(", ") { it.port.toString() })
                add("Web page", openPorts.firstNotNullOfOrNull { it.httpTitle })
                add("Web server", openPorts.firstNotNullOfOrNull { it.httpServer })
                add("Found with", discoveryMethods.sortedBy { it.ordinal }.joinToString(", ") { it.label })
                add("Response time", rttMs?.let { "${it.roundToInt()} ms" })
                add("TTL", ttl?.toString())
            },
        ),
    )
}

/** A Bonjour service's name, without the DNS-SD dressing ("_spotify-connect._tcp" → "spotify-connect"). */
private fun serviceName(raw: String): String? =
    text(raw.removePrefix("_").substringBefore("._tcp").substringBefore("._udp"))

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
    name = sanitizeScanText(displayName),
    // The BSSID leads the line: it identifies the access point even when the SSID is shared or hidden.
    // Signal strength goes on the line now that the icon tile has taken the signal bars' place.
    sub = listOfNotNull(sanitizeScanText(bssid), channel?.let { "ch $it" }, sanitizeScanText(band).ifEmpty { null }, dbm(rssiDbm), if (isCurrentNetwork) "connected" else null).joinToString(" · "),
    lvl = signalBars.coerceIn(1, 4),
    known = false,
    type = AssetType.AP,
    facts = ScanFacts(
        make = text(vendor),
        details = details {
            add("Found by", "Wi-Fi access point scan")
            add("Kind", "Wi-Fi access point")
            add("Network name", displayName)
            add("BSSID", bssid)
            add("Manufacturer", vendor)
            add("Band", band.takeIf { it != "—" })
            add("Channel", channel?.toString())
            add("Frequency", "$frequencyMHz MHz")
            add("Security", securityLabel)
            add("Signal", "$rssiDbm dBm")
            add("Connected", if (isCurrentNetwork) "Yes" else null)
        },
    ),
)

/** Builds a device's Description lines; a blank or missing value is left out, and every value is cleaned. */
private fun details(build: DetailsBuilder.() -> Unit): List<ScanDetail> = DetailsBuilder().apply(build).lines

private class DetailsBuilder {
    val lines = mutableListOf<ScanDetail>()
    fun add(label: String, value: String?) {
        text(value)?.let { lines += ScanDetail(label, it) }
    }
}
