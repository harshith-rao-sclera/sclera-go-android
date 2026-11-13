package com.sclera.sclerago.services.scan.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress

/** MASVS-PLATFORM: scan input (addresses typed by hand, DNS packets off the wire) is parsed defensively. */
class NetworkTypesTest {
    private fun ip(s: String) = IPv4.parse(s)!!

    // ---- IPv4

    @Test
    fun ipv4_parsesAndPrints() {
        assertEquals("192.168.1.20", ip(" 192.168.1.20 ").toString())
        listOf("1.2.3", "1.2.3.4.5", "256.1.1.1", "a.b.c.d", "", "1.2.3.-1").forEach { assertNull(it, IPv4.parse(it)) }
        assertEquals(listOf(10, 0, 0, 1), ip("10.0.0.1").octets.toList())
        assertArrayEquals(byteArrayOf(10, 0, 0, 1), ip("10.0.0.1").bytes)
    }

    @Test
    fun ipv4_comparesAsUnsigned() {
        assertTrue(ip("200.0.0.1") > ip("10.0.0.1"))
        assertTrue(ip("10.0.0.1") < ip("10.0.0.2"))
    }

    @Test
    fun ipv4_classifiesRanges() {
        listOf("10.1.1.1", "172.16.0.1", "172.31.9.9", "192.168.0.5", "169.254.1.1").forEach { assertTrue(it, ip(it).isPrivate) }
        listOf("8.8.8.8", "172.32.0.1", "192.169.0.1").forEach { assertFalse(it, ip(it).isPrivate) }
        assertTrue(ip("127.0.0.1").isLoopback)
        assertFalse(ip("10.0.0.1").isLoopback)
    }

    @Test
    fun ipv4_convertsToAndFromInetAddress() {
        val a = ip("10.20.30.40")
        assertEquals(a, IPv4.from(a.toInetAddress()))
        assertNull(IPv4.from(InetAddress.getByName("::1")))
    }

    // ---- Subnets

    @Test
    fun subnet_parsesPrefixMaskAndBareAddress() {
        assertEquals("10.0.5.0/24", IPv4Subnet.parse("10.0.5.42/24").toString())
        assertEquals("10.0.5.0/24", IPv4Subnet.parse("10.0.5.0/255.255.255.0").toString())
        assertEquals("10.0.5.0/24", IPv4Subnet.parse("10.0.5.9").toString())
        assertEquals("10.0.0.0/8", IPv4Subnet.parse("10.1.2.3/8").toString())
    }

    @Test
    fun subnet_rejectsBadInput() {
        listOf("", " ", "x/24", "10.0.0.0/", "10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/255.0.255.0", "10.0.0.0/zz", "10.0.0/24")
            .forEach { assertNull(it, IPv4Subnet.parse(it)) }
    }

    @Test
    fun subnet_hostMathMatchesThePrefix() {
        val s = IPv4Subnet.parse("192.168.1.0/24")!!
        assertEquals(254, s.hostCount)
        assertEquals(ip("192.168.1.255"), s.broadcast)
        assertEquals(ip("255.255.255.0"), s.netmask)
        assertEquals(ip("192.168.1.1"), s.likelyGateway)
        assertEquals(254, s.hostAddresses.size)
        assertEquals(ip("192.168.1.1"), s.hostAddresses.first())
        assertEquals(ip("192.168.1.254"), s.hostAddresses.last())
        assertTrue(ip("192.168.1.77") in s)
        assertFalse(ip("192.168.2.77") in s)
    }

    @Test
    fun subnet_tinyPrefixesHaveSpecialCases() {
        val p31 = IPv4Subnet.parse("10.0.0.0/31")!!
        assertEquals(2, p31.hostCount)
        assertEquals(listOf(ip("10.0.0.0"), ip("10.0.0.1")), p31.hostAddresses)
        assertNull(p31.likelyGateway)
        val p32 = IPv4Subnet.parse("10.0.0.7/32")!!
        assertEquals(1, p32.hostCount)
        assertEquals(listOf(ip("10.0.0.7")), p32.hostAddresses)
        assertEquals(0, IPv4Subnet.mask(0))
        assertEquals(Int.MAX_VALUE, IPv4Subnet.of(ip("0.0.0.0"), 0).hostCount)
    }

    @Test
    fun subnet_fromAddressAndNetmask() {
        assertEquals("10.1.2.0/24", IPv4Subnet.of(ip("10.1.2.99"), ip("255.255.255.0")).toString())
        assertEquals(32, IPv4Subnet.of(ip("10.1.2.3"), 99).prefixLength)
    }

    @Test
    fun subnet_clampedNarrowsOversizedNetworks() {
        val big = IPv4Subnet.parse("10.0.0.0/16")!!
        val small = big.clamped(ip("10.0.7.9"), 254)
        assertEquals(24, small.prefixLength)
        assertTrue(ip("10.0.7.9") in small)
        assertEquals(big, big.clamped(ip("10.0.7.9"), 100_000))
        assertEquals(32, big.clamped(ip("10.0.7.9"), 0).prefixLength)
    }

    // ---- MAC

    @Test
    fun mac_parsesCommonFormats() {
        val a = MACAddress.parse("aa:bb:cc:00:11:22")!!
        assertEquals("AA:BB:CC:00:11:22", a.toString())
        assertEquals(a, MACAddress.parse("AA-BB-CC-00-11-22"))
        assertEquals(a.hashCode(), MACAddress.parse("AA-BB-CC-00-11-22")!!.hashCode())
        assertEquals(0xAABBCC, a.oui)
        listOf("", "aa:bb:cc", "zz:bb:cc:00:11:22", "aa:bb:cc:00:11:22:33").forEach { assertNull(it, MACAddress.parse(it)) }
        assertNull(MACAddress.of(ByteArray(5)))
        assertNotNull(MACAddress.of(ByteArray(6)))
    }

