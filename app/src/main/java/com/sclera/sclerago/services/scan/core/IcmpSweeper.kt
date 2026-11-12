//
//  IcmpSweeper.kt
//  AssetOnBoard
//
//  Subnet-wide ICMP echo sweep over an unprivileged SOCK_DGRAM socket.
//
//  Android has set `net.ipv4.ping_group_range` wide open since Lollipop, so any
//  app may create SOCK_DGRAM/IPPROTO_ICMP — the same mechanism `ping` and
//  `InetAddress.isReachable` use. java.net has no binding for it, so this goes
//  through android.system.Os, which is the managed-code face of the same
//  syscalls the iOS build uses through Darwin.
//
//  Two kernel quirks drive this code. The kernel overwrites the echo identifier
//  with the socket's implicit port, so replies are matched on a payload token
//  instead. And Linux ping sockets hand back the ICMP message with the IP header
//  already stripped — unlike Darwin — so the reply's TTL is usually unavailable;
//  the parser reads it when a header is present and reports 0 otherwise. TTL is
//  only ever a hint in the fingerprinter, never a requirement.
//

package com.sclera.sclerago.services.scan.core

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.InterruptedIOException
import java.net.InetSocketAddress

data class IcmpReply(
    val address: IPv4,
    /** Round-trip time derived from the timestamp echoed back in the payload. */
    val roundTripTime: Double,
    /** IP header TTL of the reply, or 0 when the kernel stripped the header. */
    val ttl: Int,
)

class IcmpUnavailableException(val errno: Int) :
    Exception("ICMP socket unavailable (errno $errno). Ping sweep skipped.")

