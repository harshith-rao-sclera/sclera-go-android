//
//  GatewayDiscovery.kt
//  AssetOnBoard
//
//  Finds the real default gateway.
//
//  Assuming the router lives at x.x.x.1 is a guess that quietly fails: plenty of
//  networks put a workstation there. The iOS build has to infer the first hop
//  from an ICMP time-exceeded reply, because Darwin blocks the routing sysctl.
//  Android does not need the trick — ConnectivityManager publishes the routing
//  table for the active network, so the default route's gateway can simply be
//  read. DHCP lease data is the fallback for the rare device whose LinkProperties
//  carry no default route (some tethered and Ethernet-dongle setups).
//
//  The ICMP TTL-1 probe the iOS build uses is deliberately absent: Linux ping
//  sockets deliver ICMP errors only through MSG_ERRQUEUE, which android.system.Os
//  does not expose, so a time-exceeded reply would never reach us.
//

package com.sclera.sclerago.services.scan.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager

object GatewayDiscovery {

    /** Returns the first-hop router, or null when the platform will not say. */
    fun discover(context: Context?): IPv4? {
        context ?: return null
        return fromRoutingTable(context) ?: fromDhcpLease(context)
    }

    private fun fromRoutingTable(context: Context): IPv4? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null
        return NetworkInterfaceScanner.defaultGateway(properties)
    }

    /**
     * Reads the gateway out of the current DHCP lease.
     *
     * `WifiManager.getDhcpInfo` is deprecated and returns the address in host
     * little-endian order, but it is still the only source that answers on some
     * OEM builds where the default route is installed without a gateway.
     */
    @Suppress("DEPRECATION")
    private fun fromDhcpLease(context: Context): IPv4? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val info = try {
            wifi.dhcpInfo
        } catch (_: SecurityException) {
            null
        } ?: return null
        if (info.gateway == 0) return null
        // DhcpInfo stores addresses least-significant-octet first.
        val little = info.gateway
        val raw = (little and 0xFF shl 24) or
            (little ushr 8 and 0xFF shl 16) or
            (little ushr 16 and 0xFF shl 8) or
            (little ushr 24 and 0xFF)
        val candidate = IPv4(raw)
        return if (candidate.isLoopback || candidate.raw == 0) null else candidate
    }
}
