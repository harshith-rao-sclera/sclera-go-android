package com.sclera.sclerago.domain.model

/**
 * What a Bluetooth, network or access point scan found out about a device. [make] and [model] go into the asset's
 * Manufacturer and Model; [details] are every fact the scan has, one per line, and become its Description, from which
 * the AI fills its other fields.
 *
 * Some lines identify the device or its owner (its IP or MAC address, Bluetooth address, BSSID, network or host name):
 * they are kept on the asset, but [withoutScanIdentifiers] takes them out of anything sent to the AI (MASVS-PRIVACY).
 */
data class ScanFacts(
    val make: String? = null,
    val model: String? = null,
    val details: List<ScanDetail> = emptyList(),
) {
    /** The Description: "Found by: Wi-Fi network scan", "Kind: Printer", "IP address: 10.2.30.28"… one per line. */
    fun description(): String = details.joinToString("\n") { "${it.label}: ${it.value}" }
}

/** One fact of a scan: its label and value ("Signal" → "-78 dBm (best -62 dBm)"). */
data class ScanDetail(val label: String, val value: String)

/** The labels of the Description lines that identify a device or its owner: never sent to the AI. */
val SCAN_IDENTIFYING_LABELS = setOf(
    "Device name", "IP address", "MAC address", "Host name", "Windows name", "Workgroup", "UPnP name",
    "Bluetooth address", "Network name", "BSSID",
)

/** [text] without its scan lines that identify the device ([SCAN_IDENTIFYING_LABELS]): what an AI request may carry. */
fun withoutScanIdentifiers(text: String): String {
    if (SCAN_IDENTIFYING_LABELS.none { text.contains("$it:") }) return text
    return text.lines().filterNot { line -> SCAN_IDENTIFYING_LABELS.any { line.trimStart().startsWith("$it:") } }.joinToString("\n")
}
