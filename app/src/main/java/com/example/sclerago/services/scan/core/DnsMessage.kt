//
//  DnsMessage.kt
//  AssetOnBoard
//
//  Just enough DNS wire format to speak DNS-SD: build multi-question queries and
//  read PTR, SRV, TXT and A records back out of a response.
//
//  Names are handled as label lists rather than dotted strings throughout.
//  DNS-SD instance names routinely contain dots ("Harshith's MacBook Pro 14.2"),
//  and flattening them early makes it impossible to tell an instance boundary
//  from a character inside a label.
//

package com.example.sclerago.services.scan.core

import java.io.ByteArrayOutputStream

object DnsMessage {

    sealed interface Record {
        val name: List<String>

        data class Ptr(override val name: List<String>, val target: List<String>) : Record
        data class Srv(
            override val name: List<String>,
            val port: Int,
            val target: List<String>,
        ) : Record

        data class Txt(override val name: List<String>, val entries: Map<String, String>) : Record
        data class A(override val name: List<String>, val address: IPv4) : Record
    }

    private const val TYPE_A = 1
    private const val TYPE_PTR = 12
    private const val TYPE_TXT = 16
    private const val TYPE_SRV = 33

    /** Maximum labels to follow before declaring a compression loop. */
    private const val MAX_LABEL_HOPS = 128

    // MARK: - Building

    /**
     * Builds one query carrying every `name to type` pair given.
     *
     * The transaction ID is zero and the header flags are all clear: mDNS
     * queries are multicast and matched by question, not by ID.
     */
    fun query(questions: List<Pair<String, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeShort(0) // ID
        out.writeShort(0) // flags: standard query, not truncated, not recursive
        out.writeShort(questions.size) // QDCOUNT
        out.writeShort(0) // ANCOUNT
        out.writeShort(0) // NSCOUNT
        out.writeShort(0) // ARCOUNT

        for ((name, type) in questions) {
            out.writeName(name)
            out.writeShort(type)
            // Class IN. The top bit would request a unicast-only reply; leaving
            // it clear means answers go to the multicast group, which is how a
            // passive listener on 5353 gets to see other devices' traffic too.
            out.writeShort(1)
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeName(name: String) {
        for (label in name.trim('.').split('.')) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.UTF_8)
            val truncated = if (bytes.size > 63) bytes.copyOfRange(0, 63) else bytes
            write(truncated.size)
            write(truncated, 0, truncated.size)
        }
        write(0)
    }

    // MARK: - Parsing

    /**
     * Reads every answer and additional record out of a message.
     *
     * Questions are skipped, and record types we do not model are stepped over
     * by their RDLENGTH so one unknown record never derails the rest.
     */
    fun parse(bytes: ByteArray, length: Int): List<Record> {
        if (length < 12) return emptyList()

        val questionCount = readShort(bytes, 4)
        val answerCount = readShort(bytes, 6)
        val authorityCount = readShort(bytes, 8)
        val additionalCount = readShort(bytes, 10)

        var cursor = 12

        repeat(questionCount) {
            val skipped = skipName(bytes, length, cursor) ?: return emptyList()
            cursor = skipped + 4 // QTYPE + QCLASS
            if (cursor > length) return emptyList()
        }

        val records = mutableListOf<Record>()
        val total = answerCount + authorityCount + additionalCount

        repeat(total) {
            if (cursor >= length) return records
            val name = readName(bytes, length, cursor) ?: return records
            var position = name.next
            if (position + 10 > length) return records

            val type = readShort(bytes, position)
            position += 2
            position += 2 // CLASS, including the mDNS cache-flush bit
            position += 4 // TTL
            val dataLength = readShort(bytes, position)
            position += 2
            if (position + dataLength > length) return records

            val dataStart = position
            val dataEnd = position + dataLength

            when (type) {
                TYPE_PTR -> readName(bytes, length, dataStart)?.let {
                    records += Record.Ptr(name.labels, it.labels)
                }

                TYPE_SRV -> if (dataLength >= 7) {
                    val port = readShort(bytes, dataStart + 4)
                    readName(bytes, length, dataStart + 6)?.let {
                        records += Record.Srv(name.labels, port, it.labels)
                    }
                }

                TYPE_TXT -> records += Record.Txt(
                    name.labels,
                    readTxt(bytes, dataStart, dataEnd),
                )

                TYPE_A -> if (dataLength == 4) {
                    val address = IPv4(
                        ((bytes[dataStart].toInt() and 0xFF) shl 24) or
                            ((bytes[dataStart + 1].toInt() and 0xFF) shl 16) or
                            ((bytes[dataStart + 2].toInt() and 0xFF) shl 8) or
                            (bytes[dataStart + 3].toInt() and 0xFF),
                    )
                    records += Record.A(name.labels, address)
                }
            }
            cursor = dataEnd
        }
        return records
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private class Name(val labels: List<String>, val next: Int)

    /**
     * Decodes a name, following compression pointers.
     *
     * `next` is the offset just past the name *as written at this position* —
     * which for a pointer is two bytes, regardless of how long the name it
     * expands to turns out to be.
     */
    private fun readName(bytes: ByteArray, length: Int, start: Int): Name? {
        val labels = mutableListOf<String>()
        var cursor = start
        var next = -1
        var hops = 0

        while (cursor < length) {
            if (hops++ > MAX_LABEL_HOPS) return null
            val marker = bytes[cursor].toInt() and 0xFF

            when {
                marker == 0 -> {
                    if (next < 0) next = cursor + 1
                    return Name(labels, next)
                }

                marker and 0xC0 == 0xC0 -> {
                    if (cursor + 1 >= length) return null
                    val pointer = ((marker and 0x3F) shl 8) or (bytes[cursor + 1].toInt() and 0xFF)
                    if (pointer >= length || pointer >= cursor) return null // forward/self pointer
                    if (next < 0) next = cursor + 2
                    cursor = pointer
                }

                marker <= 63 -> {
                    val from = cursor + 1
                    val to = from + marker
                    if (to > length) return null
                    labels += String(bytes, from, marker, Charsets.UTF_8)
                    cursor = to
                }

                else -> return null // reserved label type
            }
        }
        return null
    }

    /** Steps over a name without decoding it, returning the offset just past. */
    private fun skipName(bytes: ByteArray, length: Int, start: Int): Int? {
        var cursor = start
        while (cursor < length) {
            val marker = bytes[cursor].toInt() and 0xFF
            when {
                marker == 0 -> return cursor + 1
                marker and 0xC0 == 0xC0 -> return cursor + 2
                marker <= 63 -> cursor += 1 + marker
                else -> return null
            }
        }
        return null
    }

    /**
     * Splits TXT rdata into key/value pairs.
     *
     * Each entry is a length-prefixed string of the form `key=value`. A bare
     * `key` with no separator is a boolean-style flag and maps to an empty
     * value, matching how the iOS build records `.empty` TXT entries.
     */
    private fun readTxt(bytes: ByteArray, start: Int, end: Int): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var cursor = start

        while (cursor < end) {
            val length = bytes[cursor].toInt() and 0xFF
            cursor += 1
            if (length == 0) continue
            if (cursor + length > end) break

            val entry = String(bytes, cursor, length, Charsets.UTF_8)
            cursor += length

            val separator = entry.indexOf('=')
            if (separator < 0) {
                if (entry.isNotEmpty()) result.putIfAbsent(entry, "")
            } else {
                val key = entry.substring(0, separator)
                if (key.isNotEmpty()) result.putIfAbsent(key, entry.substring(separator + 1))
            }
        }
        return result
    }
}
