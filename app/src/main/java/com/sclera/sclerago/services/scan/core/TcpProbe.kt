//
//  TcpProbe.kt
//  AssetOnBoard
//
//  TCP connect scanning and banner grabbing.
//
//  Where the iOS build reads NWConnection's error codes to tell a refusal from a
//  timeout, the JVM already draws that distinction in its exception types:
//  ConnectException carries the RST, SocketTimeoutException the silence, and
//  NoRouteToHostException / PortUnreachableException the stack's own verdict.
//

package com.sclera.sclerago.services.scan.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class TcpOutcome {
    /** Handshake completed — the port is listening. */
    OPEN,

    /** RST came back. The port is shut, but it proves the host is up. */
    REFUSED,

    /** No answer at all: firewalled, or nothing at this address. */
    FILTERED,

    /** The network stack said the host is unreachable. */
    UNREACHABLE,
}

data class TcpExchange(
    val outcome: TcpOutcome,
    val data: ByteArray = ByteArray(0),
    val certificates: List<TlsCertificateInfo> = emptyList(),
    val tlsVersion: String? = null,
    val cipherSuite: String? = null,
    val elapsed: Double = 0.0,
) {
    override fun equals(other: Any?): Boolean =
        other is TcpExchange && outcome == other.outcome && data.contentEquals(other.data)

    override fun hashCode(): Int = outcome.hashCode() * 31 + data.contentHashCode()
}

object TcpProbe {

    private val dispatcher: CoroutineDispatcher = Dispatchers.IO

    // MARK: - Connect scan

