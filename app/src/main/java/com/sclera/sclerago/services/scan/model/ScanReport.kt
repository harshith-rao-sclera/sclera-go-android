//
//  ScanReport.kt
//  AssetOnBoard
//
//  A point-in-time snapshot of one scan session, suitable for export.
//
//  A session can cover up to three surfaces, so the report carries an optional
//  network section (the LAN sweep of one subnet) alongside the flat lists the
//  radio scans produce. Any of the three may be absent — a Wi-Fi-only scan
//  exports a report with no network section and no Bluetooth devices.
//

package com.sclera.sclerago.services.scan.model

import com.sclera.sclerago.services.scan.core.IPv4
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

@Serializable
data class ScanReport(
    /** Epoch milliseconds; [startedAtIso] renders them for the export. */
    val startedAt: Long,
    val finishedAt: Long,
    /** The LAN sweep, when a network scan ran; null for a radio-only session. */
    val network: NetworkSection? = null,
    val wifiAccessPoints: List<WifiAccessPoint> = emptyList(),
    val bleDevices: List<BleDevice> = emptyList(),
) {
    /** The result of one subnet sweep, over one interface. */
    @Serializable
    data class NetworkSection(
        val interfaceName: String,
        val interfaceKind: String,
        val localAddress: IPv4,
        val subnet: String,
        /** `interface` for our own segment, `custom` for a range the user named. */
        val subnetSource: String = "interface",
        val gateway: IPv4? = null,
        val depth: String,
        val devices: List<NetworkDevice>,
    )

    val durationSeconds: Double get() = (finishedAt - startedAt) / 1000.0

    val startedAtIso: String get() = iso(startedAt)
    val finishedAtIso: String get() = iso(finishedAt)

    fun toJson(): String = JSON.encodeToString(Envelope.of(this))

    private fun iso(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

    /**
     * Adds the ISO-8601 strings and per-section counts the export carries.
     *
     * The wire model keeps epoch milliseconds so the data stays cheap to merge
     * and compare, but a report a human or another tool reads wants real
     * timestamps and item counts, so those are derived here on the way out.
     */
    @Serializable
    data class Envelope(
        val startedAt: String,
        val finishedAt: String,
        val durationSeconds: Double,
        val network: NetworkEnvelope? = null,
        val wifiAccessPointCount: Int,
        val wifiAccessPoints: List<WifiAccessPoint>,
        val bleDeviceCount: Int,
        val bleDevices: List<BleDevice>,
    ) {
        @Serializable
        data class NetworkEnvelope(
            val interfaceName: String,
            val interfaceKind: String,
            val localAddress: IPv4,
            val subnet: String,
            val subnetSource: String,
            val gateway: IPv4? = null,
            val depth: String,
            val deviceCount: Int,
            val devices: List<NetworkDevice>,
        )

        companion object {
            fun of(report: ScanReport) = Envelope(
                startedAt = report.startedAtIso,
                finishedAt = report.finishedAtIso,
                durationSeconds = report.durationSeconds,
                network = report.network?.let { section ->
                    NetworkEnvelope(
                        interfaceName = section.interfaceName,
                        interfaceKind = section.interfaceKind,
                        localAddress = section.localAddress,
                        subnet = section.subnet,
                        subnetSource = section.subnetSource,
                        gateway = section.gateway,
                        depth = section.depth,
                        deviceCount = section.devices.size,
                        devices = section.devices,
                    )
                },
                wifiAccessPointCount = report.wifiAccessPoints.size,
                wifiAccessPoints = report.wifiAccessPoints,
                bleDeviceCount = report.bleDevices.size,
                bleDevices = report.bleDevices,
            )
        }
    }

    companion object {
        private val JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
