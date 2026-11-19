package com.sclera.sclerago.domain.model

/**
 * What a Bluetooth, network or access point scan found out about a device that says what it is: its maker and model,
 * what kind of device it is, its operating system and the services it offers. An asset made from the device starts
 * with these (Manufacturer, Model and its Description), and the AI fills its other fields from them.
 *
 * Nothing here identifies the device or a person: no IP or MAC address, BLE address, BSSID, SSID or host name. Those
 * are shown on the scan list only, never stored on the asset or sent to the AI (MASVS-PRIVACY).
 */
data class ScanFacts(
    val make: String? = null,
    val model: String? = null,
    /** What kind of device: "TV / Streaming", "Printer", "Bluetooth beacon", "Wi-Fi access point". */
    val kind: String? = null,
    val os: String? = null,
    /** What it offers: "AirPlay", "Printer (IPP)", "Heart Rate", "WPA2"… */
    val services: List<String> = emptyList(),
) {
    /** One line of the facts: "Samsung · UCU8000 · TV / Streaming · Tizen · AirPlay, Chromecast". */
    fun summary(): String = (
        listOf(make, model, kind, os) + services.take(MAX_SERVICES).joinToString(", ")
        ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct().joinToString(" · ")

    private companion object {
        const val MAX_SERVICES = 6
    }
}
