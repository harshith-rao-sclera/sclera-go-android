//
//  SnmpClient.kt
//  AssetOnBoard
//
//  SNMPv2c client for reading a router's ARP table and its view of the network.
//
//  The router is the one device that legitimately knows every IP-to-MAC binding
//  on the subnet, and `ipNetToMediaPhysAddress` (1.3.6.1.2.1.4.22.1.2) is exactly
//  that table. One GETBULK walk over UDP/161 returns the whole mapping, which is
//  the only way to obtain MAC addresses for hosts that answer neither mDNS nor
//  NetBIOS once Android's own ARP cache is out of reach.
//
//  The same agent also knows which networks exist at all, which no amount of
//  sweeping from one segment can reveal: ipAdEntNetMask lists the subnets the
//  router is attached to, and ipCidrRouteTable the ones it merely routes to.
//
//  Read-only: this issues GETBULK requests and never writes to the device.
//

package com.sclera.sclerago.services.scan.core

import kotlin.random.Random

data class SnmpResult(
    /** IP-to-MAC bindings harvested from the router's ARP table. */
    val arpTable: Map<IPv4, MACAddress> = emptyMap(),
    /** The community string that worked, for display. */
    val community: String = "",
    val systemDescription: String? = null,
    val systemName: String? = null,
)

/** The networks a router told us about. */
data class SnmpSubnets(
    /** The community string that worked, for display. */
    val community: String,
    /** Subnets the router has an interface on, mapped to that interface's address. */
    val connected: Map<IPv4Subnet, IPv4> = emptyMap(),
    /** Subnets it holds a route to without being attached to them. */
    val routed: List<IPv4Subnet> = emptyList(),
) {
    val isEmpty: Boolean get() = connected.isEmpty() && routed.isEmpty()
}

object SnmpClient {

    /** ipNetToMediaPhysAddress — the ARP table, keyed by interface and IP. */
    private val ARP_TABLE_OID = intArrayOf(1, 3, 6, 1, 2, 1, 4, 22, 1, 2)

    /** ipAdEntNetMask — the agent's own addresses, each indexed by that address. */
    private val IP_ADDRESS_MASK_OID = intArrayOf(1, 3, 6, 1, 2, 1, 4, 20, 1, 3)

    /** ipCidrRouteIfIndex — one row per route, indexed by dest, mask, TOS, next hop. */
    private val CIDR_ROUTE_OID = intArrayOf(1, 3, 6, 1, 2, 1, 4, 24, 4, 1, 5)

    /** ipRouteMask — the deprecated routing table, for agents without the CIDR one. */
    private val LEGACY_ROUTE_MASK_OID = intArrayOf(1, 3, 6, 1, 2, 1, 4, 21, 1, 11)

    /** sysDescr.0 and sysName.0 — free identity information while we are here. */
    private val SYS_DESCR_OID = intArrayOf(1, 3, 6, 1, 2, 1, 1, 1, 0)
    private val SYS_NAME_OID = intArrayOf(1, 3, 6, 1, 2, 1, 1, 5, 0)

    /**
     * Walks the router's ARP table, trying each community in turn.
     *
     * Returns null when SNMP is disabled or no community is accepted, which is
     * the common case on consumer routers.
     */
    suspend fun readArpTable(
        router: IPv4,
        communities: List<String>,
        timeout: Double = 1.5,
    ): SnmpResult? {
        for (community in communities) {
            if (community.isEmpty()) continue
            val identity = get(
                listOf(SYS_DESCR_OID, SYS_NAME_OID),
                router,
                community,
                timeout,
            ) ?: continue

            val descriptionPrefix = SYS_DESCR_OID.dropLast(1).toIntArray()
            val namePrefix = SYS_NAME_OID.dropLast(1).toIntArray()

            return SnmpResult(
                community = community,
                systemDescription = identity
                    .firstOrNull { it.oid.startsWith(descriptionPrefix) }?.stringValue,
                systemName = identity
                    .firstOrNull { it.oid.startsWith(namePrefix) }?.stringValue,
                arpTable = walkArp(router, community, timeout),
            )
        }
        return null
    }

