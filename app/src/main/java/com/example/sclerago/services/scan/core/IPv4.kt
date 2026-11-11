//
//  IPv4.kt
//  AssetOnBoard
//
//  Value types for IPv4 addresses and subnet arithmetic.
//

package com.example.sclerago.services.scan.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.Inet4Address
import java.net.InetAddress

/**
 * A 32-bit IPv4 address stored in host byte order.
 *
 * Kotlin has no unsigned Int in its stable public API surface for value classes
 * used as map keys, so the raw value is a signed [Int] holding the same bits;
 * every comparison goes through [compareTo], which treats it as unsigned.
 *
 * Named `IPv4` rather than `IPv4Address` to avoid colliding with
 * `java.net.Inet4Address` in files that use both.
 */
@Serializable(with = IPv4Serializer::class)
@JvmInline
value class IPv4(val raw: Int) : Comparable<IPv4> {

    val octets: IntArray
        get() = intArrayOf(
            raw ushr 24 and 0xFF,
            raw ushr 16 and 0xFF,
            raw ushr 8 and 0xFF,
            raw and 0xFF,
        )

    override fun toString(): String {
        val o = octets
        return "${o[0]}.${o[1]}.${o[2]}.${o[3]}"
    }

    override fun compareTo(other: IPv4): Int =
        (raw.toLong() and 0xFFFFFFFFL).compareTo(other.raw.toLong() and 0xFFFFFFFFL)

    /** RFC 1918 / RFC 3927 ranges that indicate a private LAN. */
    val isPrivate: Boolean
        get() {
            val o = octets
            if (o[0] == 10) return true
            if (o[0] == 172 && o[1] in 16..31) return true
            if (o[0] == 192 && o[1] == 168) return true
            if (o[0] == 169 && o[1] == 254) return true // link-local
            return false
        }

    val isLoopback: Boolean get() = octets[0] == 127

    /** Big-endian wire bytes, the order every socket API expects. */
    val bytes: ByteArray
        get() = byteArrayOf(
            (raw ushr 24).toByte(),
            (raw ushr 16).toByte(),
            (raw ushr 8).toByte(),
            raw.toByte(),
        )

    fun toInetAddress(): InetAddress = InetAddress.getByAddress(bytes)

    companion object {
        fun parse(text: String): IPv4? {
            val parts = text.trim().split('.')
            if (parts.size != 4) return null
            var value = 0
            for (part in parts) {
                val octet = part.toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                value = value shl 8 or octet
            }
            return IPv4(value)
        }

        fun from(address: InetAddress): IPv4? {
            if (address !is Inet4Address) return null
            val raw = address.address
            return IPv4(
                (raw[0].toInt() and 0xFF shl 24) or
                    (raw[1].toInt() and 0xFF shl 16) or
                    (raw[2].toInt() and 0xFF shl 8) or
                    (raw[3].toInt() and 0xFF),
            )
        }
    }
}

internal object IPv4Serializer : KSerializer<IPv4> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IPv4", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: IPv4) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): IPv4 {
        val text = decoder.decodeString()
        return IPv4.parse(text) ?: throw IllegalArgumentException("Invalid IPv4 literal $text")
    }
}

/** An IPv4 network expressed as a base address plus prefix length. */
data class IPv4Subnet(val network: IPv4, val prefixLength: Int) {

