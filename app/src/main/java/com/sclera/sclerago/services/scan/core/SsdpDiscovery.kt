//
//  SsdpDiscovery.kt
//
//  UPnP discovery (SSDP). Routers, smart TVs, media players, IP cameras, NAS boxes, smart plugs and many printers
//  announce themselves this way rather than over Bonjour or NetBIOS. One M-SEARCH to the SSDP multicast group makes
//  every UPnP device on our own link answer (unicast, so no multicast lock is needed) with the address of its
//  description; that small XML file gives its own name, maker and model.
//
//  Everything a device sends is untrusted: a description is only fetched from the very address that answered, over
//  plain http on the local link, with tight time and size limits, and every value is cleaned and shortened.
//

package com.sclera.sclerago.services.scan.core

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL

/** What a UPnP device says about itself (the root `<device>` of its description). */
@Serializable
data class UpnpInfo(
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelNumber: String? = null,
    /** Its UPnP device type without the URN dressing: "MediaRenderer", "InternetGatewayDevice", "Printer"… */
    val deviceType: String? = null,
)

object SsdpDiscovery {
    private const val GROUP = "239.255.255.250"
    private const val PORT = 1900
    private const val RECEIVE_TIMEOUT_MS = 400
    private const val MAX_DATAGRAM = 2048
    private const val MAX_DEVICES = 256
    private const val MAX_DESCRIPTION_BYTES = 64 * 1024
    private const val MAX_TEXT = 80
    private val TARGETS = listOf("ssdp:all", "upnp:rootdevice")
    private val FIELDS = setOf("friendlyName", "manufacturer", "modelName", "modelNumber", "deviceType")

    /**
     * Asks the UPnP devices on our own link where their descriptions are, listening for [listenMs]. Host → the URL of
     * its description (only ones on that same host). Empty when nothing answers or the network blocks multicast.
     */
    suspend fun search(listenMs: Long = 3_000): Map<IPv4, URL> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<IPv4, URL>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                val group = InetAddress.getByName(GROUP)
                for (target in TARGETS) {
                    val message = "M-SEARCH * HTTP/1.1\r\nHOST: $GROUP:$PORT\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n"
                        .toByteArray(Charsets.US_ASCII)
                    socket.send(DatagramPacket(message, message.size, group, PORT))
                }
                val buffer = ByteArray(MAX_DATAGRAM)
                val end = System.currentTimeMillis() + listenMs
                while (isActive && System.currentTimeMillis() < end && found.size < MAX_DEVICES) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val sender = IPv4.from(packet.address) ?: continue
                    if (sender in found) continue
                    val location = headerValue(String(packet.data, 0, packet.length, Charsets.ISO_8859_1), "location") ?: continue
                    descriptionUrl(location, sender)?.let { found[sender] = it }
                }
            }
        }
        found
    }

    /** The description at [url] (from [search]): the device's name, maker, model and type, or null if it can't be read. */
    suspend fun describe(url: URL, timeoutMs: Int = 2_000): UpnpInfo? = withContext(Dispatchers.IO) {
        runCatching { fetch(url, timeoutMs)?.let(::parse) }.getOrNull()
    }

    /** A header's value in an SSDP reply ("LOCATION: http://…"). */
    private fun headerValue(reply: String, name: String): String? =
        reply.lineSequence().firstNotNullOfOrNull { line ->
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals(name, ignoreCase = true)) line.substring(colon + 1).trim() else null
        }?.takeIf { it.isNotEmpty() }

    /** [location] as a URL only when it is plain http on the very host that answered (never another host or a name). */
    private fun descriptionUrl(location: String, sender: IPv4): URL? {
        val url = runCatching { URL(location) }.getOrNull() ?: return null
        if (!url.protocol.equals("http", ignoreCase = true)) return null
        if (IPv4.parse(url.host) != sender) return null
        if (url.port != -1 && url.port !in 1..65535) return null
        val path = url.file.ifEmpty { "/" }
        if (!path.startsWith("/") || path.any { it.isWhitespace() || it.isISOControl() }) return null
        return url
    }

    /** A plain HTTP/1.0 GET of [url]'s body, at most [MAX_DESCRIPTION_BYTES]; null unless the device answers 200. */
    private fun fetch(url: URL, timeoutMs: Int): String? {
        val port = if (url.port > 0) url.port else 80
        Socket().use { socket ->
            // The host is the literal address checked in descriptionUrl: no name lookup happens.
            socket.connect(InetSocketAddress(url.host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val request = "GET ${url.file.ifEmpty { "/" }} HTTP/1.0\r\nHost: ${url.host}:$port\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            // At most twice the time limit in all, however slowly the device sends it.
            val text = String(readCapped(socket.getInputStream(), System.currentTimeMillis() + 2L * timeoutMs), Charsets.UTF_8)
            val headerEnd = text.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: return null
            val status = text.substringBefore("\r\n").split(' ').getOrNull(1)
            if (!text.startsWith("HTTP/") || status != "200") return null
            return text.substring(headerEnd + 4)
        }
    }

    private fun readCapped(input: InputStream, deadline: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (out.size() < MAX_DESCRIPTION_BYTES && System.currentTimeMillis() < deadline) {
            val n = input.read(chunk, 0, minOf(chunk.size, MAX_DESCRIPTION_BYTES - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    /** The root device's fields: the first of each in the document (embedded devices are listed after it). */
    private fun parse(xml: String): UpnpInfo? {
        val parser = Xml.newPullParser()
        // Android's parser doesn't fetch external entities or DTDs; namespaces are ignored to match tags by name.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val values = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && values.size < FIELDS.size) {
            if (event == XmlPullParser.START_TAG && parser.name in FIELDS && parser.name !in values) {
                val name = parser.name
                if (parser.next() == XmlPullParser.TEXT) clean(parser.text)?.let { values[name] = it }
            }
            event = parser.next()
        }
        if (values.isEmpty()) return null
        return UpnpInfo(
            friendlyName = values["friendlyName"],
            manufacturer = values["manufacturer"],
            modelName = values["modelName"],
            modelNumber = values["modelNumber"],
            deviceType = values["deviceType"]?.substringAfter(":device:", "")?.substringBefore(':')?.let(::clean),
        )
    }

    /** One line of printable text, at most [MAX_TEXT] characters; null when nothing is left. */
    private fun clean(raw: String?): String? =
        raw?.filter { !it.isISOControl() }?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.joinToString(" ")
            ?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() }
}
