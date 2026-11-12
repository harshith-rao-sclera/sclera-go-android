//
//  NetworkDevice.kt
//  AssetOnBoard
//
//  The merged picture of one host, assembled from every probe that saw it.
//

package com.example.sclerago.services.scan.model

import com.example.sclerago.services.scan.core.BonjourService
import com.example.sclerago.services.scan.core.IPv4
import com.example.sclerago.services.scan.core.MACAddress
import com.example.sclerago.services.scan.core.NameResolution
import com.example.sclerago.services.scan.core.NetBiosInfo
import com.example.sclerago.services.scan.core.PortObservation
import kotlinx.serialization.Serializable

@Serializable
enum class DiscoveryMethod(val label: String) {
    ICMP("ICMP ping"),
    TCP("TCP probe"),
    BONJOUR("Bonjour / mDNS"),
    ARP("ARP cache"),
    NETBIOS("NetBIOS"),
    REVERSE_DNS("Reverse DNS"),
    SNMP("Router SNMP"),
}

@Serializable
enum class DeviceCategory(val label: String) {
    ROUTER("Router / Gateway"),
    ACCESS_POINT("Access Point"),
    COMPUTER("Computer"),
    PHONE("Phone"),
    TABLET("Tablet"),
    TV("TV / Streaming"),
    SPEAKER("Speaker"),
    PRINTER("Printer"),
    CAMERA("Camera"),
    NAS("Network Storage"),
    GAME_CONSOLE("Game Console"),
    SMART_HOME("Smart Home"),
    SERVER("Server"),
    VIRTUAL_MACHINE("Virtual Machine"),
    WEARABLE("Wearable"),
    UNKNOWN("Unidentified"),
    ;

    companion object {
        /** Resolves the human label used by the packed model catalogue. */
        fun fromLabel(label: String): DeviceCategory? = entries.firstOrNull { it.label == label }
    }
}

@Serializable
data class NetworkDevice(
    val ip: IPv4,
    val macAddress: MACAddress? = null,
    val vendor: String? = null,
    val hostnames: List<String> = emptyList(),
    val netbios: NetBiosInfo? = null,
    val bonjourServices: List<BonjourService> = emptyList(),
    val openPorts: List<PortObservation> = emptyList(),
    val roundTripTime: Double? = null,
    val ttl: Int? = null,
    val discoveryMethods: Set<DiscoveryMethod> = emptySet(),

    val isGateway: Boolean = false,
    val isThisDevice: Boolean = false,

    val category: DeviceCategory = DeviceCategory.UNKNOWN,
    val modelIdentifier: String? = null,
    val modelName: String? = null,
    val operatingSystem: String? = null,
    val confidence: Double = 0.0,

    /** Epoch milliseconds, so an exported report stays plain JSON. */
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
) {
    val id: IPv4 get() = ip

    /** Best available human name, falling back through every naming source. */
    val displayName: String
        get() {
            if (isThisDevice) return "This device"
            bonjourServices.firstNotNullOfOrNull { it.advertisedFriendlyName }
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
            hostnames.firstOrNull()?.let { return NameResolution.shortName(it) }
            netbios?.computerName?.let { return it }
            bonjourServices.firstOrNull()?.instanceName?.takeIf { it.isNotEmpty() }
                ?.let { return cleanedInstanceName(it) }
            modelName?.let { return it }
            if (isGateway) return "Router"
            vendor?.let { return "$it device" }
            return ip.toString()
        }

    /** One-line subtitle: what we think it is, and who made it. */
    val subtitle: String
        get() {
            val parts = mutableListOf<String>()
            if (modelName != null && modelName != displayName) {
                parts += modelName
            } else if (category != DeviceCategory.UNKNOWN) {
                parts += category.label
            }
            if (vendor != null && parts.none { it.contains(vendor, ignoreCase = true) }) {
                parts += vendor
            }
            operatingSystem?.let { parts += it }
            if (parts.isNotEmpty()) return parts.joinToString(" · ")

            // Nothing identifying came back — say how it was seen instead of
            // repeating the address that is already the row's title.
            if (openPorts.isNotEmpty()) {
                return "${openPorts.size} open port${if (openPorts.size == 1) "" else "s"}"
            }
            if (DiscoveryMethod.ICMP in discoveryMethods) return "Responded to ping"
            return "Responded to discovery"
        }

    /** True when no naming source produced anything better than the address. */
    val isUnnamed: Boolean get() = displayName == ip.toString()

    val isResponsive: Boolean get() = discoveryMethods.isNotEmpty()

    /** `_raop` and friends encode a MAC into the instance name; strip it. */
    private fun cleanedInstanceName(name: String): String {
        val at = name.indexOf('@')
        if (at == 12) return name.substring(at + 1)
        val bracket = name.indexOf('[')
        if (bracket >= 0) return name.substring(0, bracket).trim()
        return name
    }

    /** Merges another sighting of the same address into this record. */
    fun mergedWith(other: NetworkDevice): NetworkDevice {
        val mergedHostnames = hostnames.toMutableList()
        for (hostname in other.hostnames) {
            if (hostname !in mergedHostnames) mergedHostnames += hostname
        }

        // Bonjour services are keyed by instance+type+domain: a later sighting of
        // the same service carries a resolved address or a fuller TXT record, so
        // it replaces the earlier one rather than sitting beside it.
        val mergedServices = bonjourServices.toMutableList()
        for (service in other.bonjourServices) {
            val existing = mergedServices.indexOfFirst { it.id == service.id }
            if (existing >= 0) mergedServices[existing] = service else mergedServices += service
        }

        val mergedPorts = openPorts.toMutableList()
        for (port in other.openPorts) {
            val existing = mergedPorts.indexOfFirst { it.port == port.port }
            if (existing >= 0) mergedPorts[existing] = port else mergedPorts += port
        }
        mergedPorts.sortBy { it.port }

        return copy(
            macAddress = macAddress ?: other.macAddress,
            vendor = vendor ?: other.vendor,
            netbios = netbios ?: other.netbios,
            roundTripTime = roundTripTime ?: other.roundTripTime,
            ttl = ttl ?: other.ttl,
            modelIdentifier = modelIdentifier ?: other.modelIdentifier,
            modelName = modelName ?: other.modelName,
            operatingSystem = operatingSystem ?: other.operatingSystem,
            isGateway = isGateway || other.isGateway,
            isThisDevice = isThisDevice || other.isThisDevice,
            discoveryMethods = discoveryMethods + other.discoveryMethods,
            lastSeen = maxOf(lastSeen, other.lastSeen),
            hostnames = mergedHostnames,
            bonjourServices = mergedServices,
            openPorts = mergedPorts,
        )
    }

    /** Matches the search box against every identifying string we hold. */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true

        val haystack = mutableListOf(displayName, subtitle, ip.toString(), category.label)
        haystack += hostnames
        haystack += listOfNotNull(vendor, modelName, modelIdentifier, operatingSystem)
        haystack += listOfNotNull(macAddress?.toString())
        haystack += bonjourServices.map { it.instanceName }
        haystack += bonjourServices.map { it.friendlyProtocolName }
        haystack += openPorts.map { "${it.port} ${it.serviceName} ${it.summary}" }
        netbios?.let { haystack += listOfNotNull(it.computerName, it.workgroup) }

        return haystack.any { it.lowercase().contains(needle) }
    }
}
