//
//  ArpTable.kt
//  AssetOnBoard
//
//  Best-effort read of the kernel ARP cache via /proc/net/arp.
//
//  This is the classic way to map IP -> MAC and it still works on emulators and
//  on rooted or older devices. Android 10 moved /proc/net behind a SELinux
//  policy that denies untrusted apps, so on a current handset the read simply
//  fails — treat a result as a bonus rather than a dependency. mDNS, NetBIOS and
//  the router's own SNMP table remain the reliable MAC sources on-device, which
//  mirrors how the iOS build treats the equivalent routing sysctl.
//

package com.sclera.sclerago.services.scan.core

import java.io.File

object ArpTable {

    /** Returns whatever IP-to-MAC bindings the kernel is willing to share. */
    fun current(): Map<IPv4, MACAddress> {
        val file = File("/proc/net/arp")
        val lines = try {
            if (!file.canRead()) return emptyMap()
            file.readLines()
        } catch (_: Exception) {
            return emptyMap()
        }
        return parse(lines)
    }

    /**
     * Parses the fixed-column table, skipping its header row.
     *
     * ```
     * IP address       HW type     Flags       HW address            Mask     Device
     * 192.168.1.1      0x1         0x2         a4:2b:8c:11:22:33     *        wlan0
     * ```
     * Flags bit 0x2 is ATF_COM — a completed entry. Incomplete rows carry an
     * all-zero hardware address and are dropped.
     */
    internal fun parse(lines: List<String>): Map<IPv4, MACAddress> {
        val results = LinkedHashMap<IPv4, MACAddress>()

        for (line in lines.drop(1)) {
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 4) continue

            val address = IPv4.parse(fields[0]) ?: continue
            val flags = fields[2].removePrefix("0x").toIntOrNull(16) ?: continue
            if (flags and 0x2 == 0) continue

            val mac = MACAddress.parse(fields[3]) ?: continue
            if (mac.isAllZero || mac.isMulticast) continue

            results[address] = mac
        }
        return results
    }
}