    /** Opens a TCP connection just far enough to classify the port, then hangs up. */
    suspend fun connect(host: IPv4, port: Int, timeout: Double = 1.5): TcpOutcome =
        withContext(dispatcher) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host.toInetAddress(), port), millis(timeout))
                TcpOutcome.OPEN
            } catch (error: Exception) {
                classify(error)
            } finally {
                runCatching { socket.close() }
            }
        }

    // MARK: - Banner grab

    /**
     * Connects, optionally sends [payload], and reads whatever the service says.
     *
     * Most plaintext services (SSH, FTP, SMTP, RTSP) announce themselves without
     * being asked; HTTP needs a nudge, which is what [payload] is for.
     */
    suspend fun exchange(
        host: IPv4,
        port: Int,
        useTls: Boolean = false,
        serverName: String? = null,
        payload: ByteArray? = null,
        maxBytes: Int = 16 * 1024,
        connectTimeout: Double = 2.0,
        readTimeout: Double = 2.5,
    ): TcpExchange = withContext(dispatcher) {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000_000.0

        val plain = Socket()
        try {
            plain.tcpNoDelay = true
            plain.connect(InetSocketAddress(host.toInetAddress(), port), millis(connectTimeout))
        } catch (error: Exception) {
            runCatching { plain.close() }
            return@withContext TcpExchange(outcome = classify(error), elapsed = elapsed())
        }

        var certificates: List<TlsCertificateInfo> = emptyList()
        var tlsVersion: String? = null
        var cipherSuite: String? = null
        var socket: Socket = plain

        if (useTls) {
            val secure = try {
                startTls(plain, host, port, serverName, millis(readTimeout))
            } catch (_: Exception) {
                // A device that accepts the connection but fails the handshake is
                // still a live listener; report the port without certificate data.
                runCatching { plain.close() }
                return@withContext TcpExchange(outcome = TcpOutcome.OPEN, elapsed = elapsed())
            }
            socket = secure
            val session = secure.session
            certificates = TlsCertificateInfo.from(session)
            tlsVersion = describeProtocol(session.protocol)
            cipherSuite = session.cipherSuite
        }

        val received = try {
            socket.soTimeout = millis(readTimeout)
            if (payload != null) {
                socket.getOutputStream().apply {
                    write(payload)
                    flush()
                }
            }
            read(socket, maxBytes, readTimeout)
        } catch (_: Exception) {
            ByteArray(0)
        } finally {
            runCatching { socket.close() }
        }

        TcpExchange(
            outcome = TcpOutcome.OPEN,
            data = received,
            certificates = certificates,
            tlsVersion = tlsVersion,
            cipherSuite = cipherSuite,
            elapsed = elapsed(),
        )
    }

    private fun read(socket: Socket, maxBytes: Int, timeout: Double): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        val deadline = System.nanoTime() + (timeout * 1_000_000_000L).toLong()
        val stream = socket.getInputStream()

        while (buffer.size() < maxBytes && System.nanoTime() < deadline) {
            val remaining = minOf(chunk.size, maxBytes - buffer.size())
            val count = try {
                stream.read(chunk, 0, remaining)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: Exception) {
                break
            }
            if (count <= 0) break
            buffer.write(chunk, 0, count)
        }
        return buffer.toByteArray()
    }

    // MARK: - Plumbing

    private fun millis(seconds: Double): Int =
        maxOf(200, (seconds * 1000).toInt())

    private fun classify(error: Exception): TcpOutcome = when (error) {
        is SocketTimeoutException -> TcpOutcome.FILTERED
        is NoRouteToHostException -> TcpOutcome.UNREACHABLE
        is PortUnreachableException -> TcpOutcome.REFUSED
        is ConnectException -> {
            // A refusal is a genuine RST and proves the host is up. Some kernels
            // surface ETIMEDOUT here too, which is indistinguishable from a
            // firewalled port, so the message is what separates the two.
            val message = error.message?.lowercase().orEmpty()
            when {
                "refused" in message -> TcpOutcome.REFUSED
                "unreachable" in message -> TcpOutcome.UNREACHABLE
                else -> TcpOutcome.FILTERED
            }
        }
        else -> TcpOutcome.FILTERED
    }

    /**
     * Wraps a connected socket in TLS without verifying anything.
     *
     * A scanner is inventorying certificates, not trusting them, so the
     * permissive trust manager is the point rather than an oversight — it is what
     * lets self-signed NAS boxes, routers and printers report their identity. The
     * enabled-protocol list reaches back to TLS 1.0 for the same reason: ageing
     * appliances never moved past it, and recording that fact is useful.
     */
    private fun startTls(
        plain: Socket,
        host: IPv4,
        port: Int,
        serverName: String?,
        handshakeTimeoutMillis: Int,
    ): SSLSocket {
        val factory = PermissiveTls.socketFactory()
        val socket = factory.createSocket(
            plain,
            serverName ?: host.toString(),
            port,
            true,
        ) as SSLSocket

        socket.soTimeout = handshakeTimeoutMillis
        socket.useClientMode = true
        val supported = socket.supportedProtocols.toSet()
        val wanted = listOf("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3")
            .filter { it in supported }
        if (wanted.isNotEmpty()) socket.enabledProtocols = wanted.toTypedArray()
        socket.enabledCipherSuites = socket.supportedCipherSuites
        socket.startHandshake()
        return socket
    }

    private fun describeProtocol(protocol: String?): String? = when (protocol) {
        "TLSv1" -> "TLS 1.0"
        "TLSv1.1" -> "TLS 1.1"
        "TLSv1.2" -> "TLS 1.2"
        "TLSv1.3" -> "TLS 1.3"
        "SSLv3" -> "SSL 3.0"
        null, "NONE" -> null
        else -> protocol
    }

    private object PermissiveTls {
        // A scanner inventories certificates, it does not rely on them: nothing
        // is sent to the peer and nothing it returns is trusted, only recorded.
        // Validating would mean losing the identity of every self-signed NAS,
        // router and printer on the segment, which is most of what is worth
        // finding. No app traffic ever crosses one of these sockets.
        @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
        private val factory: SSLSocketFactory by lazy {
            val trustEverything = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out java.security.cert.X509Certificate>?,
                        authType: String?,
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<out java.security.cert.X509Certificate>?,
                        authType: String?,
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                        emptyArray()
                },
            )
            javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustEverything, java.security.SecureRandom())
            }.socketFactory
        }

        fun socketFactory(): SSLSocketFactory = factory
    }
}
