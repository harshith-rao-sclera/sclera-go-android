//
//  MdnsDiscovery.kt
//  AssetOnBoard
//
//  Passive device discovery over mDNS / DNS-SD.
//
//  This is the single richest source of identity on a phone. Devices advertise
//  their own model strings, friendly names and — for AirPlay, AirPlay-audio and
//  Avahi workstations — their MAC address, none of which any active probe can
//  reach.
//
//  The iOS build hands this to NWBrowser. Android's NsdManager cannot do the
//  job: it resolves one service at a time, serialises badly across the fifty-odd
//  types below, and historically dropped TXT records. So this speaks mDNS
//  directly on 224.0.0.251:5353 — which has the side benefit of hearing every
//  unsolicited announcement on the segment, not only answers to our own queries.
//

package com.example.sclerago.scan.core

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BonjourService(
    val instanceName: String,
    val serviceType: String,
    val domain: String,
    val txtRecord: Map<String, String> = emptyMap(),
    val addresses: List<IPv4> = emptyList(),
    val port: Int? = null,
) {
    val id: String get() = "$instanceName.$serviceType.$domain"

    /** Human label for the protocol, e.g. `_airplay._tcp` -> "AirPlay". */
    val friendlyProtocolName: String
        get() = MdnsDiscovery.FRIENDLY_NAMES[serviceType] ?: serviceType

    /** TXT lookup that ignores key case, as the DNS-SD spec requires. */
    fun txt(key: String): String? {
        txtRecord[key]?.let { return it }
        return txtRecord.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    /**
     * MAC addresses a service advertises about itself.
     *
     * Three well-known conventions carry one:
     * `_airplay` TXT `deviceid`, `_raop` instance names (`AABBCC…@Speaker`),
     * and Avahi's `_workstation` names (`host [aa:bb:cc:dd:ee:ff]`).
     */
    val advertisedMac: MACAddress?
        get() {
            txt("deviceid")?.let { MACAddress.parse(it)?.let { mac -> return mac } }
            txt("mac")?.let { MACAddress.parse(it)?.let { mac -> return mac } }

            if (serviceType.startsWith("_raop")) {
                val separator = instanceName.indexOf('@')
                if (separator == 12) {
                    val hex = instanceName.substring(0, 12)
                    val pairs = (0 until 12 step 2).map { hex.substring(it, it + 2) }
                    MACAddress.parse(pairs.joinToString(":"))?.let { return it }
                }
            }

            val open = instanceName.indexOf('[')
            val close = instanceName.indexOf(']')
            if (open in 0 until close) {
                MACAddress.parse(instanceName.substring(open + 1, close))?.let { return it }
            }
            return null
        }

    /** The model identifier a device volunteers about itself, if any. */
    val advertisedModel: String?
        get() = listOf("model", "am", "md", "usb_MDL", "ty", "product", "MDL")
            .firstNotNullOfOrNull { key -> txt(key)?.takeIf { it.isNotEmpty() } }

    /** A display name the device chose for itself. */
    val advertisedFriendlyName: String?
        get() = listOf("fn", "n", "name", "CtlN", "friendlyName")
            .firstNotNullOfOrNull { key -> txt(key)?.takeIf { it.isNotEmpty() } }
}

class MdnsDiscovery(private val context: Context) {

    /** Set when the multicast socket could not be brought up at all. */
    @Volatile
    var unavailable: Boolean = false
        private set

    /**
     * True when we had to fall back to an ephemeral port because something else
     * owns 5353. Queries still work; unsolicited announcements are missed.
     */
    @Volatile
    var passiveListeningUnavailable: Boolean = false
        private set

    private val instances = ConcurrentHashMap<String, InstanceState>()
    private val hostAddresses = ConcurrentHashMap<String, MutableSet<IPv4>>()
    private val emitted = ConcurrentHashMap<String, String>()

    /** Instance FQDNs we have already asked for SRV/TXT, to avoid re-querying. */
    private val resolveRequested = ConcurrentHashMap.newKeySet<String>()

    /** Target hostnames we have already asked for an A record. */
    private val addressRequested = ConcurrentHashMap.newKeySet<String>()

    private class InstanceState(
        val instanceName: String,
        val serviceType: String,
        val domain: String,
    ) {
        @Volatile var txtRecord: Map<String, String> = emptyMap()

        @Volatile var port: Int? = null

        @Volatile var target: String? = null
    }

    /**
     * Browses all known service types, emitting each instance as soon as it has
     * an address and re-emitting it when better information arrives.
     *
     * The flow stays open until its collector is cancelled; devices that only
     * announce themselves periodically keep trickling in.
     */
    fun start(types: List<String> = SERVICE_TYPES): Flow<BonjourService> = callbackFlow {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val multicastLock = try {
            wifi?.createMulticastLock("sclerago-mdns")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {
            null
        }

        val nif = NetworkInterfaceScanner.preferredScanInterface(context)
            ?.let { NetworkInterfaceScanner.javaInterface(it.address) }

        val socket = openSocket(nif)
        if (socket == null) {
            unavailable = true
            runCatching { multicastLock?.release() }
            close()
            return@callbackFlow
        }
        unavailable = false

        val group = InetAddress.getByName(MULTICAST_GROUP)
        val destination = InetSocketAddress(group, MDNS_PORT)

        // Ask, then keep asking on a widening interval. mDNS responders answer a
        // fresh query immediately but suppress duplicates, so re-querying is how
        // devices that joined mid-scan get picked up.
        launch(Dispatchers.IO) {
            var wait = 1000L
            while (isActive) {
                for (batch in types.chunked(QUESTIONS_PER_PACKET)) {
                    if (!isActive) break
                    val query = DnsMessage.query(batch.map { "$it.$LOCAL_DOMAIN" to TYPE_PTR })
                    runCatching {
                        socket.send(DatagramPacket(query, query.size, destination))
                    }
                    delay(30)
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(30_000L)
            }
        }

        // Receive loop. Blocking reads with a short timeout so cancellation is
        // noticed promptly without spinning.
        launch(Dispatchers.IO) {
            val buffer = ByteArray(9000)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    if (!isActive) break
                    continue
                }

                val records = try {
                    DnsMessage.parse(buffer, packet.length)
                } catch (_: Exception) {
                    continue
                }

                val touched = apply(records)
                for (service in touched) {
                    val signature = service.signature()
                    if (emitted.put(service.id, signature) == signature) continue
                    trySend(service)
                }

                // Chase whatever the answers referred to but did not include.
                followUp(socket, destination)
            }
        }

        awaitClose {
            runCatching { socket.leaveGroup(InetSocketAddress(group, MDNS_PORT), nif) }
            runCatching { socket.close() }
            runCatching { multicastLock?.release() }
        }
    }.flowOn(Dispatchers.IO)

    fun reset() {
        instances.clear()
        hostAddresses.clear()
        emitted.clear()
        resolveRequested.clear()
        addressRequested.clear()
    }

    private fun openSocket(nif: NetworkInterface?): MulticastSocket? {
        val group = try {
            InetAddress.getByName(MULTICAST_GROUP)
        } catch (_: Exception) {
            return null
        }

        // Preferred: share port 5353 so we also hear unsolicited announcements.
        try {
            val socket = MulticastSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(MDNS_PORT))
            socket.timeToLive = 255
            socket.soTimeout = 500
            if (nif != null) {
                runCatching { socket.networkInterface = nif }
                socket.joinGroup(InetSocketAddress(group, MDNS_PORT), nif)
            } else {
                socket.joinGroup(group)
            }
            passiveListeningUnavailable = false
            return socket
        } catch (_: Exception) {
            // Fall through: something already owns 5353 exclusively.
        }

        // Fallback: an ephemeral port. Responders unicast their answers back to
        // whatever port asked, so discovery still works — only the passive
        // announcements broadcast to 5353 are lost.
        return try {
            val socket = MulticastSocket()
            socket.timeToLive = 255
            socket.soTimeout = 500
            if (nif != null) runCatching { socket.networkInterface = nif }
            passiveListeningUnavailable = true
            socket
        } catch (_: Exception) {
            null
        }
    }

    /** Folds one message's records into the registry, returning what changed. */
    private fun apply(records: List<DnsMessage.Record>): List<BonjourService> {
        val touchedKeys = LinkedHashSet<String>()

        for (record in records) {
            when (record) {
                is DnsMessage.Record.Ptr -> {
                    val serviceLabels = record.name
                    val instanceLabels = record.target
                    // `_airplay._tcp.local` -> the type; the target names the
                    // instance beneath it.
                    if (instanceLabels.size < 3) continue
                    if (instanceLabels.size < serviceLabels.size + 1) continue
                    val key = instanceLabels.joinToString(".").lowercase()
                    val state = instances.getOrPut(key) { instanceState(instanceLabels) }
                    if (state.serviceType.isNotEmpty()) touchedKeys += key
                }

                is DnsMessage.Record.Srv -> {
                    if (record.name.size < 3) continue
                    val key = record.name.joinToString(".").lowercase()
                    val state = instances.getOrPut(key) { instanceState(record.name) }
                    state.port = record.port
                    state.target = record.target.joinToString(".")
                    touchedKeys += key
                }

                is DnsMessage.Record.Txt -> {
                    // A direct SRV+TXT query can answer with TXT first, so this
                    // creates the instance rather than requiring one to exist.
                    if (record.name.size < 3) continue
                    val key = record.name.joinToString(".").lowercase()
                    val state = instances.getOrPut(key) { instanceState(record.name) }
                    state.txtRecord = record.entries
                    touchedKeys += key
                }

                is DnsMessage.Record.A -> {
                    val host = record.name.joinToString(".").lowercase()
                    val set = hostAddresses.getOrPut(host) {
                        java.util.Collections.synchronizedSet(LinkedHashSet<IPv4>())
                    }
                    if (set.add(record.address)) {
                        // Every instance pointing at this host just became
                        // resolvable, so re-evaluate all of them.
                        for ((key, state) in instances) {
                            if (state.target?.lowercase() == host) touchedKeys += key
                        }
                    }
                }
            }
        }

        return touchedKeys.mapNotNull { key -> instances[key]?.let { service(it) } }
            .filter { it.addresses.isNotEmpty() }
    }

    /** Splits an instance FQDN into `<instance>.<_type._proto>.<domain>`. */
    private fun instanceState(labels: List<String>) = InstanceState(
        instanceName = labels.first(),
        serviceType = labels.drop(1).dropLast(1).joinToString("."),
        domain = labels.last(),
    )

    private fun service(state: InstanceState): BonjourService {
        val addresses = state.target
            ?.lowercase()
            ?.let { hostAddresses[it] }
            ?.let { set -> synchronized(set) { set.toList() } }
            .orEmpty()

        return BonjourService(
            instanceName = state.instanceName,
            serviceType = state.serviceType,
            domain = state.domain,
            txtRecord = state.txtRecord,
            addresses = addresses,
            port = state.port,
        )
    }

    /** Asks for the SRV/TXT and A records the answers referred to. */
    private fun followUp(socket: MulticastSocket, destination: InetSocketAddress) {
        val questions = mutableListOf<Pair<String, Int>>()

        for ((key, state) in instances) {
            if (state.port == null && resolveRequested.add(key)) {
                val fqdn = "${state.instanceName}.${state.serviceType}.${state.domain}"
                questions += fqdn to TYPE_SRV
                questions += fqdn to TYPE_TXT
            }
            val target = state.target
            if (target != null &&
                !hostAddresses.containsKey(target.lowercase()) &&
                addressRequested.add(target.lowercase())
            ) {
                questions += target to TYPE_A
            }
            if (questions.size >= QUESTIONS_PER_PACKET * 4) break
        }

        for (batch in questions.chunked(QUESTIONS_PER_PACKET)) {
            val query = DnsMessage.query(batch)
            runCatching { socket.send(DatagramPacket(query, query.size, destination)) }
        }
    }

    private fun BonjourService.signature(): String =
        buildString {
            append(addresses.joinToString(","))
            append('|')
            append(port ?: -1)
            append('|')
            append(txtRecord.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" })
        }

    companion object {
        private const val MULTICAST_GROUP = "224.0.0.251"
        private const val MDNS_PORT = 5353
        private const val LOCAL_DOMAIN = "local"
        private const val QUESTIONS_PER_PACKET = 8

        private const val TYPE_A = 1
        private const val TYPE_PTR = 12
        private const val TYPE_TXT = 16
        private const val TYPE_SRV = 33

        /**
         * The same list the iOS build declares in `NSBonjourServices`. Android
         * needs no such declaration, but keeping the sets identical keeps the two
         * apps finding the same devices.
         */
        val SERVICE_TYPES: List<String> = listOf(
            "_device-info._tcp", "_airplay._tcp", "_raop._tcp", "_companion-link._tcp",
            "_rdlink._tcp", "_sleep-proxy._udp", "_apple-mobdev2._tcp", "_touch-able._tcp",
            "_mediaremotetv._tcp", "_homekit._tcp", "_hap._tcp", "_adisk._tcp",
            "_daap._tcp", "_dacp._tcp", "_airport._tcp",
            "_googlecast._tcp", "_androidtvremote2._tcp", "_amzn-wplay._tcp",
            "_spotify-connect._tcp", "_sonos._tcp", "_roku._tcp", "_rsp._tcp",
            "_http._tcp", "_https._tcp", "_workstation._tcp", "_ssh._tcp",
            "_sftp-ssh._tcp", "_telnet._tcp", "_rfb._tcp", "_smb._tcp",
            "_afpovertcp._tcp", "_nfs._tcp", "_webdav._tcp", "_ftp._tcp",
            "_ipp._tcp", "_ipps._tcp", "_printer._tcp", "_pdl-datastream._tcp",
            "_scanner._tcp", "_uscan._tcp", "_uscans._tcp",
            "_hue._tcp", "_esphomelib._tcp", "_matter._tcp", "_matterc._udp",
            "_miio._udp", "_ewelink._tcp", "_viziocast._tcp", "_plexmediasvr._tcp",
            "_nvstream._tcp", "_teamviewer._tcp", "_ldap._tcp", "_ipp-tls._tcp",
            // Android's own neighbours, which iOS has no reason to list.
            "_adb-tls-connect._tcp", "_androidtvremote._tcp", "_display._tcp",
        )

        val FRIENDLY_NAMES: Map<String, String> = mapOf(
            "_device-info._tcp" to "Device Info", "_airplay._tcp" to "AirPlay",
            "_raop._tcp" to "AirPlay Audio", "_companion-link._tcp" to "Apple Continuity",
            "_rdlink._tcp" to "Apple Remote Desktop", "_sleep-proxy._udp" to "Bonjour Sleep Proxy",
            "_apple-mobdev2._tcp" to "iOS Wi-Fi Sync", "_touch-able._tcp" to "Apple Remote",
            "_mediaremotetv._tcp" to "Apple TV Remote", "_homekit._tcp" to "HomeKit",
            "_hap._tcp" to "HomeKit Accessory", "_adisk._tcp" to "Time Machine",
            "_daap._tcp" to "iTunes Sharing", "_dacp._tcp" to "iTunes Remote",
            "_airport._tcp" to "AirPort Base Station", "_googlecast._tcp" to "Google Cast",
            "_androidtvremote2._tcp" to "Android TV Remote", "_amzn-wplay._tcp" to "Amazon Fire TV",
            "_spotify-connect._tcp" to "Spotify Connect", "_sonos._tcp" to "Sonos",
            "_roku._tcp" to "Roku", "_rsp._tcp" to "Roku Search",
            "_http._tcp" to "Web Server", "_https._tcp" to "Secure Web Server",
            "_workstation._tcp" to "Workstation", "_ssh._tcp" to "SSH",
            "_sftp-ssh._tcp" to "SFTP", "_telnet._tcp" to "Telnet",
            "_rfb._tcp" to "VNC / Screen Sharing",
            "_smb._tcp" to "SMB File Sharing", "_afpovertcp._tcp" to "Apple File Sharing",
            "_nfs._tcp" to "NFS", "_webdav._tcp" to "WebDAV", "_ftp._tcp" to "FTP",
            "_ipp._tcp" to "Printer (IPP)", "_ipps._tcp" to "Printer (IPPS)",
            "_printer._tcp" to "Printer (LPD)", "_pdl-datastream._tcp" to "Printer (Raw)",
            "_scanner._tcp" to "Scanner", "_uscan._tcp" to "AirScan",
            "_uscans._tcp" to "AirScan (TLS)",
            "_hue._tcp" to "Philips Hue", "_esphomelib._tcp" to "ESPHome",
            "_matter._tcp" to "Matter",
            "_matterc._udp" to "Matter Commissioning", "_miio._udp" to "Xiaomi MIoT",
            "_ewelink._tcp" to "eWeLink", "_viziocast._tcp" to "Vizio SmartCast",
            "_plexmediasvr._tcp" to "Plex Media Server", "_nvstream._tcp" to "NVIDIA GameStream",
            "_teamviewer._tcp" to "TeamViewer", "_ldap._tcp" to "LDAP",
            "_ipp-tls._tcp" to "Printer (IPP/TLS)",
            "_adb-tls-connect._tcp" to "Wireless Debugging",
            "_androidtvremote._tcp" to "Android TV Remote (legacy)",
            "_display._tcp" to "Wireless Display",
        )
    }
}