class IcmpSweeper(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    /**
     * Pings every target and returns replies keyed by responding address.
     *
     * @param rounds how many echo requests to send per host; retries cover loss.
     * @param timeout how long to keep listening after the last request goes out.
     * @param onReply called as each first reply arrives, so the UI can populate
     *   while the sweep is still running.
     */
    suspend fun sweep(
        targets: List<IPv4>,
        rounds: Int = 2,
        timeout: Double = 1.2,
        onReply: ((IcmpReply) -> Unit)? = null,
    ): Map<IPv4, IcmpReply> {
        if (targets.isEmpty()) return emptyMap()
        return withContext(dispatcher) {
            // The sweep is one long blocking syscall loop, so cancellation has to
            // be handed in explicitly: a cancelled coroutine cannot interrupt a
            // thread parked in recvfrom, and on a /20 that would leave Stop
            // unresponsive for the rest of the stage.
            val stillWanted = { isActive }
            runSweep(targets, rounds, timeout, onReply, stillWanted)
        }
    }

    // MARK: - Blocking implementation

    private fun runSweep(
        targets: List<IPv4>,
        rounds: Int,
        timeout: Double,
        onReply: ((IcmpReply) -> Unit)?,
        stillWanted: () -> Boolean,
    ): Map<IPv4, IcmpReply> {
        val fd: FileDescriptor = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
        } catch (error: ErrnoException) {
            throw IcmpUnavailableException(error.errno)
        }

        try {
            runCatching {
                Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDBUF, 128 * 1024)
            }

            val token = (Math.random() * Int.MAX_VALUE).toInt() or 1
            val replies = LinkedHashMap<IPv4, IcmpReply>()
            val effectiveRounds = maxOf(rounds, 1)

            for (round in 0 until effectiveRounds) {
                if (!stillWanted()) return replies
                targets.forEachIndexed { index, target ->
                    // Skip hosts that already answered an earlier round.
                    if (round > 0 && replies.containsKey(target)) return@forEachIndexed
                    send(fd, target, (index and 0xFFFF), token)

                    // Drain in-flight replies periodically so the kernel buffer
                    // never overflows during a large sweep.
                    if (index % 48 == 47) {
                        receive(fd, System.nanoTime(), token, replies, onReply, stillWanted)
                    }
                }
                val window = if (round == effectiveRounds - 1) timeout else 0.35
                val deadline = System.nanoTime() + (window * 1_000_000_000L).toLong()
                receive(fd, deadline, token, replies, onReply, stillWanted)
            }
            return replies
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    private fun send(fd: FileDescriptor, target: IPv4, sequence: Int, token: Int) {
        val packet = ByteArray(8 + PAYLOAD_SIZE)
        packet[0] = 8 // ICMP echo request
        packet[1] = 0 // code
        // Bytes 2-3 checksum, 4-5 identifier (kernel-assigned), 6-7 sequence.
        packet[6] = (sequence ushr 8).toByte()
        packet[7] = (sequence and 0xFF).toByte()

        var cursor = 8
        for (byte in MAGIC) packet[cursor++] = byte
        for (shift in intArrayOf(24, 16, 8, 0)) {
            packet[cursor++] = (token ushr shift and 0xFF).toByte()
        }
        packet[cursor++] = (sequence ushr 8).toByte()
        packet[cursor++] = (sequence and 0xFF).toByte()

        val stamp = System.nanoTime()
        for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) {
            packet[cursor++] = (stamp ushr shift and 0xFF).toByte()
        }

        val sum = checksum(packet)
        packet[2] = (sum ushr 8).toByte()
        packet[3] = (sum and 0xFF).toByte()

        try {
            Os.sendto(fd, packet, 0, packet.size, 0, target.toInetAddress(), 0)
        } catch (error: ErrnoException) {
            // ENOBUFS means we outran the interface queue; a brief pause lets it
            // drain. Anything else is this one host's problem, not the sweep's.
            if (error.errno == OsConstants.ENOBUFS) {
                try {
                    Thread.sleep(2)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        } catch (_: Exception) {
            // Unreachable literal, socket torn down mid-sweep: keep going.
        }
    }

    /**
     * Drains replies until [deadlineNanos], or until the scan is cancelled.
     *
     * Readiness is decided by poll(2) rather than an SO_RCVTIMEO on the socket:
     * `Os.setsockoptTimeval` only exists from API 29, and polling in short slices
     * means Stop is noticed within a slice instead of at the end of the stage.
     */
    private fun receive(
        fd: FileDescriptor,
        deadlineNanos: Long,
        token: Int,
        replies: MutableMap<IPv4, IcmpReply>,
        onReply: ((IcmpReply) -> Unit)?,
        stillWanted: () -> Boolean,
    ) {
        val buffer = ByteArray(1500)
        val pollfd = StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        }
        val fds = arrayOf(pollfd)

        do {
            if (!stillWanted()) return

            val ready = try {
                pollfd.revents = 0
                Os.poll(fds, POLL_SLICE_MS)
            } catch (error: ErrnoException) {
                // EINTR just means a signal landed mid-poll; try again.
                if (error.errno == OsConstants.EINTR) continue
                return
            } catch (_: Exception) {
                return
            }
            if (ready <= 0) continue

            val source = InetSocketAddress(0)
            val count = try {
                Os.recvfrom(fd, buffer, 0, buffer.size, 0, source)
            } catch (error: ErrnoException) {
                // On Linux EWOULDBLOCK is EAGAIN, and OsConstants exposes only
                // the latter; either way nothing was actually queued.
                if (error.errno == OsConstants.EAGAIN) continue
                return
            } catch (_: InterruptedIOException) {
                continue
            } catch (_: Exception) {
                return
            }
            if (count <= 0) continue

            val from = source.address?.let { IPv4.from(it) } ?: continue
            val reply = parseReply(buffer, count, from, token) ?: continue

            if (!replies.containsKey(reply.address)) {
                replies[reply.address] = reply
                onReply?.invoke(reply)
            }
        } while (System.nanoTime() < deadlineNanos)
    }

    private companion object {
        /** How long one poll waits before the loop re-checks its deadline. */
        const val POLL_SLICE_MS = 60

        val MAGIC: ByteArray = "AOB1".toByteArray(Charsets.US_ASCII)

        /** magic + token + sequence + timestamp */
        const val PAYLOAD_SIZE = 4 + 4 + 2 + 8

        fun parseReply(bytes: ByteArray, length: Int, source: IPv4, token: Int): IcmpReply? {
            var offset = 0
            var ttl = 0

            // Darwin delivers the IP header with the payload; Linux ping sockets
            // strip it. Detect which we got instead of assuming.
            if (length > 20 && (bytes[0].toInt() and 0xFF) ushr 4 == 4) {
                val headerLength = (bytes[0].toInt() and 0x0F) * 4
                if (headerLength >= 20 && length > headerLength) {
                    ttl = bytes[8].toInt() and 0xFF
                    offset = headerLength
                }
            }

            if (length < offset + 8 + PAYLOAD_SIZE) return null
            // Echo reply is type 0, code 0.
            if (bytes[offset].toInt() != 0 || bytes[offset + 1].toInt() != 0) return null

            var cursor = offset + 8
            for (expected in MAGIC) {
                if (bytes[cursor] != expected) return null
                cursor += 1
            }

            var receivedToken = 0
            repeat(4) {
                receivedToken = receivedToken shl 8 or (bytes[cursor].toInt() and 0xFF)
                cursor += 1
            }
            if (receivedToken != token) return null

            cursor += 2 // sequence, already implied by the source address

            var stamp = 0L
            repeat(8) {
                stamp = stamp shl 8 or (bytes[cursor].toLong() and 0xFF)
                cursor += 1
            }
            val elapsed = (System.nanoTime() - stamp) / 1_000_000_000.0
            if (!elapsed.isFinite() || elapsed < 0 || elapsed > 30) return null

            return IcmpReply(address = source, roundTripTime = elapsed, ttl = ttl)
        }

        /** Standard 16-bit one's-complement checksum (RFC 1071). */
        fun checksum(bytes: ByteArray): Int {
            var sum = 0L
            var index = 0
            while (index + 1 < bytes.size) {
                sum += ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
                index += 2
            }
            if (index < bytes.size) sum += (bytes[index].toInt() and 0xFF) shl 8
            while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
            return sum.inv().toInt() and 0xFFFF
        }
    }
}
