//
//  WifiAccessPoint.kt
//  AssetOnBoard
//
//  One Wi-Fi access point seen over the air.
//
//  This is a different shape of thing from a [NetworkDevice]: it has no IPv4
//  address, only a radio identity (SSID + BSSID) and a signal reading. It is the
//  result of the over-the-air Wi-Fi scan, which enumerates the networks in range
//  rather than the hosts on the one we joined.
//

package com.example.sclerago.services.scan.model

import kotlinx.serialization.Serializable

@Serializable
data class WifiAccessPoint(
    /** Network name; empty for a hidden SSID. */
    val ssid: String,
    /** The AP's radio MAC — the stable identity even when the SSID is hidden. */
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMHz: Int,
    /** Human security label derived from the capability string ("WPA2", "Open"). */
    val securityLabel: String,
    /** True for the AP the device is currently associated with. */
    val isCurrentNetwork: Boolean = false,
    /** IEEE OUI vendor for the BSSID, when the MAC is not randomised. */
    val vendor: String? = null,
    val lastSeen: Long = System.currentTimeMillis(),
) {
    val id: String get() = bssid

    val displayName: String get() = ssid.ifBlank { "Hidden network" }

    /** 2.4 / 5 / 6 GHz band label, from the operating frequency. */
    val band: String
        get() = when (frequencyMHz) {
            in 2401..2499 -> "2.4 GHz"
            in 4900..5899 -> "5 GHz"
            in 5900..7125 -> "6 GHz"
            in 57000..71000 -> "60 GHz"
            else -> "—"
        }

    /** IEEE channel number, computed from the centre frequency. */
    val channel: Int?
        get() = when (frequencyMHz) {
            2484 -> 14
            in 2412..2472 -> (frequencyMHz - 2407) / 5
            in 5160..5885 -> (frequencyMHz - 5000) / 5
            in 5955..7115 -> (frequencyMHz - 5950) / 5
            else -> null
        }

    /** 0–4 bars, the same thresholds a status bar uses. */
    val signalBars: Int
        get() = when {
            rssiDbm >= -55 -> 4
            rssiDbm >= -66 -> 3
            rssiDbm >= -77 -> 2
            rssiDbm >= -88 -> 1
            else -> 0
        }

    val isOpen: Boolean get() = securityLabel == "Open"

    /** One-line subtitle: band, channel and security. */
    val subtitle: String
        get() = buildList {
            add(band)
            channel?.let { add("Ch $it") }
            add(securityLabel)
        }.joinToString(" · ")

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return listOfNotNull(displayName, bssid, band, securityLabel, vendor)
            .any { it.lowercase().contains(needle) }
    }
}
