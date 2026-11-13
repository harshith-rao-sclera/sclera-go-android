package com.sclera.sclerago.services.scan.core

import com.sclera.sclerago.services.scan.model.DeviceCategory
import com.sclera.sclerago.services.scan.model.DiscoveryMethod
import com.sclera.sclerago.services.scan.model.NetworkDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFingerprinterTest {
    private val ip = IPv4.parse("10.0.0.5")!!

    private fun device(
        ports: List<Int> = emptyList(),
        services: List<String> = emptyList(),
        vendor: String? = null,
        text: String? = null,
        configure: (NetworkDevice) -> NetworkDevice = { it },
    ): NetworkDevice {
        val observations = ports.map { PortObservation(it, "svc", "detail", httpTitle = text) } +
            if (ports.isEmpty() && text != null) listOf(PortObservation(80, "http", "d", httpTitle = text)) else emptyList()
        val bonjour = services.map { BonjourService("inst", it, "local") }
        return configure(NetworkDevice(ip, vendor = vendor, openPorts = observations, bonjourServices = bonjour))
    }

    private fun category(d: NetworkDevice) = DeviceFingerprinter.classify(d).category

    @Test
    fun noEvidence_isUnknownWithZeroConfidence() {
        val r = DeviceFingerprinter.classify(NetworkDevice(ip))
        assertEquals(DeviceCategory.UNKNOWN, r.category)
        assertEquals(0.0, r.confidence, 0.0)
    }

    @Test
    fun portsVoteForCategories() {
        assertEquals(DeviceCategory.PHONE, category(device(ports = listOf(62078))))
        assertEquals(DeviceCategory.PRINTER, category(device(ports = listOf(9100))))
        assertEquals(DeviceCategory.CAMERA, category(device(ports = listOf(554))))
        assertEquals(DeviceCategory.SPEAKER, category(device(ports = listOf(1400))))
        assertEquals(DeviceCategory.TV, category(device(ports = listOf(8008))))
        assertEquals(DeviceCategory.NAS, category(device(ports = listOf(5000, 5001))))
        assertEquals(DeviceCategory.COMPUTER, category(device(ports = listOf(3389))))
        assertEquals(DeviceCategory.SERVER, category(device(ports = listOf(5432))))
        assertEquals(DeviceCategory.SMART_HOME, category(device(ports = listOf(1883))))
    }

    @Test
    fun serviceTypesVoteForCategories() {
        assertEquals(DeviceCategory.PRINTER, category(device(services = listOf("_ipp._tcp"))))
        assertEquals(DeviceCategory.TV, category(device(services = listOf("_googlecast._tcp"))))
        assertEquals(DeviceCategory.SPEAKER, category(device(services = listOf("_sonos._tcp"))))
        assertEquals(DeviceCategory.ROUTER, category(device(services = listOf("_airport._tcp"))))
        assertEquals(DeviceCategory.SMART_HOME, category(device(services = listOf("_hap._tcp"))))
        assertEquals(DeviceCategory.PHONE, category(device(services = listOf("_adb-tls-connect._tcp"))))
        assertEquals(DeviceCategory.NAS, category(device(services = listOf("_smb._tcp"))))
    }

    @Test
    fun homeKitCategoryNumberSelectsTheType() {
        fun hap(ci: String) = NetworkDevice(ip, bonjourServices = listOf(BonjourService("i", "_hap._tcp", "local", mapOf("ci" to ci))))
        listOf("17", "24", "27").forEach { assertTrue(DeviceFingerprinter.classify(hap(it)).confidence > 0.0) }
        assertEquals(DeviceCategory.SMART_HOME, category(hap("5")))
        assertEquals(DeviceCategory.SMART_HOME, category(hap("999")))
    }

    @Test
    fun vendorsVoteForCategories() {
        mapOf(
            "TP-Link Technologies" to DeviceCategory.ROUTER,
            "Ubiquiti Inc" to DeviceCategory.ACCESS_POINT,
            "Brother Industries" to DeviceCategory.PRINTER,
            "Synology Inc." to DeviceCategory.NAS,
            "Sonos, Inc." to DeviceCategory.SPEAKER,
            "Roku, Inc" to DeviceCategory.TV,
            "Nintendo Co." to DeviceCategory.GAME_CONSOLE,
            "Espressif Inc." to DeviceCategory.SMART_HOME,
            "Raspberry Pi Trading" to DeviceCategory.SERVER,
            "VMware, Inc." to DeviceCategory.VIRTUAL_MACHINE,
            "Samsung Electronics" to DeviceCategory.PHONE,
            "Dell Inc." to DeviceCategory.COMPUTER,
            "Apple, Inc." to DeviceCategory.COMPUTER,
        ).forEach { (vendor, expected) ->
            assertEquals(vendor, expected, category(device(vendor = vendor)))
        }
    }

    @Test
    fun freeTextVotes() {
        assertEquals(DeviceCategory.NAS, category(device(text = "Synology DiskStation")))
        assertEquals(DeviceCategory.ROUTER, category(device(text = "OpenWrt LuCI")))
        assertEquals(DeviceCategory.CAMERA, category(device(text = "Hikvision web")))
        assertEquals(DeviceCategory.PRINTER, category(device(text = "HP LaserJet 400")))
        assertEquals(DeviceCategory.GAME_CONSOLE, category(device(text = "PlayStation")))
    }

    @Test
    fun gatewayAndThisDevice() {
        assertEquals(DeviceCategory.ROUTER, category(NetworkDevice(ip, isGateway = true)))
        assertEquals(DeviceCategory.PHONE, category(NetworkDevice(ip, isThisDevice = true)))
    }

    @Test
    fun netbios_decidesBetweenComputerAndServer() {
        assertEquals(DeviceCategory.COMPUTER, category(NetworkDevice(ip, netbios = NetBiosInfo("PC", services = listOf("File Server")))))
        assertEquals(DeviceCategory.SERVER, category(NetworkDevice(ip, netbios = NetBiosInfo("DC", isDomainController = true))))
    }

    @Test
    fun advertisedModel_fillsInModelAndCategory() {
        val svc = BonjourService("Phone", "_device-info._tcp", "local", mapOf("model" to "iPhone15,2"))
        val r = DeviceFingerprinter.classify(NetworkDevice(ip, bonjourServices = listOf(svc)))
        assertNotNull(r.modelIdentifier)
        assertNotNull(r.modelName)
        assertTrue(r.confidence > 0.0)
        val unknown = BonjourService("X", "_device-info._tcp", "local", mapOf("model" to "AudioAccessory5,1"))
        assertEquals(DeviceCategory.SPEAKER, category(NetworkDevice(ip, bonjourServices = listOf(unknown))))
        val ipad = BonjourService("X", "_device-info._tcp", "local", mapOf("model" to "iPadZZ,9"))
        assertEquals(DeviceCategory.TABLET, category(NetworkDevice(ip, bonjourServices = listOf(ipad))))
    }

    @Test
    fun operatingSystem_isInferredFromBannersAndTtl() {
        fun os(banner: String? = null, ttl: Int? = null, text: String? = null, netbios: NetBiosInfo? = null): String? {
            val ports = if (banner != null) listOf(PortObservation(22, "ssh", "d", banner = banner)) else emptyList()
            val extra = if (text != null) listOf(PortObservation(80, "http", "d", httpTitle = text)) else emptyList()
            return DeviceFingerprinter.classify(NetworkDevice(ip, openPorts = ports + extra, ttl = ttl, netbios = netbios, isGateway = true)).operatingSystem
        }
        assertEquals("Linux (Ubuntu)", os(banner = "SSH-2.0-OpenSSH_8.9 Ubuntu"))
        assertEquals("Linux (Debian)", os(banner = "SSH-2.0-OpenSSH_9 Debian"))
        assertEquals("Raspberry Pi OS", os(banner = "SSH-2.0-OpenSSH_9 Raspbian"))
        assertEquals("FreeBSD", os(banner = "SSH-2.0-OpenSSH_9 FreeBSD"))
        assertEquals("OpenSSH 7.4", os(banner = "SSH-2.0-OpenSSH_7.4 x"))
        assertEquals("Embedded Linux (Dropbear)", os(banner = "SSH-2.0-dropbear_2020"))
        assertEquals("Windows / SMB", os(netbios = NetBiosInfo("PC")))
        assertEquals("macOS", os(text = "Darwin"))
        assertEquals("Android", os(text = "Android"))
        assertEquals("OpenWrt", os(text = "openwrt"))
        assertEquals("Network appliance (BSD-family stack)", os(ttl = 255))
        assertEquals("Windows-family stack", os(ttl = 128))
        assertEquals("Unix-family stack", os(ttl = 64))
        assertNull(os(ttl = 10))
        assertNull(os())
    }

    // ---- NetworkDevice

    @Test
    fun displayName_fallsBackThroughNamingSources() {
        assertEquals("This device", NetworkDevice(ip, isThisDevice = true).displayName)
        assertEquals("host", NetworkDevice(ip, hostnames = listOf("host.local")).displayName)
        assertEquals("PC", NetworkDevice(ip, netbios = NetBiosInfo("PC")).displayName)
        assertEquals("Model X", NetworkDevice(ip, modelName = "Model X").displayName)
        assertEquals("Router", NetworkDevice(ip, isGateway = true).displayName)
        assertEquals("Acme device", NetworkDevice(ip, vendor = "Acme").displayName)
        assertEquals("10.0.0.5", NetworkDevice(ip).displayName)
        assertTrue(NetworkDevice(ip).isUnnamed)
        val raop = BonjourService("AABBCCDDEEFF@Kitchen", "_raop._tcp", "local")
        assertEquals("Kitchen", NetworkDevice(ip, bonjourServices = listOf(raop)).displayName)
        val avahi = BonjourService("box [aa:bb:cc:dd:ee:ff]", "_workstation._tcp", "local")
        assertEquals("box", NetworkDevice(ip, bonjourServices = listOf(avahi)).displayName)
    }

    @Test
    fun subtitle_describesWhatIsKnown() {
        assertEquals("Printer · Brother", NetworkDevice(ip, category = DeviceCategory.PRINTER, vendor = "Brother").subtitle)
        assertEquals("Model Z · Linux", NetworkDevice(ip, hostnames = listOf("box.local"), modelName = "Model Z", operatingSystem = "Linux").subtitle)
        assertEquals("2 open ports", NetworkDevice(ip, openPorts = listOf(PortObservation(1, "a", "d"), PortObservation(2, "b", "d"))).subtitle)
        assertEquals("1 open port", NetworkDevice(ip, openPorts = listOf(PortObservation(1, "a", "d"))).subtitle)
        assertEquals("Responded to ping", NetworkDevice(ip, discoveryMethods = setOf(DiscoveryMethod.ICMP)).subtitle)
        assertEquals("Responded to discovery", NetworkDevice(ip).subtitle)
        assertFalse(NetworkDevice(ip).isResponsive)
        assertTrue(NetworkDevice(ip, discoveryMethods = setOf(DiscoveryMethod.TCP)).isResponsive)
    }

    @Test
    fun mergedWith_combinesSightings() {
        val a = NetworkDevice(ip, hostnames = listOf("a"), openPorts = listOf(PortObservation(80, "http", "d")), discoveryMethods = setOf(DiscoveryMethod.ICMP), lastSeen = 1)
        val b = NetworkDevice(
            ip, vendor = "V", hostnames = listOf("a", "b"), ttl = 64, isGateway = true,
            openPorts = listOf(PortObservation(22, "ssh", "d"), PortObservation(80, "http", "new")),
            discoveryMethods = setOf(DiscoveryMethod.TCP), lastSeen = 9,
        )
        val m = a.mergedWith(b)
        assertEquals(listOf("a", "b"), m.hostnames)
        assertEquals(listOf(22, 80), m.openPorts.map { it.port })
        assertEquals("new", m.openPorts.last().detail)
        assertEquals("V", m.vendor)
        assertEquals(64, m.ttl)
        assertTrue(m.isGateway)
        assertEquals(setOf(DiscoveryMethod.ICMP, DiscoveryMethod.TCP), m.discoveryMethods)
        assertEquals(9L, m.lastSeen)
        val s1 = BonjourService("i", "_t._tcp", "local")
        val s2 = s1.copy(port = 5)
        assertEquals(5, NetworkDevice(ip, bonjourServices = listOf(s1)).mergedWith(NetworkDevice(ip, bonjourServices = listOf(s2))).bonjourServices.single().port)
    }

    @Test
    fun matches_searchesEveryIdentifyingString() {
        val d = NetworkDevice(ip, vendor = "Acme", hostnames = listOf("printer-1.local"), macAddress = MACAddress.parse("aa:bb:cc:00:11:22"), openPorts = listOf(PortObservation(631, "ipp", "d")))
        listOf("", "acme", "PRINTER-1", "aa:bb:cc", "631", "ipp", "10.0.0.5").forEach { assertTrue(it, d.matches(it)) }
        assertFalse(d.matches("zzz"))
    }

    @Test
    fun deviceCategory_resolvesLabels() {
        assertEquals(DeviceCategory.NAS, DeviceCategory.fromLabel("Network Storage"))
        assertNull(DeviceCategory.fromLabel("nope"))
    }

    @Test
    fun portSummary_prefersTheMostDescriptiveLine() {
        assertEquals("Title", PortObservation(80, "h", "d", httpTitle = "Title", banner = "b").summary)
        assertEquals("Auth realm: R", PortObservation(80, "h", "d", httpRealm = "R", httpServer = "S").summary)
        assertEquals("S", PortObservation(80, "h", "d", httpServer = "S", banner = "b").summary)
        assertEquals("b", PortObservation(80, "h", "d", banner = "b").summary)
        assertEquals("d", PortObservation(80, "h", "d").summary)
    }
}
