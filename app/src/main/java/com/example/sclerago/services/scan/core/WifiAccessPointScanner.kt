//
//  WifiAccessPointScanner.kt
//  AssetOnBoard
//
//  The over-the-air Wi-Fi scan: the access points in range, not the hosts on the
//  joined network.
//
//  Android delivers scan results asynchronously through a broadcast, and throttles
//  how often an app may ask for a fresh sweep. So the flow here is: publish the
//  cached results straight away for something to show, ask for a refresh, then
//  wait for the broadcast (or a timeout) before settling. State is published as
//  StateFlows to match [NetworkScanner].
//

package com.example.sclerago.services.scan.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.example.sclerago.services.scan.data.OuiDatabase
import com.example.sclerago.services.scan.model.WifiAccessPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WifiAccessPointScanner(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _accessPoints = MutableStateFlow<List<WifiAccessPoint>>(emptyList())
    val accessPoints: StateFlow<List<WifiAccessPoint>> = _accessPoints.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Set when Wi-Fi is switched off, which returns an empty list regardless. */
    private val _adapterOff = MutableStateFlow(false)
    val adapterOff: StateFlow<Boolean> = _adapterOff.asStateFlow()

    val supported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    private val wifiManager: WifiManager?
        get() = context.applicationContext.getSystemService(WifiManager::class.java)

    private var job: Job? = null
    private var receiver: BroadcastReceiver? = null

    /** Caller must hold [ScanPermissions.Surface.WIFI] before calling this. */
    fun start() {
        if (_isScanning.value) return
        val wifi = wifiManager
        if (wifi == null || !supported) {
            _status.value = "This device has no Wi-Fi radio."
            return
        }
        if (!wifi.isWifiEnabled) {
            _adapterOff.value = true
            _status.value = "Turn on Wi-Fi to scan for networks."
            return
        }
        _adapterOff.value = false
        _isScanning.value = true
        _status.value = "Scanning for networks…"

        // Show whatever the framework already has cached, then ask for a refresh.
        publish(wifi)

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                publish(wifi)
            }
        }
        receiver = listener
        // SCAN_RESULTS_AVAILABLE is a protected system broadcast; NOT_EXPORTED is
        // correct and satisfies the export-flag requirement from Android 14 on.
        ContextCompat.registerReceiver(
            context.applicationContext,
            listener,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        @Suppress("DEPRECATION")
        val accepted = try {
            wifi.startScan()
        } catch (_: SecurityException) {
            false
        }

        job = scope.launch {
            // The broadcast usually lands within a few seconds; give it a window,
            // then settle with whatever the last publish produced.
            delay(if (accepted) SCAN_WINDOW_MS else SETTLE_MS)
            publish(wifi)
            finish()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        unregister()
        if (_isScanning.value) finish()
    }

    fun reset() {
        stop()
        _accessPoints.value = emptyList()
        _status.value = ""
        _adapterOff.value = false
    }

    private fun finish() {
        unregister()
        _isScanning.value = false
        val count = _accessPoints.value.size
        _status.value = "$count network${if (count == 1) "" else "s"} in range"
    }

    private fun unregister() {
        receiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered; nothing to undo.
            }
        }
        receiver = null
    }

    private fun publish(wifi: WifiManager) {
        val currentBssid = try {
            @Suppress("DEPRECATION")
            wifi.connectionInfo?.bssid
        } catch (_: SecurityException) {
            null
        }

        val results = try {
            @Suppress("DEPRECATION")
            wifi.scanResults
        } catch (_: SecurityException) {
            emptyList()
        }

        _accessPoints.value = results
            .mapNotNull { result ->
                val bssid = result.BSSID ?: return@mapNotNull null
                @Suppress("DEPRECATION")
                val ssid = result.SSID ?: ""
                val mac = MACAddress.parse(bssid)
                WifiAccessPoint(
                    ssid = ssid.trim(),
                    bssid = bssid.uppercase(),
                    rssiDbm = result.level,
                    frequencyMHz = result.frequency,
                    securityLabel = securityLabel(result.capabilities ?: ""),
                    isCurrentNetwork = currentBssid != null &&
                        bssid.equals(currentBssid, ignoreCase = true),
                    vendor = mac?.takeIf { !it.isLocallyAdministered }
                        ?.let { OuiDatabase.vendor(it) },
                )
            }
            // Strongest first, but keep the joined network pinned to the top.
            .sortedWith(compareByDescending<WifiAccessPoint> { it.isCurrentNetwork }
                .thenByDescending { it.rssiDbm })
    }

    private companion object {
        const val SCAN_WINDOW_MS = 8_000L
        const val SETTLE_MS = 1_500L

        fun securityLabel(capabilities: String): String = when {
            capabilities.contains("WPA3") || capabilities.contains("SAE") -> "WPA3"
            capabilities.contains("OWE") -> "Enhanced Open"
            capabilities.contains("WPA2") || capabilities.contains("RSN") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }
}
