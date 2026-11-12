//
//  NetworkInterfaces.kt
//  AssetOnBoard
//
//  Enumerates the device's own IPv4 interfaces.
//
//  Two sources, in order of quality. ConnectivityManager's LinkProperties give
//  the authoritative picture for the network Android considers active: the exact
//  prefix length, the interface name, the default-route gateway and the
//  transport type. java.net.NetworkInterface is the fallback — it sees every
//  interface but has to guess the transport from the BSD name, the same way the
//  iOS build does.
//

package com.sclera.sclerago.services.scan.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

data class LocalInterface(
    val name: String,
    val address: IPv4,
    val prefixLength: Int,
    val kind: Kind,
    /** Default-route gateway, when the platform told us one. */
    val routerAddress: IPv4? = null,
    /**
     * Resolvers this link was handed.
     *
     * On a corporate network these usually sit in a server segment rather than on
     * the user's own, which makes them free evidence of a subnet no sweep of the
     * local space would ever find.
     */
    val dnsServers: List<IPv4> = emptyList(),
    /** The DHCP server that issued this lease, where the platform exposes it. */
    val dhcpServer: IPv4? = null,
    /** On-link and static routes other than this interface's own subnet. */
    val extraRoutes: List<IPv4Subnet> = emptyList(),
) {
    enum class Kind(val label: String) {
        WIFI("Wi-Fi"),
        WIRED("Ethernet"),
        CELLULAR("Cellular"),
        HOTSPOT("Personal Hotspot"),
        VPN("VPN"),
        AWDL("Wi-Fi Direct"),
        OTHER("Other"),
    }

    val id: String get() = "$name-$address"

    val subnet: IPv4Subnet get() = IPv4Subnet.of(address, prefixLength)

    /**
     * Interfaces worth sweeping: a real LAN segment we are attached to.
     *
     * Cellular has no local peers, VPN tunnels are point-to-point, and Wi-Fi
     * Direct / Aware links are peer-to-peer rather than the network the user
     * joined.
     */
    val isScannable: Boolean
        get() = when (kind) {
            Kind.WIFI, Kind.WIRED, Kind.HOTSPOT -> true
            Kind.CELLULAR, Kind.VPN, Kind.AWDL, Kind.OTHER -> false
        }

    companion object {
        fun kindForInterfaceName(name: String): Kind = when {
            name.startsWith("wlan") -> Kind.WIFI
            name.startsWith("eth") -> Kind.WIRED
            name.startsWith("usb") || name.startsWith("rndis") -> Kind.WIRED
            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("bridge") -> Kind.HOTSPOT
            name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") -> Kind.CELLULAR
            name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec") -> Kind.VPN
            name.startsWith("p2p") || name.startsWith("aware") -> Kind.AWDL
            else -> Kind.OTHER
        }
    }
}

object NetworkInterfaceScanner {