    companion object {
        fun mask(prefix: Int): Int = if (prefix == 0) 0 else -1 shl (32 - prefix)

        fun of(network: IPv4, prefixLength: Int): IPv4Subnet {
            val prefix = prefixLength.coerceIn(0, 32)
            return IPv4Subnet(IPv4(network.raw and mask(prefix)), prefix)
        }

        /** Builds the subnet containing [address] given a dotted-quad netmask. */
        fun of(address: IPv4, netmask: IPv4): IPv4Subnet =
            of(address, Integer.bitCount(netmask.raw))

        /** Prefix assumed when the user types a bare address with no suffix. */
        const val DEFAULT_PREFIX_LENGTH = 24

        /**
         * Parses subnet text as a person would type it: `10.0.5.0/24`,
         * `10.0.5.0/255.255.255.0`, or a bare `10.0.5.0` read as a /24.
         *
         * The address may be any host inside the network — `10.0.5.42/24` and
         * `10.0.5.0/24` name the same subnet — because that is how people quote
         * the network they are pointing at.
         */
        fun parse(text: String): IPv4Subnet? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null

            val slash = trimmed.indexOf('/')
            if (slash < 0) {
                val address = IPv4.parse(trimmed) ?: return null
                return of(address, DEFAULT_PREFIX_LENGTH)
            }

            val address = IPv4.parse(trimmed.substring(0, slash)) ?: return null
            val suffix = trimmed.substring(slash + 1).trim()
            if (suffix.isEmpty()) return null

            suffix.toIntOrNull()?.let { prefix ->
                return if (prefix in 0..32) of(address, prefix) else null
            }

            val netmask = IPv4.parse(suffix) ?: return null
            // A non-contiguous mask such as 255.0.255.0 is not a prefix at all;
            // bitCount alone would quietly read it as a /16.
            val length = Integer.bitCount(netmask.raw)
            if (mask(length) != netmask.raw) return null
            return of(address, length)
        }
    }

    val netmask: IPv4 get() = IPv4(mask(prefixLength))
    val broadcast: IPv4 get() = IPv4(network.raw or mask(prefixLength).inv())

    /** Total addressable hosts, excluding network and broadcast addresses. */
    val hostCount: Int
        get() {
            if (prefixLength >= 31) return if (prefixLength == 32) 1 else 2
            val total = 1L shl (32 - prefixLength)
            return (total - 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    operator fun contains(address: IPv4): Boolean =
        address.raw and mask(prefixLength) == network.raw

    /** Every usable host address in the subnet, in ascending order. */
    val hostAddresses: List<IPv4>
        get() {
            if (prefixLength >= 31) {
                return if (prefixLength == 32) listOf(network) else listOf(network, broadcast)
            }
            val first = network.raw + 1
            val last = broadcast.raw - 1
            val count = last - first + 1
            if (count <= 0) return emptyList()
            return List(count) { IPv4(first + it) }
        }

    /** The conventional router address (`x.x.x.1`) for this subnet. */
    val likelyGateway: IPv4?
        get() = if (prefixLength >= 31) null else IPv4(network.raw + 1)

    /**
     * Narrows an oversized subnet to at most [limit] hosts, centred on [anchor].
     *
     * Sweeping a /16 would mean 65k probes; this keeps scans bounded while still
     * covering the neighbourhood the device actually sits in.
     */
    fun clamped(anchor: IPv4, maxHosts: Int): IPv4Subnet {
        if (hostCount <= maxHosts) return this
        var prefix = prefixLength
        while (prefix < 32) {
            val candidate = of(anchor, prefix)
            if (candidate.hostCount <= maxHosts) return candidate
            prefix += 1
        }
        return of(anchor, 32)
    }

    override fun toString(): String = "$network/$prefixLength"
}

/** A 48-bit hardware address. */
@Serializable(with = MACAddressSerializer::class)
class MACAddress private constructor(val bytes: ByteArray) {

    override fun toString(): String = bytes.joinToString(":") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean =
        other is MACAddress && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    /** First three octets, used for IEEE OUI vendor lookup. */
    val oui: Int
        get() = (bytes[0].toInt() and 0xFF shl 16) or
            (bytes[1].toInt() and 0xFF shl 8) or
            (bytes[2].toInt() and 0xFF)

    /**
     * True when the U/L bit is set, meaning the address was self-assigned.
     *
     * Modern phones rotate a private MAC per network, so this flag explains why
     * a vendor lookup came back empty.
     */
    val isLocallyAdministered: Boolean get() = bytes[0].toInt() and 0x02 != 0

    val isMulticast: Boolean get() = bytes[0].toInt() and 0x01 != 0

    val isAllZero: Boolean get() = bytes.all { it == 0.toByte() }

    companion object {
        fun of(bytes: ByteArray): MACAddress? =
            if (bytes.size == 6) MACAddress(bytes.copyOf()) else null

        fun parse(text: String): MACAddress? {
            val cleaned = text.replace('-', ':').replace('.', ':')
            val parts = cleaned.split(':').filter { it.isNotEmpty() }
            if (parts.size != 6) return null
            val parsed = ByteArray(6)
            for (index in 0 until 6) {
                val value = parts[index].toIntOrNull(16) ?: return null
                if (value !in 0..255) return null
                parsed[index] = value.toByte()
            }
            return MACAddress(parsed)
        }
    }
}

internal object MACAddressSerializer : KSerializer<MACAddress> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MACAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MACAddress) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): MACAddress {
        val text = decoder.decodeString()
        return MACAddress.parse(text) ?: throw IllegalArgumentException("Invalid MAC literal $text")
    }
}
