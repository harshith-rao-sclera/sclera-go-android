//
//  ServiceProbe.kt
//  AssetOnBoard
//
//  The port catalogue and per-service interrogation.
//

package com.sclera.sclerago.services.scan.core

import kotlinx.serialization.Serializable

enum class ProbeKind {
    /** Send a bare GET and read the response. */
    HTTP,

    /** Same, wrapped in TLS, which also captures the certificate. */
    HTTPS,

    /** Say nothing and read whatever the service volunteers. */
    BANNER,

    /** Connect only; the port number itself is the signal. */
    CONNECT_ONLY,
}

data class PortDefinition(
    val port: Int,
    val name: String,
    val detail: String,
    val probe: ProbeKind,
) {
    override fun equals(other: Any?): Boolean = other is PortDefinition && port == other.port
    override fun hashCode(): Int = port
}

@Serializable
data class PortObservation(
    val port: Int,
    val serviceName: String,
    val detail: String,
    val banner: String? = null,
    val httpStatus: Int? = null,
    val httpServer: String? = null,
    val httpTitle: String? = null,
    val httpRealm: String? = null,
    val httpRedirect: String? = null,
    val certificates: List<TlsCertificateInfo> = emptyList(),
    val tlsVersion: String? = null,
    val responseTime: Double = 0.0,
) {
    /** The single most descriptive line we can show for this port. */
    val summary: String
        get() {
            httpTitle?.takeIf { it.isNotEmpty() }?.let { return it }
            httpRealm?.takeIf { it.isNotEmpty() }?.let { return "Auth realm: $it" }
            httpServer?.takeIf { it.isNotEmpty() }?.let { return it }
            certificates.firstOrNull()?.displayName?.let { return it }
            banner?.takeIf { it.isNotEmpty() }?.let { return it }
            return detail
        }
}

enum class ScanDepth(val label: String) {
    QUICK("Quick"),
    STANDARD("Standard"),
    DEEP("Deep"),
    ;

    val subtitle: String
        get() = when (this) {
            QUICK -> "16 ports · fastest"
            STANDARD -> "48 ports · balanced"
            DEEP -> "120+ ports · thorough"
        }

    val ports: List<PortDefinition>
        get() = when (this) {
            QUICK -> ServiceCatalog.all.take(16)
            STANDARD -> ServiceCatalog.all.take(48)
            DEEP -> ServiceCatalog.all
        }

    /**
     * Concurrent probes in flight, clamped to the descriptor budget the process
     * actually has.
     */
    val concurrency: Int
        get() {
            val preferred = when (this) {
                QUICK -> 128
                STANDARD -> 160
                DEEP -> 192
            }
            return SystemLimits.socketBudget(preferred)
        }

    /** How many discovery ports to knock on per silent host. */
    val knockPortCount: Int
        get() = when (this) {
            QUICK -> 6
            STANDARD -> 12
            DEEP -> ServiceCatalog.discoveryKnockPorts.size
        }

    /**
     * How long to wait for a handshake. Firewalled ports never answer, so this
     * timeout — not the round trip — sets the pace of the whole scan.
     */
    val connectTimeout: Double
        get() = when (this) {
            QUICK -> 0.7
            STANDARD -> 1.2
            DEEP -> 2.0
        }
}

object ServiceCatalog {

