//
//  DeviceFingerprinter.kt
//  AssetOnBoard
//
//  Turns raw observations into a device type, model and OS guess.
//
//  No single signal is trustworthy on its own, so each one votes with a weight
//  and the highest-scoring category wins. Self-reported model strings from mDNS
//  are near-certain; an open port is only a hint.
//

package com.example.sclerago.services.scan.core

import com.example.sclerago.services.scan.data.AppleModelCatalog
import com.example.sclerago.services.scan.model.DeviceCategory
import com.example.sclerago.services.scan.model.NetworkDevice

object DeviceFingerprinter {

    /** Returns [device] with `category`, `modelName`, `operatingSystem` and `confidence` filled in. */
    fun classify(device: NetworkDevice): NetworkDevice {
        val scores = HashMap<DeviceCategory, Double>()
        fun vote(category: DeviceCategory, weight: Double) {
            scores[category] = (scores[category] ?: 0.0) + weight
        }

        val ports = device.openPorts.map { it.port }.toSet()
        val serviceTypes = device.bonjourServices.map { it.serviceType }.toSet()
        val vendor = device.vendor?.lowercase().orEmpty()
        val corpus = buildCorpus(device).lowercase()

        var modelIdentifier = device.modelIdentifier
        var modelName = device.modelName
        var operatingSystem = device.operatingSystem

        // 1. Self-reported hardware model — the strongest signal there is.
        device.bonjourServices.firstNotNullOfOrNull { it.advertisedModel }?.let { identifier ->
            modelIdentifier = identifier
            val resolved = AppleModelCatalog.match(identifier)
            if (resolved != null) {
                modelName = resolved.name
                vote(resolved.category, 10.0)
                operatingSystem = resolved.operatingSystem
            } else {
                modelName = identifier
                vote(categoryFromModelString(identifier) ?: DeviceCategory.UNKNOWN, 4.0)
            }
        }

        // 2. Advertised services.
        for (type in serviceTypes) {
            when (type) {
                "_airplay._tcp", "_mediaremotetv._tcp", "_androidtvremote2._tcp",
                "_androidtvremote._tcp", "_googlecast._tcp", "_amzn-wplay._tcp",
                "_roku._tcp", "_rsp._tcp", "_viziocast._tcp",
                -> vote(DeviceCategory.TV, 5.0)

                "_raop._tcp", "_sonos._tcp", "_spotify-connect._tcp",
                -> vote(DeviceCategory.SPEAKER, 5.0)

                "_ipp._tcp", "_ipps._tcp", "_printer._tcp", "_pdl-datastream._tcp",
                "_scanner._tcp", "_uscan._tcp", "_uscans._tcp", "_ipp-tls._tcp",
                -> vote(DeviceCategory.PRINTER, 8.0)

                "_adisk._tcp", "_afpovertcp._tcp", "_smb._tcp", "_nfs._tcp",
                -> vote(DeviceCategory.NAS, 3.0)

                "_hap._tcp", "_homekit._tcp", "_hue._tcp", "_esphomelib._tcp",
                "_matter._tcp", "_matterc._udp", "_miio._udp", "_ewelink._tcp",
                -> vote(DeviceCategory.SMART_HOME, 6.0)

                "_apple-mobdev2._tcp" -> vote(DeviceCategory.PHONE, 4.0)

                // Wireless debugging is only ever exposed by an Android device.
                "_adb-tls-connect._tcp" -> vote(DeviceCategory.PHONE, 5.0)

                "_workstation._tcp", "_rfb._tcp", "_sftp-ssh._tcp",
                -> vote(DeviceCategory.COMPUTER, 4.0)

                "_companion-link._tcp", "_rdlink._tcp",
                -> vote(DeviceCategory.COMPUTER, 1.5)

                "_airport._tcp" -> vote(DeviceCategory.ROUTER, 8.0)

                "_plexmediasvr._tcp", "_nvstream._tcp",
                -> vote(DeviceCategory.SERVER, 3.0)
            }
        }

        // HomeKit accessories publish a numeric category in their TXT record.
        device.bonjourServices.firstOrNull { it.serviceType == "_hap._tcp" }
            ?.txt("ci")
            ?.toIntOrNull()
            ?.let { vote(homeKitCategory(it), 6.0) }

        // 3. NetBIOS. A node-status reply is near-conclusive evidence of a
        //    Windows or Samba host, and it often arrives from machines whose
        //    firewall drops every TCP probe, leaving no other signal at all.
        device.netbios?.let { netbios ->
            vote(
                if (netbios.isDomainController) DeviceCategory.SERVER else DeviceCategory.COMPUTER,
                6.0,
            )
            if ("File Server" in netbios.services) vote(DeviceCategory.COMPUTER, 1.0)
        }

        // 4. Open ports.
        if (62078 in ports) vote(DeviceCategory.PHONE, 7.0)
        // ADB over TCP is an Android box: a handset with debugging on, a TV stick
        // or an emulator. Weighted higher than the iOS build's guess because on
        // Android's own network this is a very common and very specific sighting.
        if (5555 in ports) vote(DeviceCategory.PHONE, 3.0)
        if (9100 in ports || 515 in ports || 631 in ports) vote(DeviceCategory.PRINTER, 5.0)
        if (445 in ports || 139 in ports) vote(DeviceCategory.COMPUTER, 2.0)
        if (3389 in ports) vote(DeviceCategory.COMPUTER, 3.0)
        if (548 in ports || 5900 in ports) vote(DeviceCategory.COMPUTER, 2.0)
        if (5000 in ports && 5001 in ports) vote(DeviceCategory.NAS, 4.0)
        if (32400 in ports || 8096 in ports) vote(DeviceCategory.SERVER, 3.0)
        if (554 in ports) vote(DeviceCategory.CAMERA, 4.0)
        if (8008 in ports || 8009 in ports || 8060 in ports) vote(DeviceCategory.TV, 4.0)
        if (1400 in ports) vote(DeviceCategory.SPEAKER, 6.0)
        if (1883 in ports || 8883 in ports) vote(DeviceCategory.SMART_HOME, 2.0)
        if (3306 in ports || 5432 in ports || 27017 in ports ||
            6379 in ports || 9200 in ports
        ) {
            vote(DeviceCategory.SERVER, 3.0)
        }
        if (53 in ports && 80 in ports) vote(DeviceCategory.ROUTER, 2.0)
        if (22 in ports && 80 in ports) vote(DeviceCategory.SERVER, 1.0)

        // 5. Vendor.
        when {
            vendor.contains("apple") -> vote(DeviceCategory.COMPUTER, 1.0)

            vendor.contains("netgear") || vendor.contains("tp-link") ||
                vendor.contains("d-link") || vendor.contains("zyxel") ||
                vendor.contains("arcadyan") || vendor.contains("sagemcom") ||
                vendor.contains("technicolor") || vendor.contains("arris") ||
                vendor.contains("mikrotik") || vendor.contains("linksys") ||
                vendor.contains("edimax") -> vote(DeviceCategory.ROUTER, 5.0)

            vendor.contains("ubiquiti") || vendor.contains("aruba") ||
                vendor.contains("meraki") -> vote(DeviceCategory.ACCESS_POINT, 5.0)

            vendor.contains("brother") || vendor.contains("epson") ||
                vendor.contains("canon") || vendor.contains("kyocera") ||
                vendor.contains("ricoh") || vendor.contains("xerox") ||
                vendor.contains("konica") || vendor.contains("sharp") ->
                vote(DeviceCategory.PRINTER, 5.0)

            vendor.contains("synology") || vendor.contains("qnap") ||
                vendor.contains("western digital") -> vote(DeviceCategory.NAS, 6.0)

            vendor.contains("sonos") -> vote(DeviceCategory.SPEAKER, 7.0)
            vendor.contains("roku") || vendor.contains("vizio") -> vote(DeviceCategory.TV, 7.0)

            vendor.contains("nintendo") || vendor.contains("sony interactive") ||
                vendor.contains("xbox") -> vote(DeviceCategory.GAME_CONSOLE, 8.0)

            vendor.contains("espressif") || vendor.contains("tuya") ||
                vendor.contains("signify") || vendor.contains("nest") ||
                vendor.contains("ring") || vendor.contains("netatmo") ->
                vote(DeviceCategory.SMART_HOME, 6.0)

            vendor.contains("raspberry") -> vote(DeviceCategory.SERVER, 4.0)

            vendor.contains("vmware") || vendor.contains("virtualbox") ||
                vendor.contains("qemu") || vendor.contains("parallels") ||
                vendor.contains("hyper-v") -> vote(DeviceCategory.VIRTUAL_MACHINE, 9.0)

            vendor.contains("samsung") || vendor.contains("xiaomi") ||
                vendor.contains("huawei") || vendor.contains("lg electronics") ->
                vote(DeviceCategory.PHONE, 1.0)

            vendor.contains("intel") || vendor.contains("dell") ||
                vendor.contains("lenovo") || vendor.contains("hewlett") ||
                vendor.contains("asustek") || vendor.contains("giga-byte") ->
                vote(DeviceCategory.COMPUTER, 3.0)

            vendor.contains("amazon") -> {
                vote(DeviceCategory.TV, 2.0)
                vote(DeviceCategory.SPEAKER, 2.0)
            }
        }

        // 6. Free text from HTTP titles, certificates and banners.
        for ((needle, category, weight) in TEXT_SIGNALS) {
            if (corpus.contains(needle)) vote(category, weight)
        }

        // 7. Gateways are routers unless something much stronger disagrees.
        if (device.isGateway) vote(DeviceCategory.ROUTER, 6.0)
        if (device.isThisDevice) vote(DeviceCategory.PHONE, 8.0)

        // 8. Operating system, mostly from banners and TTL.
        val resolvedOs = operatingSystem ?: inferOperatingSystem(device, corpus)

        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()

        return if (best != null && best.value >= 1.0) {
            val runnerUp = ranked.drop(1).firstOrNull()?.value ?: 0.0
            // Confidence reflects both the absolute score and how clearly it beat
            // the alternatives, so a lone weak hint never reads as certainty.
            val margin = (best.value - runnerUp) / maxOf(best.value, 1.0)
            device.copy(
                category = best.key,
                modelIdentifier = modelIdentifier,
                modelName = modelName,
                operatingSystem = resolvedOs,
                confidence = minOf(1.0, (best.value / 12.0) * 0.7 + margin * 0.3),
            )
        } else {
            device.copy(
                category = DeviceCategory.UNKNOWN,
                modelIdentifier = modelIdentifier,
                modelName = modelName,
                operatingSystem = resolvedOs,
                confidence = 0.0,
            )
        }
    }