    /**
     * Reads the networks the router knows about, trying each community in turn.
     *
     * Two tables, because they answer different halves of the question. The
     * address table is what the router is plugged into and is trustworthy down to
     * the mask; the route table reaches further, to segments behind other routers,
     * and is the only way to learn of a subnet nothing local has ever talked to.
     *
     * Returns null when SNMP is disabled or no community is accepted.
     */
    suspend fun readSubnets(
        router: IPv4,
        communities: List<String>,
        timeout: Double = 1.5,
    ): SnmpSubnets? {
        for (community in communities) {
            if (community.isEmpty()) continue
            // One cheap GET first: a wrong community should cost a single round
            // trip, not two full walks that quietly return nothing.
            get(listOf(SYS_NAME_OID), router, community, timeout) ?: continue

            val connected = walkConnectedSubnets(router, community, timeout)
            var routed = walkRoutedSubnets(CIDR_ROUTE_OID, router, community, timeout)
            if (routed.isEmpty()) {
                routed = walkRoutedSubnets(LEGACY_ROUTE_MASK_OID, router, community, timeout)
            }

            return SnmpSubnets(
                community = community,
                connected = connected,
                routed = routed.filterNot { it in connected.keys },
            )
        }
        return null
    }

    // MARK: - Walk

    /**
     * Walks [root] with repeated GETBULKs, handing every varbind under it to
     * [onVarBind] and stopping as soon as the agent leaves the subtree.
     */
    private suspend fun walk(
        root: IntArray,
        router: IPv4,
        community: String,
        timeout: Double,
        onVarBind: (VarBind) -> Unit,
    ) {
        var cursor = root

        // Bounded so a misbehaving agent cannot loop forever.
        repeat(WALK_ROUND_LIMIT) {
            val varbinds = getBulk(cursor, router, community, timeout)
            if (varbinds.isNullOrEmpty()) return

            var advanced = false
            for (varbind in varbinds) {
                if (!varbind.oid.startsWith(root)) return
                onVarBind(varbind)
                cursor = varbind.oid
                advanced = true
            }
            if (!advanced) return
        }
    }

    /** The subnets the router itself sits on, from the IP address table. */
    private suspend fun walkConnectedSubnets(
        router: IPv4,
        community: String,
        timeout: Double,
    ): Map<IPv4Subnet, IPv4> {
        val found = LinkedHashMap<IPv4Subnet, IPv4>()
        walk(IP_ADDRESS_MASK_OID, router, community, timeout) { varbind ->
            // The row is indexed by the address it describes; the value is its mask.
            val suffix = varbind.oid.drop(IP_ADDRESS_MASK_OID.size)
            if (suffix.size < 4) return@walk
            val address = addressOf(suffix.takeLast(4))
            val mask = varbind.dataValue?.takeIf { it.size == 4 }?.let { addressOf(it) }
                ?: return@walk
            if (mask.raw == 0 || address.isLoopback) return@walk
            found.putIfAbsent(IPv4Subnet.of(address, mask), address)
        }
        return found
    }

    /**
     * The subnets the router holds a route to.
     *
     * Both tables encode what we need in the row's index rather than its value:
     * the CIDR table's index is dest, mask, TOS and next hop, the deprecated
     * table's is the destination alone with the mask as the value. Either way one
     * column is enough, so this walks the cheapest one.
     */
    private suspend fun walkRoutedSubnets(
        root: IntArray,
        router: IPv4,
        community: String,
        timeout: Double,
    ): List<IPv4Subnet> {
        val found = LinkedHashSet<IPv4Subnet>()
        walk(root, router, community, timeout) { varbind ->
            val suffix = varbind.oid.drop(root.size)
            val destination: IPv4
            val mask: IPv4
            if (root.contentEquals(CIDR_ROUTE_OID)) {
                if (suffix.size < 8) return@walk
                destination = addressOf(suffix.take(4))
                mask = addressOf(suffix.drop(4).take(4))
            } else {
                if (suffix.size < 4) return@walk
                destination = addressOf(suffix.take(4))
                mask = varbind.dataValue?.takeIf { it.size == 4 }?.let { addressOf(it) }
                    ?: return@walk
            }
            // The default route names no subnet, and a zero destination is padding.
            if (mask.raw == 0 || destination.raw == 0 || destination.isLoopback) return@walk
            found += IPv4Subnet.of(destination, mask)
        }
        return found.toList()
    }

