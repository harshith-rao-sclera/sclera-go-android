package com.example.sclerago.services.scan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sclerago.data.Device
import com.example.sclerago.services.scan.core.BleScanner
import com.example.sclerago.services.scan.core.NetworkScanner
import com.example.sclerago.services.scan.core.ScanDepth
import com.example.sclerago.services.scan.core.ScanPermissions
import com.example.sclerago.services.scan.core.WifiAccessPointScanner
import com.example.sclerago.services.scan.model.BleDevice
import com.example.sclerago.services.scan.model.NetworkDevice
import com.example.sclerago.services.scan.model.WifiAccessPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs the three real scans (Bluetooth, Wi-Fi network sweep, access points; the scanners in core/
 * are ported from AssetOnBoard) and holds what the scan pages need from them: the devices found as
 * rows, whether each scan is running, why one can't run, and the text for its radar card.
 *
 * State is Compose snapshot state, so screens reading it recompose as scans progress. Runtime
 * permissions can only be asked from the UI: when a scan needs one, [permissionRequest] is set,
 * the UI shows Android's prompt, and the answer comes back through [onPermissionResult].
 */
class ScanService(private val app: Application, private val scope: CoroutineScope) {

    /** The three scans. The Network tab's two modes are [NETWORK] and [ACCESS_POINTS]. */
    enum class Target { BLUETOOTH, NETWORK, ACCESS_POINTS }

    /** Title and detail line for a scan page's radar card. */
    data class Card(val title: String, val sub: String)

    private val ble = BleScanner(app, scope)
    private val lan = NetworkScanner(app, scope)
    private val aps = WifiAccessPointScanner(app, scope)

    private var bleFound by mutableStateOf<List<BleDevice>>(emptyList())
    private var lanFound by mutableStateOf<List<NetworkDevice>>(emptyList())
    private var apFound by mutableStateOf<List<WifiAccessPoint>>(emptyList())
    private var lanSubnet by mutableStateOf<String?>(null)

    private var scanning by mutableStateOf(emptySet<Target>())

    /** Why a scan could not run (radio off, permission refused…); shown on its card until the next try. */
    private var issues by mutableStateOf(emptyMap<Target, String>())

    /** Scans the user paused themselves (as opposed to finishing on their own). */
    private var paused by mutableStateOf(emptySet<Target>())

    /** The scan waiting on a runtime permission prompt. */
    private var asking by mutableStateOf<Target?>(null)

    /** Permissions the UI should ask for now, or null; answer with [onPermissionResult]. */
    val permissionRequest: List<String>?
        get() = asking?.let { ScanPermissions.required(surface(it)) }

    init {
        scope.launch { ble.devices.collect { bleFound = it } }
        scope.launch { ble.isScanning.collect { setScanning(Target.BLUETOOTH, it) } }
        scope.launch { lan.devices.collect { lanFound = it } }
        scope.launch { lan.subnet.collect { lanSubnet = it?.toString() } }
        scope.launch {
            lan.phase.collect { p ->
                setScanning(Target.NETWORK, p.isScanning)
                if (p is NetworkScanner.Phase.Failed) issues = issues + (Target.NETWORK to p.label)
            }
        }
        scope.launch { aps.accessPoints.collect { apFound = it } }
        scope.launch { aps.isScanning.collect { setScanning(Target.ACCESS_POINTS, it) } }
    }

    private fun setScanning(t: Target, on: Boolean) {
        scanning = if (on) scanning + t else scanning - t
    }

    fun isScanning(t: Target): Boolean = t in scanning

    /** What [t] has found so far, as scan page rows. */
    fun rows(t: Target): List<Device> = when (t) {
        Target.BLUETOOTH -> bleFound.map { it.toRow() }
        Target.NETWORK -> lanFound.sortedBy { it.ip }.map { it.toRow() }
        Target.ACCESS_POINTS -> apFound.map { it.toRow() }
    }

