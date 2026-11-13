package com.sclera.sclerago.services.scan

import com.sclera.sclerago.domain.model.Device
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sclera.sclerago.services.scan.core.BleScanner
import com.sclera.sclerago.services.scan.core.NetworkScanner
import com.sclera.sclerago.services.scan.core.ScanDepth
import com.sclera.sclerago.services.scan.core.ScanPermissions
import com.sclera.sclerago.services.scan.core.WifiAccessPointScanner
import com.sclera.sclerago.services.scan.model.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

/**
 * Runs the three real scans (Bluetooth, Wi-Fi network sweep, access points; the scanners in core/
 * are ported from AssetOnBoard) and holds what the scan pages need from them: the devices found as
 * rows, whether each scan is running, why one can't run, and the text for its radar card.
 *
 * State is Compose snapshot state, so screens reading it recompose as scans progress. Runtime
 * permissions can only be asked from the UI: when a scan needs one, [permissionAsk] / [permissionRequest]
 * are set, the UI explains why and shows Android's prompt, and the answer comes back through
 * [onPermissionResult]. A scan whose permission was refused is [isBlocked]: its page shows how to allow
 * it rather than asking again every time the tab opens.
 */
class ScanService(private val app: Application, private val scope: CoroutineScope) {

    /** The three scans. The Network tab's two modes are [NETWORK] and [ACCESS_POINTS]. */
    enum class Target { BLUETOOTH, NETWORK, ACCESS_POINTS }

    /**
     * Title and detail line for a scan page's radar card, plus how far a running scan has got
     * ([progress] 0–1, null when not scanning).
     */
    data class Card(val title: String, val sub: String, val progress: Float? = null)

    private val ble = BleScanner(app, scope)
    // The network sweep handles tens of thousands of probe results and re-fingerprints hosts as they
    // come in; on the main thread that made the scan pages stutter. It runs on a background
    // dispatcher, still cancelled with [scope] (its results are handled one at a time, so its own
    // bookkeeping needs no extra locking). Bluetooth and access points stay on [scope]: their
    // callbacks arrive on the main thread anyway and are cheap.
    private val lan = NetworkScanner(app, CoroutineScope(scope.coroutineContext + Dispatchers.Default))
    private val aps = WifiAccessPointScanner(app, scope)

    // What each scan has found, already turned into page rows (off the main thread) and refreshed at
    // most a few times a second, however fast results arrive.
    private var bleRows by mutableStateOf<List<Device>>(emptyList())
    private var lanRows by mutableStateOf<List<Device>>(emptyList())
    private var apRows by mutableStateOf<List<Device>>(emptyList())
    private var lanSubnet by mutableStateOf<String?>(null)

    private var scanning by mutableStateOf(emptySet<Target>())

    /** Why a scan could not run (radio off, permission refused…); shown on its card until the next try. */
    private var issues by mutableStateOf(emptyMap<Target, String>())

    /** Scans the user paused themselves (as opposed to finishing on their own). */
    private var paused by mutableStateOf(emptySet<Target>())

    /** The scan waiting on a runtime permission prompt, or null; answer with [onPermissionResult]. */
    var permissionAsk by mutableStateOf<Target?>(null)
        private set

    /** The Android permissions [permissionAsk] needs. */
    val permissionRequest: List<String>?
        get() = permissionAsk?.let { ScanPermissions.required(surface(it)) }

    /** Scans whose permission the user refused (or put off with "Not now"). */
    private var blocked by mutableStateOf(emptySet<Target>())

    /** [t] can't run until its permission is allowed; its page shows how. */
    fun isBlocked(t: Target): Boolean = t in blocked

    /** How far each running scan has got, 0–1. Declared before init: the scanners report from the start. */
    private var progress by mutableStateOf(emptyMap<Target, Float>())
    private val clocks = mutableMapOf<Target, Job>()

