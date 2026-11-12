//
//  BleScanner.kt
//  AssetOnBoard
//
//  The Bluetooth Low Energy scan: peripherals advertising nearby.
//
//  A BLE scan is a stream of advertisement sightings rather than a one-shot list,
//  so results are merged by address as they arrive and the strongest recent RSSI
//  for each peripheral is kept. The scan runs for a bounded window and then stops,
//  because leaving a BLE scan running is a notable battery drain.
//
//  Everything that touches the Bluetooth stack is wrapped against SecurityException:
//  the caller is expected to hold BLUETOOTH_SCAN, but the permission can be revoked
//  between the check and the call, and a crash there is worse than an empty list.
//

package com.sclera.sclerago.services.scan.core

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import com.sclera.sclerago.services.scan.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleScanner(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Set when Bluetooth is switched off, which no permission can work around. */
    private val _adapterOff = MutableStateFlow(false)
    val adapterOff: StateFlow<Boolean> = _adapterOff.asStateFlow()

    val supported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /** How long one scan runs before it stops itself (ScleraGo: drives the progress shown on the scan card). */
    val scanWindowMs: Long get() = SCAN_WINDOW_MS

    private val found = LinkedHashMap<String, BleDevice>()
    private var job: Job? = null
    private var callback: ScanCallback? = null

    /** Caller must hold [ScanPermissions.Surface.BLE] before calling this. */
    fun start() {
        if (_isScanning.value) return
        if (!supported) {
            _status.value = "This device has no Bluetooth LE radio."
            return
        }

        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            _status.value = "Bluetooth is unavailable on this device."
            return
        }
        if (!adapter.isEnabled) {
            _adapterOff.value = true
            _status.value = "Turn on Bluetooth to scan for devices."
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _adapterOff.value = true
            _status.value = "Turn on Bluetooth to scan for devices."
            return
        }

        _adapterOff.value = false
        found.clear()
        _devices.value = emptyList()
        _isScanning.value = true
        _status.value = "Listening for Bluetooth devices…"

        val listener = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                merge(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::merge)
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = "Bluetooth scan failed (code $errorCode)."
                stop()
            }
        }
        callback = listener

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, listener)
        } catch (_: SecurityException) {
            _status.value = "Bluetooth permission was not granted."
            callback = null
            _isScanning.value = false
            return
        }

        job = scope.launch {
            delay(SCAN_WINDOW_MS)
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        val listener = callback
        callback = null
        if (listener != null) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            try {
                adapter?.bluetoothLeScanner?.stopScan(listener)
            } catch (_: SecurityException) {
                // Permission gone; the scan stops itself when the process yields.
            }
        }

        if (_isScanning.value) {
            _isScanning.value = false
            val count = _devices.value.size
            _status.value = "$count device${if (count == 1) "" else "s"} nearby"
        }
    }

    fun reset() {
        stop()
        found.clear()
        _devices.value = emptyList()
        _status.value = ""
        _adapterOff.value = false
    }

    private fun merge(result: ScanResult) {
        val record = result.scanRecord
        val address = result.device?.address ?: return

        val manufacturer = record?.manufacturerSpecificData
        val companyId = if (manufacturer != null && manufacturer.size() > 0) {
            manufacturer.keyAt(0)
        } else {
            null
        }

        val txPower = record?.txPowerLevel
            ?.takeIf { it != TX_POWER_ABSENT }

        val sighting = BleDevice(
            address = address.uppercase(),
            name = record?.deviceName?.takeIf { it.isNotBlank() },
            rssiDbm = result.rssi,
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
            manufacturerName = companyId?.let(CompanyIds::name),
            companyId = companyId,
            txPowerDbm = txPower,
            connectable = result.isConnectable,
            addressIsRandom = MACAddress.parse(address)?.isLocallyAdministered ?: false,
        )

        val existing = found[address]
        found[address] = if (existing == null) {
            sighting
        } else {
            // Keep the earliest first-seen and the fullest name, but take the
            // freshest signal and any late-arriving manufacturer / service data.
            existing.copy(
                name = existing.name ?: sighting.name,
                rssiDbm = sighting.rssiDbm,
                serviceUuids = sighting.serviceUuids.ifEmpty { existing.serviceUuids },
                manufacturerName = existing.manufacturerName ?: sighting.manufacturerName,
                companyId = existing.companyId ?: sighting.companyId,
                txPowerDbm = sighting.txPowerDbm ?: existing.txPowerDbm,
                lastSeen = sighting.lastSeen,
            )
        }

        _devices.value = found.values.sortedByDescending { it.rssiDbm }
    }

    private companion object {
        const val SCAN_WINDOW_MS = 12_000L

        /** ScanRecord returns this sentinel when no TX power is advertised. */
        const val TX_POWER_ABSENT = Int.MIN_VALUE
    }
}

/**
 * A small slice of the Bluetooth SIG's company-identifier registry.
 *
 * The full list has thousands of entries; only the assignments common on a home
 * or office network are worth carrying, and an unknown id simply shows as its
 * number.
 */
private object CompanyIds {
    private val names = mapOf(
        0x004C to "Apple",
        0x0006 to "Microsoft",
        0x00E0 to "Google",
        0x0075 to "Samsung",
        0x0087 to "Garmin",
        0x0157 to "Anhui Huami (Amazfit)",
        0x0059 to "Nordic Semiconductor",
        0x000F to "Broadcom",
        0x001D to "Qualcomm",
        0x0499 to "Ruuvi",
        0x0171 to "Amazon",
        0x0131 to "Cypress",
        0x02E5 to "Espressif",
        0x0110 to "Nippon Seiki",
        0x038F to "Xiaomi",
        0x0180 to "Logitech",
        0x00D7 to "Qualcomm Connected Experiences",
        0x0201 to "GN Netcom (Jabra)",
        0x004F to "APT (Fitbit)",
        0x0154 to "Fitbit",
        0x022B to "Tesla",
        0x0118 to "Sony",
        0x008A to "Bose",
        0x05A7 to "Sonos",
    )

    fun name(companyId: Int): String? = names[companyId]
}
