//
//  ScanPermissions.kt
//  AssetOnBoard
//
//  The runtime permissions each over-the-air scan needs, resolved per API level.
//
//  The LAN scan asks for nothing at runtime — every permission it uses is granted
//  at install. The radio scans are different: Android gates both Wi-Fi scan
//  results and BLE advertisements behind runtime permissions, because either can
//  betray the user's location. This is the one place that maps a [Surface] to the
//  exact permission strings to request, so the UI does not have to know the
//  version rules.
//

package com.example.sclerago.scan.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object ScanPermissions {

    /** Which scan a permission set belongs to. [LAN] is the Wi-Fi network sweep (ScleraGo addition). */
    enum class Surface { WIFI, BLE, LAN }

    /**
     * Android 17's local network permission. Apps targeting API 37+ (ScleraGo does) cannot ping,
     * connect to, or hear mDNS from other devices on the LAN without it; older targets get it implicitly.
     * Spelled out because the constant only exists in the API 37 SDK.
     */
    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val API_37 = 37

    /**
     * The permissions [surface] needs on this device.
     *
     * Wi-Fi scan results require fine location on every supported release. BLE
     * needs the dedicated `BLUETOOTH_SCAN` from Android 12; before that it, too,
     * fell back to location, with the classic `BLUETOOTH*` permissions granted at
     * install.
     */
    fun required(surface: Surface): List<String> = when (surface) {
        Surface.WIFI -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        Surface.BLE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        // Before Android 17 the LAN sweep needs only install-time permissions.
        Surface.LAN -> if (Build.VERSION.SDK_INT >= API_37) listOf(ACCESS_LOCAL_NETWORK) else emptyList()
    }

    fun granted(context: Context, surface: Surface): Boolean =
        required(surface).all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * Whether the device's location master switch is on.
     *
     * Even with the permission granted, Android returns an empty Wi-Fi scan list
     * — and no BLE results before Android 12 — while location services are off,
     * so the UI needs to tell that apart from "nothing in range".
     */
    fun locationServicesEnabled(context: Context): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Whether location must be on for [surface] to return anything on this API level. */
    fun needsLocationServices(surface: Surface): Boolean = when (surface) {
        Surface.WIFI -> true
        Surface.BLE -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        Surface.LAN -> false
    }
}