    private fun addressOf(octets: List<Int>): IPv4 = IPv4(
        (octets[0] and 0xFF shl 24) or
            (octets[1] and 0xFF shl 16) or
            (octets[2] and 0xFF shl 8) or
            (octets[3] and 0xFF),
    )

    private fun addressOf(octets: ByteArray): IPv4 = addressOf(octets.map { it.toInt() })

    private suspend fun walkArp(
        router: IPv4,
        community: String,
        timeout: Double,
    ): Map<IPv4, MACAddress> {
        val bindings = LinkedHashMap<IPv4, MACAddress>()
        walk(ARP_TABLE_OID, router, community, timeout) { varbind ->
            // Trailing arcs are <ifIndex>.<a>.<b>.<c>.<d>.
            val suffix = varbind.oid.drop(ARP_TABLE_OID.size)
            if (suffix.size < 5) return@walk
            val address = addressOf(suffix.takeLast(4))

            val bytes = varbind.dataValue
            if (bytes != null && bytes.size == 6) {
                val mac = MACAddress.of(bytes)
                if (mac != null && !mac.isAllZero && !mac.isMulticast) {
                    bindings[address] = mac
                }
            }
        }
        return bindings
    }

    /** GETBULK rounds one walk may take before giving up on the agent. */
    private const val WALK_ROUND_LIMIT = 64