    /**
     * Ordered by how much each port reveals about a device, because the Quick
     * and Standard depths simply take a prefix of this list.
     */
    val all: List<PortDefinition> = deduplicated(
        listOf(
            PortDefinition(80, "HTTP", "Web interface", ProbeKind.HTTP),
            PortDefinition(443, "HTTPS", "Secure web interface", ProbeKind.HTTPS),
            PortDefinition(22, "SSH", "Secure shell", ProbeKind.BANNER),
            PortDefinition(445, "SMB", "Windows / Samba file sharing", ProbeKind.CONNECT_ONLY),
            PortDefinition(139, "NetBIOS-SSN", "Legacy Windows file sharing", ProbeKind.CONNECT_ONLY),
            PortDefinition(62078, "lockdownd", "iPhone / iPad pairing service", ProbeKind.CONNECT_ONLY),
            PortDefinition(8080, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(8443, "HTTPS-alt", "Alternate secure web interface", ProbeKind.HTTPS),
            PortDefinition(5000, "UPnP / Synology", "Device web service", ProbeKind.HTTP),
            PortDefinition(9100, "JetDirect", "Raw printing", ProbeKind.CONNECT_ONLY),
            PortDefinition(631, "IPP", "Internet Printing Protocol", ProbeKind.HTTP),
            PortDefinition(548, "AFP", "Apple File Protocol", ProbeKind.CONNECT_ONLY),
            PortDefinition(3389, "RDP", "Windows Remote Desktop", ProbeKind.CONNECT_ONLY),
            PortDefinition(5900, "VNC", "Screen sharing", ProbeKind.BANNER),
            PortDefinition(23, "Telnet", "Unencrypted shell", ProbeKind.BANNER),
            PortDefinition(53, "DNS", "Name server", ProbeKind.CONNECT_ONLY),

            PortDefinition(8008, "Chromecast", "Google Cast control", ProbeKind.HTTP),
            PortDefinition(8009, "Cast TLS", "Google Cast channel", ProbeKind.CONNECT_ONLY),
            PortDefinition(1400, "Sonos", "Sonos control", ProbeKind.HTTP),
            PortDefinition(32400, "Plex", "Plex Media Server", ProbeKind.HTTP),
            PortDefinition(8123, "Home Assistant", "Home automation", ProbeKind.HTTP),
            PortDefinition(7000, "AirPlay", "AirPlay receiver", ProbeKind.HTTP),
            PortDefinition(5001, "Synology HTTPS", "DSM secure interface", ProbeKind.HTTPS),
            PortDefinition(21, "FTP", "File transfer", ProbeKind.BANNER),
            PortDefinition(25, "SMTP", "Mail transfer", ProbeKind.BANNER),
            PortDefinition(110, "POP3", "Mail retrieval", ProbeKind.BANNER),
            PortDefinition(143, "IMAP", "Mail access", ProbeKind.BANNER),
            PortDefinition(554, "RTSP", "Camera / media stream", ProbeKind.BANNER),
            PortDefinition(3306, "MySQL", "Database", ProbeKind.BANNER),
            PortDefinition(5432, "PostgreSQL", "Database", ProbeKind.CONNECT_ONLY),
            PortDefinition(6379, "Redis", "Key-value store", ProbeKind.CONNECT_ONLY),
            PortDefinition(27017, "MongoDB", "Database", ProbeKind.CONNECT_ONLY),
            PortDefinition(5555, "ADB", "Android Debug Bridge", ProbeKind.CONNECT_ONLY),
            PortDefinition(515, "LPD", "Line printer daemon", ProbeKind.CONNECT_ONLY),
            PortDefinition(111, "Portmapper", "ONC RPC", ProbeKind.CONNECT_ONLY),
            PortDefinition(2049, "NFS", "Network file system", ProbeKind.CONNECT_ONLY),
            PortDefinition(135, "MSRPC", "Windows RPC endpoint mapper", ProbeKind.CONNECT_ONLY),
            PortDefinition(3283, "ARD", "Apple Remote Desktop", ProbeKind.CONNECT_ONLY),
            PortDefinition(88, "Kerberos", "Authentication / HomeKit", ProbeKind.CONNECT_ONLY),
            PortDefinition(389, "LDAP", "Directory service", ProbeKind.CONNECT_ONLY),
            PortDefinition(873, "rsync", "File synchronisation", ProbeKind.BANNER),
            PortDefinition(993, "IMAPS", "Secure mail access", ProbeKind.HTTPS),
            PortDefinition(995, "POP3S", "Secure mail retrieval", ProbeKind.HTTPS),
            PortDefinition(1883, "MQTT", "IoT message broker", ProbeKind.CONNECT_ONLY),
            PortDefinition(8081, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(8000, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(10000, "Webmin", "Server administration", ProbeKind.HTTPS),
            PortDefinition(9000, "Portainer / PHP-FPM", "Management interface", ProbeKind.HTTP),

            PortDefinition(81, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(82, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(591, "FileMaker", "Web sharing", ProbeKind.HTTP),
            PortDefinition(8006, "Proxmox", "Virtualisation manager", ProbeKind.HTTPS),
            PortDefinition(8086, "InfluxDB", "Time-series database", ProbeKind.HTTP),
            PortDefinition(8096, "Jellyfin", "Media server", ProbeKind.HTTP),
            PortDefinition(8200, "Synology / DLNA", "Media service", ProbeKind.HTTP),
            PortDefinition(8888, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(9090, "Prometheus / Cockpit", "Monitoring", ProbeKind.HTTP),
            PortDefinition(9200, "Elasticsearch", "Search cluster", ProbeKind.HTTP),
            PortDefinition(3000, "Grafana / Node", "Application server", ProbeKind.HTTP),
            PortDefinition(5357, "WSD", "Web Services for Devices", ProbeKind.HTTP),
            PortDefinition(1900, "SSDP", "UPnP discovery", ProbeKind.CONNECT_ONLY),
            PortDefinition(49152, "UPnP", "Dynamic UPnP service", ProbeKind.HTTP),
            PortDefinition(5060, "SIP", "Voice over IP", ProbeKind.CONNECT_ONLY),
            PortDefinition(1723, "PPTP", "Legacy VPN", ProbeKind.CONNECT_ONLY),
            PortDefinition(500, "IKE", "IPsec key exchange", ProbeKind.CONNECT_ONLY),
            PortDefinition(1194, "OpenVPN", "VPN tunnel", ProbeKind.CONNECT_ONLY),
            PortDefinition(1433, "MSSQL", "Microsoft SQL Server", ProbeKind.CONNECT_ONLY),
            PortDefinition(1521, "Oracle", "Database listener", ProbeKind.CONNECT_ONLY),
            PortDefinition(2375, "Docker", "Unencrypted Docker API", ProbeKind.HTTP),
            PortDefinition(2376, "Docker TLS", "Docker API", ProbeKind.HTTPS),
            PortDefinition(5222, "XMPP", "Messaging", ProbeKind.CONNECT_ONLY),
            PortDefinition(6000, "X11", "Remote display", ProbeKind.CONNECT_ONLY),
            PortDefinition(7100, "Printer", "Font / print service", ProbeKind.CONNECT_ONLY),
            PortDefinition(11211, "memcached", "Cache", ProbeKind.CONNECT_ONLY),
            PortDefinition(51413, "Transmission", "BitTorrent", ProbeKind.CONNECT_ONLY),
            PortDefinition(4070, "Spotify Connect", "Playback endpoint", ProbeKind.CONNECT_ONLY),
            PortDefinition(3689, "DAAP", "iTunes / Music sharing", ProbeKind.HTTP),
            PortDefinition(902, "VMware", "ESXi agent", ProbeKind.BANNER),
            PortDefinition(465, "SMTPS", "Secure mail transfer", ProbeKind.HTTPS),
            PortDefinition(587, "Submission", "Mail submission", ProbeKind.BANNER),
            PortDefinition(636, "LDAPS", "Secure directory", ProbeKind.HTTPS),
            PortDefinition(989, "FTPS data", "Secure file transfer", ProbeKind.CONNECT_ONLY),
            PortDefinition(990, "FTPS", "Secure file transfer", ProbeKind.HTTPS),
            PortDefinition(1080, "SOCKS", "Proxy", ProbeKind.CONNECT_ONLY),
            PortDefinition(2082, "cPanel", "Hosting control panel", ProbeKind.HTTP),
            PortDefinition(2083, "cPanel TLS", "Hosting control panel", ProbeKind.HTTPS),
            PortDefinition(2222, "SSH-alt", "Alternate secure shell", ProbeKind.BANNER),
            PortDefinition(3128, "Squid", "HTTP proxy", ProbeKind.HTTP),
            PortDefinition(4444, "Alt service", "Assorted services", ProbeKind.CONNECT_ONLY),
            PortDefinition(5010, "Telepathy", "Assorted services", ProbeKind.CONNECT_ONLY),
            PortDefinition(5800, "VNC HTTP", "Browser screen sharing", ProbeKind.HTTP),
            PortDefinition(5901, "VNC :1", "Screen sharing", ProbeKind.BANNER),
            PortDefinition(5984, "CouchDB", "Database", ProbeKind.HTTP),
            PortDefinition(6667, "IRC", "Chat", ProbeKind.BANNER),
            PortDefinition(7001, "AirPlay alt", "Media endpoint", ProbeKind.CONNECT_ONLY),
            PortDefinition(7070, "RTSP alt", "Media stream", ProbeKind.CONNECT_ONLY),
            PortDefinition(8060, "Roku ECP", "Roku external control", ProbeKind.HTTP),
            PortDefinition(8089, "Splunk", "Management port", ProbeKind.HTTPS),
            PortDefinition(8112, "Deluge", "BitTorrent client", ProbeKind.HTTP),
            PortDefinition(8181, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(8291, "MikroTik", "Winbox management", ProbeKind.CONNECT_ONLY),
            PortDefinition(8883, "MQTT TLS", "Secure IoT broker", ProbeKind.HTTPS),
            PortDefinition(9091, "Transmission Web", "BitTorrent UI", ProbeKind.HTTP),
            PortDefinition(9443, "HTTPS-alt", "Management interface", ProbeKind.HTTPS),
            PortDefinition(10001, "Ubiquiti", "Device discovery", ProbeKind.CONNECT_ONLY),
            PortDefinition(32469, "Plex DLNA", "Media sharing", ProbeKind.HTTP),
            PortDefinition(49153, "UPnP alt", "Dynamic UPnP service", ProbeKind.HTTP),
            PortDefinition(5988, "WBEM", "Hardware management", ProbeKind.HTTP),
            PortDefinition(5989, "WBEM TLS", "Hardware management", ProbeKind.HTTPS),
            PortDefinition(7080, "LiteSpeed", "Web server admin", ProbeKind.HTTP),
            PortDefinition(8010, "HTTP-alt", "Alternate web interface", ProbeKind.HTTP),
            PortDefinition(8098, "Riak", "Database", ProbeKind.HTTP),
            PortDefinition(9999, "Alt admin", "Assorted services", ProbeKind.HTTP),
        ),
    )

    /**
     * Deduplicated by port, keeping the first (highest-signal) definition.
     * Scanning the same port twice wastes a socket and shows a duplicate row.
     */
    private fun deduplicated(definitions: List<PortDefinition>): List<PortDefinition> {
        val seen = HashSet<Int>()
        return definitions.filter { seen.add(it.port) }
    }

    /**
     * Ports knocked on during discovery, ordered by how many otherwise-silent
     * hosts they reveal.
     *
     * Windows leads the list deliberately: a stock Windows machine drops ICMP
     * and listens on none of the usual web/SSH ports, but almost always answers
     * on WSD (5357) or MSRPC (135). Measured on a live /23, those two alone
     * accounted for most of the hosts a ping sweep never sees.
     */
    val discoveryKnockPorts: List<Int> = listOf(
        5357, // WSD — Windows network discovery
        445, // SMB
        80, 443,
        135, // MSRPC — domain-joined Windows
        139,
        62078, // lockdownd — iPhone / iPad
        22,
        3389, // RDP
        8080,
        5985, // WinRM
        5040, // Windows connected-devices platform
        53,
        9100, 631,
        5000,
    )
}

object ServiceProbe {

    /** Interrogates one port, returning null when nothing is listening. */
    suspend fun probe(
        host: IPv4,
        definition: PortDefinition,
        connectTimeout: Double,
    ): PortObservation? {
        var observation = PortObservation(
            port = definition.port,
            serviceName = definition.name,
            detail = definition.detail,
        )

        when (definition.probe) {
            ProbeKind.CONNECT_ONLY -> {
                val outcome = TcpProbe.connect(host, definition.port, connectTimeout)
                if (outcome != TcpOutcome.OPEN) return null
            }

            ProbeKind.BANNER -> {
                val exchange = TcpProbe.exchange(
                    host = host,
                    port = definition.port,
                    maxBytes = 2048,
                    connectTimeout = connectTimeout,
                    readTimeout = 1.5,
                )
                if (exchange.outcome != TcpOutcome.OPEN) return null
                observation = observation.copy(
                    banner = cleanBanner(exchange.data),
                    responseTime = exchange.elapsed,
                )
            }

            ProbeKind.HTTP, ProbeKind.HTTPS -> {
                val useTls = definition.probe == ProbeKind.HTTPS
                val request = httpRequest(host, definition.port, useTls)
                val exchange = TcpProbe.exchange(
                    host = host,
                    port = definition.port,
                    useTls = useTls,
                    serverName = host.toString(),
                    payload = request,
                    connectTimeout = connectTimeout,
                    readTimeout = 2.5,
                )
                if (exchange.outcome != TcpOutcome.OPEN) return null
                observation = observation.copy(
                    certificates = exchange.certificates,
                    tlsVersion = exchange.tlsVersion,
                    responseTime = exchange.elapsed,
                )
                if (exchange.data.isNotEmpty()) {
                    observation = applyHttpResponse(exchange.data, observation)
                }
            }
        }
        return observation
    }

    private fun httpRequest(host: IPv4, port: Int, secure: Boolean): ByteArray {
        val defaultPort = if (secure) 443 else 80
        val hostHeader = if (port == defaultPort) host.toString() else "$host:$port"
        val request = buildString {
            append("GET / HTTP/1.1\r\n")
            append("Host: ").append(hostHeader).append("\r\n")
            append("User-Agent: AssetOnBoard/1.0 (Local network inventory)\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        return request.toByteArray(Charsets.US_ASCII)
    }

    /** Pulls status, headers and page title out of a raw HTTP response. */
    private fun applyHttpResponse(data: ByteArray, observation: PortObservation): PortObservation {
        val text = String(data, Charsets.ISO_8859_1)

        val separatorIndex = text.indexOf("\r\n\r\n").let {
            if (it >= 0) it to 4 else text.indexOf("\n\n").let { plain -> plain to 2 }
        }
        val headerBlock = if (separatorIndex.first >= 0) text.substring(0, separatorIndex.first) else text
        val body = if (separatorIndex.first >= 0) {
            text.substring(separatorIndex.first + separatorIndex.second)
        } else {
            ""
        }

        val lines = headerBlock.split(Regex("\r\n|\n|\r"))
        val statusLine = lines.firstOrNull() ?: return observation

        var result = observation
        if (statusLine.startsWith("HTTP/")) {
            val parts = statusLine.split(' ').filter { it.isNotEmpty() }
            if (parts.size >= 2) result = result.copy(httpStatus = parts[1].toIntOrNull())
        } else {
            // Not HTTP at all — keep it as a raw banner instead of discarding it.
            return result.copy(banner = cleanBanner(data))
        }

        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            if (value.isEmpty()) continue

            result = when (key) {
                "server" -> result.copy(httpServer = value)
                "location" -> result.copy(httpRedirect = value)
                "www-authenticate" -> {
                    // The Basic-auth realm is often the literal model name.
                    val marker = value.indexOf("realm=", ignoreCase = true)
                    val realm = if (marker >= 0) {
                        value.substring(marker + 6).trim('"', ' ')
                    } else {
                        value
                    }
                    result.copy(httpRealm = realm)
                }
                "x-powered-by" -> if (result.httpServer == null) result.copy(httpServer = value) else result
                else -> result
            }
        }

        extractTitle(body)?.let { result = result.copy(httpTitle = it) }
        return result
    }

    private fun extractTitle(body: String): String? {
        val open = body.indexOf("<title", ignoreCase = true)
        if (open < 0) return null
        val openEnd = body.indexOf('>', open)
        if (openEnd < 0) return null
        val close = body.indexOf("</title>", openEnd, ignoreCase = true)
        if (close < 0) return null

        val title = body.substring(openEnd + 1, close)
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .trim()
        val collapsed = title.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
        return if (collapsed.isEmpty()) null else collapsed.take(120)
    }

    /** Turns raw bytes into one printable line suitable for a UI row. */
    private fun cleanBanner(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val text = String(data, Charsets.ISO_8859_1)
        val printable = StringBuilder(text.length)
        for (character in text) {
            printable.append(
                when {
                    character == '\n' || character == '\r' || character == '\t' -> ' '
                    character.isLetter() || character.code in 32..126 -> character
                    else -> '.'
                },
            )
        }
        val collapsed = printable.toString()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .trim()
        if (collapsed.isEmpty() || collapsed.all { it == '.' }) return null
        return collapsed.take(200)
    }
}
