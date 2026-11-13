package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import java.security.SecureRandom
import javax.inject.Inject

/**
 * UUIDv7 ids (RFC 9562): a 48-bit Unix-millisecond timestamp, version 7 and random bits from
 * [SecureRandom], as a lowercase hyphenated string. They sort by creation time and match the
 * text primary keys of the Sclera database. A prefix gives "prefix-<uuid>".
 */
class UuidIdGenerator @Inject constructor(private val time: TimeProvider) : IdGenerator {
    private val random = SecureRandom()

    override fun next(prefix: String): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        val ms = time.nowMillis()
        for (i in 0..5) bytes[i] = (ms ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val uuid = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        return if (prefix.isEmpty()) uuid else "$prefix-$uuid"
    }
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