    private fun IntArray.startsWith(prefix: IntArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) if (this[index] != prefix[index]) return false
        return true
    }

    // MARK: - Requests

    private class VarBind(
        val oid: IntArray,
        val dataValue: ByteArray?,
        val stringValue: String?,
    )

    private suspend fun get(
        oids: List<IntArray>,
        host: IPv4,
        community: String,
        timeout: Double,
    ): List<VarBind>? {
        val requestId = Random.nextInt(1, Int.MAX_VALUE)
        val pdu = encodeSequence(
            0xA0, // GetRequest
            encodeInteger(requestId) + encodeInteger(0) + encodeInteger(0) +
                encodeSequence(0x30, oids.fold(ByteArray(0)) { acc, oid -> acc + encodeVarBind(oid) }),
        )
        return exchange(encodeMessage(community, pdu), host, timeout)
    }

    private suspend fun getBulk(
        oid: IntArray,
        host: IPv4,
        community: String,
        timeout: Double,
    ): List<VarBind>? {
        val requestId = Random.nextInt(1, Int.MAX_VALUE)
        val pdu = encodeSequence(
            0xA5, // GetBulkRequest
            encodeInteger(requestId) +
                encodeInteger(0) + // non-repeaters
                encodeInteger(24) + // max-repetitions
                encodeSequence(0x30, encodeVarBind(oid)),
        )
        return exchange(encodeMessage(community, pdu), host, timeout)
    }

    private suspend fun exchange(
        message: ByteArray,
        host: IPv4,
        timeout: Double,
    ): List<VarBind>? {
        val response = NetBiosProbe.sendDatagram(host, 161, message, timeout) ?: return null
        return decode(response)
    }

    // MARK: - BER encoding

    private fun encodeMessage(community: String, pdu: ByteArray): ByteArray =
        encodeSequence(
            0x30,
            encodeInteger(1) + // version 1 == SNMPv2c
                encodeOctetString(community.toByteArray(Charsets.US_ASCII)) +
                pdu,
        )

    private fun encodeLength(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = ArrayList<Byte>(4)
        var remaining = length
        while (remaining > 0) {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun encodeSequence(tag: Int, contents: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + encodeLength(contents.size) + contents

    private fun encodeInteger(value: Int): ByteArray {
        val bytes = ArrayList<Byte>(5)
        var remaining = value
        do {
            bytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        } while (remaining != 0 && remaining != -1)
        // Clear the sign bit so a positive value is never read as negative.
        if (value >= 0 && (bytes[0].toInt() and 0x80) != 0) bytes.add(0, 0)
        return byteArrayOf(0x02) + encodeLength(bytes.size) + bytes.toByteArray()
    }

    private fun encodeOctetString(bytes: ByteArray): ByteArray =
        byteArrayOf(0x04) + encodeLength(bytes.size) + bytes

    private fun encodeOid(arcs: IntArray): ByteArray {
        if (arcs.size < 2) return byteArrayOf(0x06, 0x00)
        // The first two arcs share one byte: 40 * first + second.
        val body = ArrayList<Byte>(arcs.size + 4)
        body.add((arcs[0] * 40 + arcs[1]).toByte())
        for (index in 2 until arcs.size) {
            val arc = arcs[index]
            val chunks = ArrayList<Byte>(5)
            chunks.add((arc and 0x7F).toByte())
            var remaining = arc ushr 7
            while (remaining > 0) {
                chunks.add(0, ((remaining and 0x7F) or 0x80).toByte())
                remaining = remaining ushr 7
            }
            body.addAll(chunks)
        }
        return byteArrayOf(0x06) + encodeLength(body.size) + body.toByteArray()
    }

    private fun encodeVarBind(oid: IntArray): ByteArray =
        encodeSequence(0x30, encodeOid(oid) + byteArrayOf(0x05, 0x00)) // value = NULL

    // MARK: - BER decoding

    private class Element(val tag: Int, val start: Int, val end: Int)

    private fun elements(bytes: ByteArray, start: Int, end: Int): List<Element> {
        val results = mutableListOf<Element>()
        var index = start

        while (index + 1 < end) {
            val tag = bytes[index].toInt() and 0xFF
            var cursor = index + 1
            var length = 0

            val first = bytes[cursor].toInt() and 0xFF
            cursor += 1
            if (first and 0x80 == 0) {
                length = first
            } else {
                val byteCount = first and 0x7F
                if (byteCount == 0 || byteCount > 4 || cursor + byteCount > end) break
                repeat(byteCount) {
                    length = length shl 8 or (bytes[cursor].toInt() and 0xFF)
                    cursor += 1
                }
            }
            if (length < 0 || cursor + length > end) break
            results += Element(tag, cursor, cursor + length)
            index = cursor + length
        }
        return results
    }

    private fun decode(bytes: ByteArray): List<VarBind>? {
        val message = elements(bytes, 0, bytes.size).firstOrNull() ?: return null
        if (message.tag != 0x30) return null

        val top = elements(bytes, message.start, message.end)
        // version, community, then the response PDU tagged [2].
        val pdu = top.firstOrNull { it.tag == 0xA2 } ?: return null

        val fields = elements(bytes, pdu.start, pdu.end)
        if (fields.size < 4) return null
        // fields[1] is error-status; anything non-zero means no usable varbinds.
        if (decodeInteger(bytes, fields[1]) != 0) return null

        return elements(bytes, fields[3].start, fields[3].end).mapNotNull { entry ->
            val pair = elements(bytes, entry.start, entry.end)
            if (pair.size < 2 || pair[0].tag != 0x06) return@mapNotNull null
            val oid = decodeOid(bytes, pair[0])
            if (oid.isEmpty()) return@mapNotNull null

            val value = pair[1]
            // 0x80/0x81/0x82 are noSuchObject / noSuchInstance / endOfMibView.
            if (value.tag == 0x80 || value.tag == 0x81 || value.tag == 0x82) {
                return@mapNotNull VarBind(oid, null, null)
            }
            val raw = bytes.copyOfRange(value.start, value.end)
            VarBind(
                oid = oid,
                dataValue = raw,
                stringValue = if (value.tag == 0x04) String(raw, Charsets.UTF_8) else null,
            )
        }
    }

    private fun decodeInteger(bytes: ByteArray, element: Element): Int {
        var value = 0
        for (index in element.start until element.end) {
            value = value shl 8 or (bytes[index].toInt() and 0xFF)
        }
        return value
    }

    private fun decodeOid(bytes: ByteArray, element: Element): IntArray {
        if (element.start >= element.end) return IntArray(0)
        val first = bytes[element.start].toInt() and 0xFF
        val arcs = mutableListOf(first / 40, first % 40)
        var accumulator = 0

        for (index in (element.start + 1) until element.end) {
            val byte = bytes[index].toInt() and 0xFF
            accumulator = accumulator shl 7 or (byte and 0x7F)
            if (byte and 0x80 == 0) {
                arcs += accumulator
                accumulator = 0
            }
        }
        return arcs.toIntArray()
    }
}