    /**
     * Starts [t] unless it is already running: asks for its permission first if needed, and records
     * why it can't run when the radio is off, location is off or there is no network.
     */
    fun start(t: Target) {
        if (isScanning(t)) return
        paused = paused - t
        if (!ScanPermissions.granted(app, surface(t))) {
            asking = t
            return
        }
        // Android returns no access points (and, before Android 12, no Bluetooth results) with location off.
        if (ScanPermissions.needsLocationServices(surface(t)) && !ScanPermissions.locationServicesEnabled(app)) {
            issue(t, if (t == Target.ACCESS_POINTS) "Turn on Location to see access points" else "Turn on Location to scan for Bluetooth devices")
            return
        }
        issue(t, null)
        when (t) {
            Target.BLUETOOTH -> {
                ble.start()
                // start() returns without scanning when the radio is off or missing; say why.
                if (!ble.isScanning.value) issue(t, ble.status.value.removeSuffix(".").ifBlank { null })
            }
            Target.ACCESS_POINTS -> {
                aps.start()
                if (!aps.isScanning.value) issue(t, aps.status.value.removeSuffix(".").ifBlank { null })
            }
            // A missing Wi-Fi / Ethernet connection shows up as a failed phase (see init).
            Target.NETWORK -> lan.start(ScanDepth.STANDARD)
        }
    }

    /** Stops [t]; [byUser] marks it paused rather than finished. */
    fun stop(t: Target, byUser: Boolean = false) {
        if (byUser && isScanning(t)) paused = paused + t
        when (t) {
            Target.BLUETOOTH -> ble.stop()
            Target.NETWORK -> lan.stop()
            Target.ACCESS_POINTS -> aps.stop()
        }
    }

    /** Tap on the radar card: pause [t] if it is running, otherwise start it. */
    fun toggle(t: Target) = if (isScanning(t)) stop(t, byUser = true) else start(t)

    /** The answer to [permissionRequest]: start the scan that asked, or say why it can't run. */
    fun onPermissionResult(granted: Boolean) {
        val t = asking ?: return
        asking = null
        if (granted) start(t) else issue(
            t, when (t) {
                Target.BLUETOOTH -> "Allow Nearby devices to scan for Bluetooth devices"
                Target.NETWORK -> "Allow access to devices on your local network to scan it"
                Target.ACCESS_POINTS -> "Allow Location to see access points"
            },
        )
    }

    /** The radar card for [t], given how many rows it shows and how many of those are not assets yet. */
    fun card(t: Target, found: Int, new: Int): Card {
        val what = when (t) {
            Target.BLUETOOTH -> "$found found"
            Target.NETWORK -> listOfNotNull(lanSubnet, "$found devices").joinToString(" · ")
            Target.ACCESS_POINTS -> "$found access points"
        }
        val issue = issues[t]
        return when {
            isScanning(t) -> Card(
                when (t) {
                    Target.BLUETOOTH -> "Scanning nearby…"
                    Target.NETWORK -> "Scanning Wi-Fi network…"
                    Target.ACCESS_POINTS -> "Looking for access points…"
                },
                "$what · $new new · tap to pause",
            )
            issue != null -> Card("Can't scan", "$issue · tap to try again")
            t in paused -> Card("Scan paused", "Tap to scan again")
            found > 0 -> Card("Scan complete", "$what · $new new · tap to scan again")
            else -> Card("Scan paused", "Tap to scan again")
        }
    }

    /** Releases the radios and sockets; call when the owner goes away. */
    fun shutdown() {
        ble.stop()
        aps.stop()
        lan.stop()
    }

    private fun issue(t: Target, text: String?) {
        issues = if (text == null) issues - t else issues + (t to text)
    }

    private fun surface(t: Target) = when (t) {
        Target.BLUETOOTH -> ScanPermissions.Surface.BLE
        Target.NETWORK -> ScanPermissions.Surface.LAN
        Target.ACCESS_POINTS -> ScanPermissions.Surface.WIFI
    }
}
