package com.sclera.sclerago.services.ai

import com.sclera.sclerago.data.AssetType
import com.sclera.sclerago.data.typeOf

/** Typical values per asset type, used by AI autofill for fields that are still empty. */
private val TYPICAL: Map<AssetType, Map<String, String>> = mapOf(
    AssetType.MOTOR to mapOf("make" to "WEG", "model" to "W22 132M 4", "power" to "7.5 kW", "voltage" to "415 V", "speed" to "1460 RPM", "sys" to "Mechanical", "sub" to "Induction"),
    AssetType.PUMP to mapOf("make" to "Grundfos", "model" to "NB 50-200", "power" to "11 kW", "voltage" to "415 V", "speed" to "2900 RPM", "sys" to "Mechanical", "sub" to "Centrifugal"),
    AssetType.CHILLER to mapOf("make" to "Daikin", "model" to "EWAD-TZ 400", "power" to "140 kW", "voltage" to "415 V", "sys" to "HVAC", "sub" to "Air-cooled screw"),
    AssetType.FAN to mapOf("make" to "Systemair", "model" to "DVS 400E4", "power" to "0.55 kW", "voltage" to "415 V", "speed" to "1400 RPM", "sys" to "HVAC", "sub" to "Centrifugal"),
    AssetType.CONTROLLER to mapOf("make" to "Siemens", "model" to "Desigo PXC4.E16", "voltage" to "24 V", "sys" to "HVAC", "sub" to "BACnet/IP controller"),
    AssetType.CCTV to mapOf("make" to "Axis", "model" to "M2036-LE", "voltage" to "48 V", "sys" to "Security", "sub" to "Outdoor bullet"),
    AssetType.PANEL to mapOf("make" to "Schneider Electric", "model" to "Acti9 Isobar P TPN 12-way", "voltage" to "415 V", "sys" to "Electrical", "sub" to "TP&N final distribution"),
    AssetType.AP to mapOf("make" to "Ruckus", "model" to "R550", "voltage" to "48 V", "sys" to "IT & network", "sub" to "Wi-Fi 6 indoor AP"),
    AssetType.COMPUTER to mapOf("make" to "Dell", "model" to "OptiPlex 7010 SFF", "power" to "0.18 kW", "voltage" to "230 V", "sys" to "IT & network", "sub" to "Desktop"),
    AssetType.PHONE to mapOf("make" to "Yealink", "model" to "SIP-T54W", "voltage" to "48 V", "sys" to "IT & network", "sub" to "VoIP desk phone"),
    AssetType.SPEAKER to mapOf("make" to "Jabra", "model" to "Speak2 55", "voltage" to "5 V", "sys" to "Audio-visual", "sub" to "Bluetooth speakerphone"),
    AssetType.SENSOR to mapOf("make" to "Siemens", "model" to "QFA2060", "voltage" to "24 V", "sys" to "HVAC"),
)

/**
 * Suggestions for the fields that are still empty (or "Generic"), based on the asset's name or type.
 * Never overwrites something already entered.
 */
fun aiAutofill(fallbackType: AssetType, values: Map<String, String>): Map<String, String> {
    fun empty(k: String) = values[k].isNullOrBlank() || values[k] == "Generic"
    val type = typeOf(values["name"]).takeIf { it != AssetType.GENERIC } ?: fallbackType
    val guess = TYPICAL[type].orEmpty().toMutableMap()
    if (type != AssetType.GENERIC) guess["typ"] = type.label
    guess["jacs"] = if (type in setOf(AssetType.MOTOR, AssetType.PUMP, AssetType.CHILLER, AssetType.FAN)) "[JACS code 1]" else "[JACS code 2]"
    guess["note"] = "Suggested by AI from the name, type and photos. Check before saving."
    return guess.filterKeys { empty(it) }
}