    /**
     * The interface a LAN scan should run on.
     *
     * With no [preferred] kind the active network wins, then Wi-Fi, wired and
     * hotspot in that order — the historical default. Passing a [preferred] kind
     * (to force the scan onto Ethernet, say) selects an interface of that kind
     * when one is attached, and otherwise falls back to the same ordering so the
     * scan still runs on something.
     *
     * A [Context] is optional so the pure-Java path stays unit-testable, but
     * passing one gets the exact prefix and the real gateway instead of a guess.
     */
    fun preferredScanInterface(
        context: Context?,
        preferred: LocalInterface.Kind? = null,
    ): LocalInterface? {
        val candidates = scannable(context)
        if (preferred != null) {
            candidates.firstOrNull { it.kind == preferred }?.let { return it }
        } else {
            activeInterface(context)?.let { if (it.isScannable) return it }
        }
        for (kind in listOf(
            LocalInterface.Kind.WIFI,
            LocalInterface.Kind.WIRED,
            LocalInterface.Kind.HOTSPOT,
        )) {
            candidates.firstOrNull { it.kind == kind }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    /**
     * Every scannable interface currently attached, richest record first.
     *
     * The active network is listed ahead of the rest because its record carries
     * the exact prefix and real gateway; the plain-Java enumeration fills in any
     * other wired or wireless segments the device is also on. Deduplicated by
     * interface name so the active one is not listed twice.
     */
    fun scannable(context: Context?): List<LocalInterface> {
        val byName = LinkedHashMap<String, LocalInterface>()
        activeInterface(context)?.takeIf { it.isScannable }?.let { byName[it.name] = it }
        for (iface in current().filter { it.isScannable }) {
            byName.putIfAbsent(iface.name, iface)
        }
        return byName.values.toList()
    }

    /** The network Android currently routes traffic over, if it is a LAN. */
    private fun activeInterface(context: Context?): LocalInterface? {
        context ?: return null
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null
        val capabilities = manager.getNetworkCapabilities(network)

        val linkAddress = properties.linkAddresses.firstOrNull { entry ->
            val address = entry.address
            address is Inet4Address && !address.isLoopbackAddress && !address.isAnyLocalAddress
        } ?: return null

        val parsed = IPv4.from(linkAddress.address) ?: return null
        val name = properties.interfaceName ?: "unknown"

        val prefixLength = linkAddress.prefixLength.coerceIn(1, 32)
        return LocalInterface(
            name = name,
            address = parsed,
            prefixLength = prefixLength,
            kind = kind(capabilities, name),
            routerAddress = defaultGateway(properties),
            dnsServers = properties.dnsServers
                .filterIsInstance<Inet4Address>()
                .mapNotNull { IPv4.from(it) }
                .filterNot { it.isLoopback },
            dhcpServer = dhcpServer(properties),
            extraRoutes = staticRoutes(properties, IPv4Subnet.of(parsed, prefixLength)),
        )
    }

    /** The DHCP server of this lease; only exposed from Android 11 on. */
    private fun dhcpServer(properties: LinkProperties): IPv4? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val server = properties.dhcpServerAddress ?: return null
        return IPv4.from(server)?.takeUnless { it.isLoopback || it.raw == 0 }
    }

    /**
     * Routes this link carries beyond its own subnet and the default.
     *
     * A plain Wi-Fi link has none; a VPN or a network that pushes classless
     * static routes (DHCP option 121) has one per reachable segment, which is the
     * platform handing us the answer outright.
     */
    private fun staticRoutes(
        properties: LinkProperties,
        own: IPv4Subnet,
    ): List<IPv4Subnet> = properties.routes.mapNotNull { route ->
        if (route.isDefaultRoute) return@mapNotNull null
        val destination = route.destination
        val network = IPv4.from(destination.address) ?: return@mapNotNull null
        val prefix = destination.prefixLength
        if (prefix !in 1..32) return@mapNotNull null
        val subnet = IPv4Subnet.of(network, prefix)
        if (subnet == own) null else subnet
    }

    /** The IPv4 gateway of the default route, straight from the routing table. */
    fun defaultGateway(properties: LinkProperties): IPv4? =
        properties.routes
            .asSequence()
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { IPv4.from(it) }
            .firstOrNull { !it.isLoopback && it.raw != 0 }

    private fun kind(capabilities: NetworkCapabilities?, name: String): LocalInterface.Kind {
        capabilities ?: return LocalInterface.kindForInterfaceName(name)
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> LocalInterface.Kind.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LocalInterface.Kind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LocalInterface.Kind.WIRED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LocalInterface.Kind.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> LocalInterface.Kind.AWDL
            else -> LocalInterface.kindForInterfaceName(name)
        }
    }

    /** Returns every up-and-running IPv4 interface, loopback excluded. */
    fun current(): List<LocalInterface> {
        val results = mutableListOf<LocalInterface>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: Exception) {
            return emptyList()
        }

        for (nif in interfaces) {
            val usable = try {
                nif.isUp && !nif.isLoopback
            } catch (_: Exception) {
                false
            }
            if (!usable) continue

            for (entry in nif.interfaceAddresses) {
                val address = entry.address
                if (address !is Inet4Address) continue
                if (address.isLoopbackAddress || address.isAnyLocalAddress) continue
                val parsed = IPv4.from(address) ?: continue
                val prefix = entry.networkPrefixLength.toInt()
                if (prefix !in 1..32) continue

                results += LocalInterface(
                    name = nif.name,
                    address = parsed,
                    prefixLength = prefix,
                    kind = LocalInterface.kindForInterfaceName(nif.name),
                )
            }
        }
        return results
    }

    /** The [NetworkInterface] carrying [address], needed to bind multicast. */
    fun javaInterface(address: IPv4): NetworkInterface? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: Exception) {
            return null
        }
        return interfaces.firstOrNull { nif ->
            nif.inetAddresses.toList().any { IPv4.from(it) == address }
        }
    }
}