    init {
        scope.launch {
            // Strongest first, but by signal bars rather than raw dBm (which flickers every advert),
            // then in the order they were found: rows stay put instead of jumping around the list.
            ble.devices.asRows { list ->
                list.sortedWith(compareByDescending<BleDevice> { it.signalBars }.thenBy { it.firstSeen }).map { it.toRow() }
            }.collect { bleRows = it }
        }
        scope.launch { ble.isScanning.collect { setScanning(Target.BLUETOOTH, it) } }
        scope.launch { lan.devices.asRows { list -> list.sortedBy { it.ip }.map { it.toRow() } }.collect { lanRows = it } }
        scope.launch { lan.subnet.collect { lanSubnet = it?.toString() } }
        scope.launch {
            lan.phase.collect { p ->
                setScanning(Target.NETWORK, p.isScanning)
                if (p is NetworkScanner.Phase.Failed) issues = issues + (Target.NETWORK to p.label)
            }
        }
        scope.launch { aps.accessPoints.asRows { list -> list.map { it.toRow() } }.collect { apRows = it } }
        scope.launch { aps.isScanning.collect { setScanning(Target.ACCESS_POINTS, it) } }
        scope.launch { lan.progress.collect { setProgress(Target.NETWORK, it) } }
    }

    private fun setScanning(t: Target, on: Boolean) {
        scanning = if (on) scanning + t else scanning - t
        // The radio scans run for a fixed window, so their progress is the time gone. The network
        // sweep reports its own (see init).
        if (t == Target.NETWORK) return
        clocks.remove(t)?.cancel()
        if (!on) { progress = progress - t; return }
        val window = if (t == Target.BLUETOOTH) ble.scanWindowMs else aps.scanWindowMs
        val started = System.currentTimeMillis()
        clocks[t] = scope.launch {
            while (true) {
                // Held short of 100 % until the scan actually stops.
                setProgress(t, ((System.currentTimeMillis() - started).toFloat() / window).coerceAtMost(0.99f))
                delay(250)
            }
        }
    }

    private fun setProgress(t: Target, p: Float) {
        progress = progress + (t to p.coerceIn(0f, 1f))
    }

    fun isScanning(t: Target): Boolean = t in scanning

    /** What [t] has found so far, as scan page rows (built once per refresh, not on every read). */
    fun rows(t: Target): List<Device> = when (t) {
        Target.BLUETOOTH -> bleRows
        Target.NETWORK -> lanRows
        Target.ACCESS_POINTS -> apRows
    }

    /**
     * A scanner's results as page rows: the first list straight away, then at most one every
     * [ROW_REFRESH_MS] (always the latest, nothing in between is kept), built on a background thread.
     */
    private fun <T> Flow<List<T>>.asRows(build: (List<T>) -> List<Device>): Flow<List<Device>> =
        conflate()
            .transform { emit(it); delay(ROW_REFRESH_MS) }
            .map(build)
            .flowOn(Dispatchers.Default)

    /**
     * Starts [t] unless it is already running: asks for its permission first if needed, and records
     * why it can't run when the radio is off, location is off or there is no network.
     *
     * [auto] is for starts the user didn't ask for directly (opening a tab): those don't ask again for a
     * permission that was refused, so the page can show how to allow it instead of re-prompting each visit.
     */
    fun start(t: Target, auto: Boolean = false) {
        if (isScanning(t)) return
        paused = paused - t
        if (!ScanPermissions.granted(app, surface(t))) {
            if (!(auto && t in blocked)) permissionAsk = t
            return
        }
        // Allowed now (perhaps in Settings or on the Permissions page), so no longer blocked.
        blocked = blocked - t
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

    /** The answer to [permissionAsk] ("Not now" counts as no): start the scan that asked, or block it. */
    fun onPermissionResult(granted: Boolean) {
        val t = permissionAsk ?: return
        permissionAsk = null
        if (granted) start(t) else blocked = blocked + t
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
                progress = progress[t] ?: 0f,
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

    private companion object {
        /** How often a scan page's list may change while results pour in. */
        const val ROW_REFRESH_MS = 500L
    }
}