    @Test
    fun mac_flags() {
        assertTrue(MACAddress.parse("02:00:00:00:00:01")!!.isLocallyAdministered)
        assertFalse(MACAddress.parse("00:00:00:00:00:01")!!.isLocallyAdministered)
        assertTrue(MACAddress.parse("01:00:5e:00:00:fb")!!.isMulticast)
        assertTrue(MACAddress.of(ByteArray(6))!!.isAllZero)
        assertFalse(MACAddress.parse("00:00:00:00:00:01")!!.isAllZero)
        assertFalse(MACAddress.parse("00:00:00:00:00:01")!!.equals("x"))
    }

    // ---- DNS

    private fun name(vararg labels: String): ByteArray {
        val out = ByteArrayOutputStream()
        labels.forEach { out.write(it.length); out.write(it.toByteArray()) }
        out.write(0)
        return out.toByteArray()
    }

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun record(name: ByteArray, type: Int, data: ByteArray) = name + u16(type) + u16(1) + byteArrayOf(0, 0, 0, 120) + u16(data.size) + data

    private fun response(vararg records: ByteArray, questions: Int = 0): ByteArray =
        u16(0) + u16(0x8400) + u16(questions) + u16(records.size) + u16(0) + u16(0) + records.fold(ByteArray(0)) { a, b -> a + b }

    @Test
    fun dnsQuery_encodesQuestions() {
        val q = DnsMessage.query(listOf("_http._tcp.local" to 12))
        assertEquals(1, q[5].toInt())
        assertTrue(q.size > 12)
        val long = DnsMessage.query(listOf("a".repeat(80) + ".local" to 1))
        assertEquals(63, long[12].toInt())
        assertTrue(DnsMessage.query(emptyList()).size == 12)
    }

    @Test
    fun dnsParse_readsPtrSrvTxtAndA() {
        val owner = name("Printer", "_ipp", "_tcp", "local")
        val data = response(
            record(name("_ipp", "_tcp", "local"), 12, owner),
            record(owner, 33, u16(0) + u16(0) + u16(631) + name("host", "local")),
            record(owner, 16, byteArrayOf(7) + "rp=ipp/".toByteArray() + byteArrayOf(5) + "flag1".toByteArray() + byteArrayOf(0)),
            record(name("host", "local"), 1, byteArrayOf(10, 0, 0, 9)),
        )
        val recs = DnsMessage.parse(data, data.size)
        assertEquals(4, recs.size)
        assertEquals(listOf("Printer", "_ipp", "_tcp", "local"), (recs[0] as DnsMessage.Record.Ptr).target)
        val srv = recs[1] as DnsMessage.Record.Srv
        assertEquals(631, srv.port)
        assertEquals(listOf("host", "local"), srv.target)
        assertEquals(mapOf("rp" to "ipp/", "flag1" to ""), (recs[2] as DnsMessage.Record.Txt).entries)
        assertEquals(ip("10.0.0.9"), (recs[3] as DnsMessage.Record.A).address)
    }

    @Test
    fun dnsParse_skipsQuestionsAndFollowsCompressionPointers() {
        val question = name("x", "local") + u16(12) + u16(1)
        val first = name("a", "local")
        val head = u16(0) + u16(0x8400) + u16(1) + u16(2) + u16(0) + u16(0) + question
        val firstRecordOffset = head.size
        val rec1 = record(first, 1, byteArrayOf(1, 2, 3, 4))
        // second record's owner name is a pointer to the first record's name
        val rec2 = record(byteArrayOf(0xC0.toByte(), firstRecordOffset.toByte()), 1, byteArrayOf(5, 6, 7, 8))
        val data = head + rec1 + rec2
        val recs = DnsMessage.parse(data, data.size)
        assertEquals(2, recs.size)
        assertEquals(listOf("a", "local"), recs[1].name)
    }

    @Test
    fun dnsParse_survivesMalformedPackets() {
        assertTrue(DnsMessage.parse(ByteArray(5), 5).isEmpty())
        val ok = response(record(name("a"), 1, byteArrayOf(1, 2, 3, 4)))
        for (cut in 12 until ok.size) DnsMessage.parse(ok.copyOf(cut), cut) // never throws
        // a pointer that points at itself is rejected
        val loop = response(record(byteArrayOf(0xC0.toByte(), 12), 1, byteArrayOf(1, 2, 3, 4)))
        assertTrue(DnsMessage.parse(loop, loop.size).isEmpty())
        // a reserved label type stops parsing
        val reserved = response(record(byteArrayOf(0x80.toByte(), 0), 1, byteArrayOf(1, 2, 3, 4)))
        assertTrue(DnsMessage.parse(reserved, reserved.size).isEmpty())
        // an A record with the wrong length is ignored; a short SRV too
        val odd = response(record(name("a"), 1, byteArrayOf(1, 2, 3)), record(name("b"), 33, byteArrayOf(1, 2)))
        assertTrue(DnsMessage.parse(odd, odd.size).isEmpty())
        // a question count with a truncated question
        val badQuestion = u16(0) + u16(0) + u16(3) + u16(0) + u16(0) + u16(0) + byteArrayOf(1, 'a'.code.toByte())
        assertTrue(DnsMessage.parse(badQuestion, badQuestion.size).isEmpty())
    }
}