    // MARK: - Signals

    private data class TextSignal(
        val needle: String,
        val category: DeviceCategory,
        val weight: Double,
    )

    private val TEXT_SIGNALS: List<TextSignal> = listOf(
        TextSignal("synology", DeviceCategory.NAS, 7.0),
        TextSignal("diskstation", DeviceCategory.NAS, 7.0),
        TextSignal("qnap", DeviceCategory.NAS, 7.0),
        TextSignal("truenas", DeviceCategory.NAS, 7.0),
        TextSignal("freenas", DeviceCategory.NAS, 7.0),
        TextSignal("unraid", DeviceCategory.NAS, 6.0),
        TextSignal("openmediavault", DeviceCategory.NAS, 6.0),
        TextSignal("openwrt", DeviceCategory.ROUTER, 7.0),
        TextSignal("dd-wrt", DeviceCategory.ROUTER, 7.0),
        TextSignal("pfsense", DeviceCategory.ROUTER, 7.0),
        TextSignal("opnsense", DeviceCategory.ROUTER, 7.0),
        TextSignal("mikrotik", DeviceCategory.ROUTER, 7.0),
        TextSignal("routeros", DeviceCategory.ROUTER, 7.0),
        TextSignal("fritz!box", DeviceCategory.ROUTER, 7.0),
        TextSignal("asuswrt", DeviceCategory.ROUTER, 7.0),
        TextSignal("tomato", DeviceCategory.ROUTER, 5.0),
        TextSignal("unifi", DeviceCategory.ACCESS_POINT, 6.0),
        TextSignal("ubnt", DeviceCategory.ACCESS_POINT, 6.0),
        TextSignal("aruba", DeviceCategory.ACCESS_POINT, 5.0),
        TextSignal("hp laserjet", DeviceCategory.PRINTER, 8.0),
        TextSignal("officejet", DeviceCategory.PRINTER, 8.0),
        TextSignal("deskjet", DeviceCategory.PRINTER, 8.0),
        TextSignal("brother", DeviceCategory.PRINTER, 6.0),
        TextSignal("epson", DeviceCategory.PRINTER, 6.0),
        TextSignal("cups", DeviceCategory.PRINTER, 5.0),
        TextSignal("printer", DeviceCategory.PRINTER, 4.0),
        TextSignal("ipp everywhere", DeviceCategory.PRINTER, 5.0),
        TextSignal("hikvision", DeviceCategory.CAMERA, 8.0),
        TextSignal("dahua", DeviceCategory.CAMERA, 8.0),
        TextSignal("axis", DeviceCategory.CAMERA, 5.0),
        TextSignal("reolink", DeviceCategory.CAMERA, 8.0),
        TextSignal("onvif", DeviceCategory.CAMERA, 7.0),
        TextSignal("ip camera", DeviceCategory.CAMERA, 7.0),
        TextSignal("nvr", DeviceCategory.CAMERA, 5.0),
        TextSignal("webcam", DeviceCategory.CAMERA, 4.0),
        TextSignal("home assistant", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("homebridge", DeviceCategory.SMART_HOME, 7.0),
        TextSignal("tasmota", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("esphome", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("shelly", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("philips hue", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("hue bridge", DeviceCategory.SMART_HOME, 8.0),
        TextSignal("plex", DeviceCategory.SERVER, 6.0),
        TextSignal("jellyfin", DeviceCategory.SERVER, 6.0),
        TextSignal("emby", DeviceCategory.SERVER, 6.0),
        TextSignal("portainer", DeviceCategory.SERVER, 5.0),
        TextSignal("grafana", DeviceCategory.SERVER, 5.0),
        TextSignal("proxmox", DeviceCategory.SERVER, 7.0),
        TextSignal("nextcloud", DeviceCategory.SERVER, 5.0),
        TextSignal("gitlab", DeviceCategory.SERVER, 5.0),
        TextSignal("jenkins", DeviceCategory.SERVER, 5.0),
        TextSignal("apache", DeviceCategory.SERVER, 1.0),
        TextSignal("nginx", DeviceCategory.SERVER, 1.0),
        TextSignal("openresty", DeviceCategory.SERVER, 1.0),
        TextSignal("chromecast", DeviceCategory.TV, 8.0),
        TextSignal("android tv", DeviceCategory.TV, 8.0),
        TextSignal("google tv", DeviceCategory.TV, 8.0),
        TextSignal("roku", DeviceCategory.TV, 8.0),
        TextSignal("fire tv", DeviceCategory.TV, 8.0),
        TextSignal("apple tv", DeviceCategory.TV, 8.0),
        TextSignal("smartcast", DeviceCategory.TV, 7.0),
        TextSignal("webos", DeviceCategory.TV, 8.0),
        TextSignal("tizen", DeviceCategory.TV, 7.0),
        TextSignal("bravia", DeviceCategory.TV, 7.0),
        TextSignal("sonos", DeviceCategory.SPEAKER, 8.0),
        TextSignal("homepod", DeviceCategory.SPEAKER, 8.0),
        TextSignal("bose", DeviceCategory.SPEAKER, 6.0),
        TextSignal("playstation", DeviceCategory.GAME_CONSOLE, 9.0),
        TextSignal("xbox", DeviceCategory.GAME_CONSOLE, 9.0),
        TextSignal("nintendo", DeviceCategory.GAME_CONSOLE, 9.0),
        TextSignal("raspberry pi", DeviceCategory.SERVER, 5.0),
        TextSignal("windows", DeviceCategory.COMPUTER, 3.0),
        TextSignal("macbook", DeviceCategory.COMPUTER, 6.0),
        TextSignal("imac", DeviceCategory.COMPUTER, 6.0),
    )

    private fun inferOperatingSystem(device: NetworkDevice, corpus: String): String? {
        val ssh = device.openPorts.firstOrNull { it.port == 22 || it.port == 2222 }?.banner
        if (ssh != null) {
            if (ssh.contains("OpenSSH")) {
                if (ssh.contains("Ubuntu")) return "Linux (Ubuntu)"
                if (ssh.contains("Debian")) return "Linux (Debian)"
                if (ssh.contains("Raspbian")) return "Raspberry Pi OS"
                if (ssh.contains("FreeBSD")) return "FreeBSD"
                val marker = ssh.indexOf("OpenSSH_")
                if (marker >= 0) {
                    val tail = ssh.substring(marker + 8).takeWhile { !it.isWhitespace() }
                    return "OpenSSH $tail"
                }
            }
            if (ssh.contains("dropbear")) return "Embedded Linux (Dropbear)"
        }
        if (device.netbios != null) return "Windows / SMB"
        if (corpus.contains("darwin") || corpus.contains("macos")) return "macOS"
        if (corpus.contains("android")) return "Android"
        if (corpus.contains("ubuntu")) return "Linux (Ubuntu)"
        if (corpus.contains("debian")) return "Linux (Debian)"
        if (corpus.contains("openwrt")) return "OpenWrt"

        // TTL is a coarse but genuine fingerprint: stacks start at 64, 128 or 255
        // and every hop decrements it. On a flat LAN there are no hops at all.
        // Linux ping sockets usually strip the reply header, so this only fires
        // on the kernels that hand it back.
        val ttl = device.ttl ?: return null
        return when {
            ttl > 200 -> "Network appliance (BSD-family stack)"
            ttl > 100 -> "Windows-family stack"
            ttl > 32 -> "Unix-family stack"
            else -> null
        }
    }

    private fun buildCorpus(device: NetworkDevice): String {
        val parts = mutableListOf<String>()
        parts += device.hostnames
        parts += device.bonjourServices.map { it.instanceName }
        parts += device.bonjourServices.flatMap { it.txtRecord.values }
        for (port in device.openPorts) {
            parts += listOfNotNull(port.httpTitle, port.httpServer, port.httpRealm, port.banner)
            parts += port.certificates.mapNotNull { it.displayName }
            parts += port.certificates.mapNotNull { it.issuerOrganization }
        }
        device.netbios?.let {
            parts += listOfNotNull(it.computerName, it.workgroup)
        }
        return parts.joinToString(" ")
    }

    private fun categoryFromModelString(model: String): DeviceCategory? {
        val lowered = model.lowercase()
        if (lowered.startsWith("iphone")) return DeviceCategory.PHONE
        if (lowered.startsWith("ipad")) return DeviceCategory.TABLET
        if (lowered.startsWith("watch")) return DeviceCategory.WEARABLE
        if (lowered.startsWith("appletv")) return DeviceCategory.TV
        if (lowered.startsWith("audioaccessory")) return DeviceCategory.SPEAKER
        if (lowered.startsWith("airport")) return DeviceCategory.ROUTER
        if (lowered.contains("mac") || lowered.contains("book")) return DeviceCategory.COMPUTER
        return null
    }

    /** HomeKit accessory category identifiers from the HAP specification. */
    private fun homeKitCategory(identifier: Int): DeviceCategory = when (identifier) {
        2, 3, 5, 7, 8, 9, 10, 12, 13, 14, 15, 16, 21, 22, 23, 26, 28, 29, 30, 31, 32, 33, 34 ->
            DeviceCategory.SMART_HOME
        17, 18, 19, 20 -> DeviceCategory.CAMERA
        24, 25 -> DeviceCategory.TV
        27 -> DeviceCategory.SPEAKER
        else -> DeviceCategory.SMART_HOME
    }
}
