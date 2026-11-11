//
//  NetBiosProbe.kt
//  AssetOnBoard
//
//  NetBIOS node-status query (UDP/137).
//
//  Worth the effort because it is the one *unicast* probe that returns a MAC
//  address — no multicast, no ARP cache. Windows PCs, Samba shares, NAS boxes
//  and many printers all answer, giving back the machine name, the workgroup and
//  the adapter's hardware address in a single datagram.
//
//  This matters more on Android than on iOS: /proc/net/arp has been unreadable
//  to apps since Android 10, so for a host that answers neither mDNS nor SNMP
//  this is the only way a MAC address — and therefore a manufacturer — is ever
//  learned.
//

package com.example.sclerago.scan.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.random.Random

@Serializable
data class NetBiosInfo(
    val computerName: String? = null,
    val workgroup: String? = null,
    val services: List<String> = emptyList(),
    val macAddress: MACAddress? = null,
    val isDomainController: Boolean = false,
    val isMasterBrowser: Boolean = false,
)

object NetBiosProbe {

    suspend fun query(host: IPv4, timeout: Double = 1.2): NetBiosInfo? {
        val transactionId = Random.nextInt(0, 0x10000)
        val request = buildNodeStatusRequest(transactionId)

        val response = sendDatagram(host, 137, request, timeout) ?: return null
        if (response.size <= 56) return null
        return parse(response, transactionId)
    }

    // MARK: - Request

    /**
     * Builds an NBSTAT query for the wildcard name `*`.
     *
     * NetBIOS names travel in "first-level encoding": each byte becomes two
     * nibbles offset by ASCII 'A', so a 16-byte name expands to 32 characters.
     */
    private fun buildNodeStatusRequest(transactionId: Int): ByteArray {
        val packet = ArrayList<Byte>(50)
        packet.add((transactionId ushr 8).toByte())
        packet.add((transactionId and 0xFF).toByte())
        packet.addAll(listOf<Byte>(0x00, 0x00)) // flags: standard query
        packet.addAll(listOf<Byte>(0x00, 0x01)) // QDCOUNT = 1
        packet.addAll(listOf<Byte>(0x00, 0x00)) // ANCOUNT
        packet.addAll(listOf<Byte>(0x00, 0x00)) // NSCOUNT
        packet.addAll(listOf<Byte>(0x00, 0x00)) // ARCOUNT

        val name = ByteArray(16)
        name[0] = 0x2A // '*'
        packet.add(0x20) // encoded name length
        for (byte in name) {
            val value = byte.toInt() and 0xFF
            packet.add((0x41 + (value ushr 4)).toByte())
            packet.add((0x41 + (value and 0x0F)).toByte())
        }
        packet.add(0x00) // root label

        packet.addAll(listOf<Byte>(0x00, 0x21)) // QTYPE = NBSTAT
        packet.addAll(listOf<Byte>(0x00, 0x01)) // QCLASS = IN
        return packet.toByteArray()
    }

    // MARK: - Response

    private fun parse(response: ByteArray, transactionId: Int): NetBiosInfo? {
        fun byteAt(index: Int) = response[index].toInt() and 0xFF

        if (byteAt(0) shl 8 or byteAt(1) != transactionId) return null
        val answerCount = byteAt(6) shl 8 or byteAt(7)
        if (answerCount <= 0) return null

        // 12-byte header + 34-byte encoded name + type/class/TTL/RDLENGTH.
        var cursor = 12 + 34 + 2 + 2 + 4 + 2
        if (cursor >= response.size) return null

        val nameCount = byteAt(cursor)
        cursor += 1

        var computerName: String? = null
        var workgroup: String? = null
        val services = mutableListOf<String>()
        var isDomainController = false
        var isMasterBrowser = false

        repeat(nameCount) {
            if (cursor + 18 > response.size) return@repeat
            val rawName = response.copyOfRange(cursor, cursor + 15)
            val suffix = byteAt(cursor + 15)
            val flags = byteAt(cursor + 16) shl 8 or byteAt(cursor + 17)
            cursor += 18

            val name = String(rawName, Charsets.US_ASCII).trim()
            if (name.isEmpty()) return@repeat

            val isGroup = flags and 0x8000 != 0
            when {
                suffix == 0x00 && !isGroup -> if (computerName == null) computerName = name
                suffix == 0x00 && isGroup -> if (workgroup == null) workgroup = name
                suffix == 0x20 && !isGroup -> services += "File Server"
                suffix == 0x03 && !isGroup -> services += "Messenger"
                suffix == 0x1B -> {
                    isDomainController = true
                    services += "Domain Master Browser"
                }
                suffix == 0x1C -> isDomainController = true
                suffix == 0x1D -> {
                    isMasterBrowser = true
                    services += "Master Browser"
                }
                suffix == 0x1E -> services += "Browser Elections"
                suffix == 0x06 -> services += "RAS Server"
                suffix == 0x21 -> services += "RAS Client"
            }
        }

        // The 6-byte adapter unit ID sits immediately after the name table.
        var macAddress: MACAddress? = null
        if (cursor + 6 <= response.size) {
            val candidate = MACAddress.of(response.copyOfRange(cursor, cursor + 6))
            if (candidate != null && !candidate.isAllZero) macAddress = candidate
        }

        if (computerName == null && workgroup == null && macAddress == null) return null
        return NetBiosInfo(
            computerName = computerName,
            workgroup = workgroup,
            services = services.distinct().sorted(),
            macAddress = macAddress,
            isDomainController = isDomainController,
            isMasterBrowser = isMasterBrowser,
        )
    }

    // MARK: - UDP transport

    /**
     * Sends one datagram and waits for one reply.
     *
     * Shared with [SnmpClient], which needs exactly the same request/response
     * shape against a different port.
     */
    suspend fun sendDatagram(
        host: IPv4,
        port: Int,
        payload: ByteArray,
        timeout: Double,
    ): ByteArray? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = maxOf(200, (timeout * 1000).toInt())
            socket.send(
                DatagramPacket(
                    payload,
                    payload.size,
                    InetSocketAddress(host.toInetAddress(), port),
                ),
            )

            val buffer = ByteArray(4096)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            buffer.copyOfRange(0, reply.length)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}
