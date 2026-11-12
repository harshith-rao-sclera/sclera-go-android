//
//  BleDevice.kt
//  AssetOnBoard
//
//  One Bluetooth Low Energy peripheral seen advertising nearby.
//
//  Like a [WifiAccessPoint] and unlike a [NetworkDevice], this has no IP address.
//  Its identity is a Bluetooth MAC — which on most modern peripherals is a
//  rotating random address rather than the hardware one, so it is stable only for
//  the length of a scan.
//

package com.sclera.sclerago.services.scan.model

import kotlinx.serialization.Serializable

@Serializable
data class BleDevice(
    /** Bluetooth address; often a rotating random one (see [addressIsRandom]). */
    val address: String,
    /** Advertised local name, when the peripheral broadcasts one. */
    val name: String? = null,
    val rssiDbm: Int,
    /** Advertised GATT service UUIDs. */
    val serviceUuids: List<String> = emptyList(),
    /** Bluetooth SIG company that owns the manufacturer-data block, if known. */
    val manufacturerName: String? = null,
    val companyId: Int? = null,
    /** Advertised transmit power, used with RSSI to estimate distance. */
    val txPowerDbm: Int? = null,
    val connectable: Boolean = true,
    val addressIsRandom: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
) {
    val id: String get() = address

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: manufacturerName?.let { "$it device" }
            ?: "Bluetooth device"

    /** True when no advertised name gave us anything better than the address. */
    val isUnnamed: Boolean get() = name.isNullOrBlank()

    /** 0–4 bars. BLE runs weaker than Wi-Fi, so the thresholds sit lower. */
    val signalBars: Int
        get() = when {
            rssiDbm >= -60 -> 4
            rssiDbm >= -72 -> 3
            rssiDbm >= -84 -> 2
            rssiDbm >= -96 -> 1
            else -> 0
        }

    val subtitle: String
        get() = buildList {
            manufacturerName?.let { add(it) }
            if (!connectable) add("Broadcast only")
            add("$rssiDbm dBm")
        }.joinToString(" · ")

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val haystack = mutableListOf(displayName, address)
        manufacturerName?.let { haystack += it }
        haystack += serviceUuids
        return haystack.any { it.lowercase().contains(needle) }
    }
}
