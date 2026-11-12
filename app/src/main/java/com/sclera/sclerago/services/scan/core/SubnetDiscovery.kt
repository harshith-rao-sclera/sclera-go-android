//
//  SubnetDiscovery.kt
//  AssetOnBoard
//
//  Finds the subnets that exist on a network, as opposed to the hosts on one.
//
//  A host on one segment cannot see another segment by sweeping it: it has to be
//  told, or it has to guess. So this asks in that order. The router's SNMP tables
//  are the authority — they carry the real masks and reach segments nothing local
//  has ever talked to — and the gateway sweep is the fallback for when SNMP is
//  off, which is most consumer gear.
//
//  Between the two sits the cheapest source of all: our own lease. The resolvers
//  and DHCP server a corporate network hands out almost always live in a server
//  segment rather than on the user's, so they name a real subnet for nothing, and
//  they keep naming it where inter-VLAN filtering means no probe would ever have
//  been answered.
//
//  The sweep leans on the convention that a router sits at a memorable host
//  number, which turns "is this /24 live?" into a few probes instead of 254 and
//  is what makes covering a whole /16 in one pass affordable on a phone. The
//  best guess at that number is not .1 but whatever number this network put its
//  own gateway on: a site that numbers its routers .250 does so on every segment,
//  and probing .1 there finds nothing at all. Quiet addresses get a TCP knock
//  afterwards, because a router that drops ICMP will still usually answer on its
//  management port.
//

package com.sclera.sclerago.services.scan.core

/** One subnet discovery turned up, and what proved it. */
data class DiscoveredSubnet(
    val subnet: IPv4Subnet,
    val sources: Set<Source>,
    /** The address that proved it: a router interface, or whatever answered. */
    val evidence: IPv4? = null,
) {
    enum class Source(val label: String) {
        /** The segment this device is attached to. */
        LOCAL("This device"),

        /** An interface the router has an address on. */
        ROUTER("Router"),

        /** A route the router holds to a segment it is not itself on. */
        ROUTED("Routed"),

        /** A resolver this device was handed sits there. */
        DNS("DNS server"),

        /** The DHCP server that issued our lease sits there. */
        DHCP("DHCP server"),

        /** A route this device itself holds, from DHCP option 121 or a VPN. */
        ROUTE("Our route"),

        /** Something answered at the conventional gateway address. */
        PING("Ping"),
    }

    val isLocal: Boolean get() = Source.LOCAL in sources

    /** Sources in a stable order, for a caption like "Router · Ping". */
    val sourceLabel: String
        get() = Source.entries.filter { it in sources }.joinToString(" · ") { it.label }
}

/**
 * What one discovery run turned up, and how each source fared.
 *
 * The outcome per source matters as much as the list: "one subnet" means
 * something very different when the router answered than when SNMP was never
 * tried and every probe went unanswered, and the user cannot tell those apart
 * from the list alone.
 */
data class SubnetDiscoveryReport(
    val subnets: List<DiscoveredSubnet> = emptyList(),
    val snmp: SnmpOutcome = SnmpOutcome.NOT_TRIED,
    /** The space the gateway sweep covered, so its silence can be read correctly. */
    val sweptSpace: IPv4Subnet? = null,
    val candidatesProbed: Int = 0,
    val gatewaysAnswered: Int = 0,
    /** Set when the ICMP socket was refused and the sweep fell back to TCP. */
    val icmpUnavailable: Boolean = false,
) {
    enum class SnmpOutcome { NOT_TRIED, NO_ANSWER, ANSWERED }

    /** A one-line account of where the answer came from, for the status line. */
    val summary: String
        get() = buildString {
            append(
                when (subnets.size) {
                    0 -> "No subnets found"
                    1 -> "1 subnet"
                    else -> "${subnets.size} subnets"
                },
            )
            append(
                when (snmp) {
                    SnmpOutcome.NOT_TRIED -> " · SNMP not tried"
                    SnmpOutcome.NO_ANSWER -> " · no SNMP reply"
                    SnmpOutcome.ANSWERED -> " · router answered"
                },
            )
            if (icmpUnavailable) append(" · ping unavailable")
            if (sweptSpace != null) {
                append(" · $gatewaysAnswered of $candidatesProbed gateways in $sweptSpace")
            }
        }
}

object SubnetDiscovery {

    /** Last octets a router conventionally answers on, absent better evidence. */
    private val GATEWAY_HOSTS = intArrayOf(1, 254)

    /** Ports a router's own management interface usually answers on. */
    private val GATEWAY_PORTS = intArrayOf(80, 443)

    /**
     * Widest a reported subnet may be and still absorb a guessed /24.
     *
     * A router that summarises its routes reports something like 10.0.0.0/8,
     * which contains every 10.x guess we could ever make. Folding into that would
     * collapse a dozen separate segments into one useless row — which is exactly
     * how a discovery of a dozen subnets reports itself as one.
     */
    private const val WIDEST_ABSORBING_PREFIX = 16

    /**
     * How wide a space the gateway sweep covers, as a prefix around our address.
     *
     * A /16 is 256 candidate /24s, so 512 probes — seconds of work. Widening it to
     * the enclosing private block is a different kind of decision, so it is the
     * caller's to make: see [enclosingPrivateBlock] and [Breadth].
     */
    const val SWEEP_PREFIX_LENGTH = 16

    /** How much address space one discovery run is allowed to probe. */
    enum class Breadth(val label: String) {
        /** The /16 around this device, at both conventional gateway addresses. */
        NEARBY("Nearby"),

        /** The whole private block this device sits in, at .1 only. */
        WIDE("Wide"),
    }

    /** Above this many candidates the TCP knock costs more time than it earns. */
    private const val MAX_KNOCK_CANDIDATES = 1024

    /** The RFC 1918 block [address] belongs to, or its /16 if it is in none. */
    fun enclosingPrivateBlock(address: IPv4): IPv4Subnet {
        val octets = address.octets
        return when {
            octets[0] == 10 -> IPv4Subnet.of(address, 8)
            octets[0] == 172 && octets[1] in 16..31 -> IPv4Subnet.of(address, 12)
            octets[0] == 192 && octets[1] == 168 -> IPv4Subnet.of(address, 16)
            else -> IPv4Subnet.of(address, SWEEP_PREFIX_LENGTH)
        }
    }

    /**
     * Everything we can learn about which subnets exist, richest source first.
     *
     * Results are restricted to RFC 1918 space. A router's route table also names
     * the public networks behind its WAN port, and offering those up as scan
     * targets would point the sweep at somebody else's network.
     */
    suspend fun discover(
        local: LocalInterface?,
        router: IPv4?,
        communities: List<String>,
        breadth: Breadth = Breadth.NEARBY,
        sweeper: IcmpSweeper = IcmpSweeper(),
        onStatus: (String) -> Unit = {},
    ): SubnetDiscoveryReport {
        // ---- What the router will tell us ----------------------------------
        var reported: SnmpSubnets? = null
        var outcome = SubnetDiscoveryReport.SnmpOutcome.NOT_TRIED
        if (router != null && communities.isNotEmpty()) {
            onStatus("Asking $router for its routing table…")
            reported = SnmpClient.readSubnets(router, communities)
            outcome = if (reported == null || reported.isEmpty) {
                SubnetDiscoveryReport.SnmpOutcome.NO_ANSWER
            } else {
                SubnetDiscoveryReport.SnmpOutcome.ANSWERED
            }
        }

        // ---- What answers where a router usually sits ----------------------
        val anchor = local?.address
        val space = anchor?.let { sweepSpace(it, breadth) }
        val candidates = if (anchor == null || space == null) {
            emptyList()
        } else {
            gatewayCandidates(anchor, breadth, observedGateway = router)
        }
        val answered = LinkedHashSet<IPv4>()
        var icmpUnavailable = false

        if (candidates.isNotEmpty()) {
            onStatus("Pinging ${candidates.size} likely gateways in $space…")
            val rounds = if (breadth == Breadth.NEARBY) 2 else 1
            try {
                answered += sweeper.sweep(candidates, rounds = rounds, timeout = 1.4).keys
            } catch (_: IcmpUnavailableException) {
                icmpUnavailable = true
            }

            // Plenty of routers drop ICMP but still serve a management page, and
            // on a device where the ping socket is refused outright this is the
            // only probe left. Only addresses that stayed silent are worth the
            // connect, and only while there are few enough of them to be quick.
            val silent = candidates.filterNot { it in answered }
            if (silent.isNotEmpty() && silent.size <= MAX_KNOCK_CANDIDATES) {
                onStatus("Knocking on ${silent.size} quiet gateways…")
                answered += knockGateways(silent)
            }
        }

        return SubnetDiscoveryReport(
            subnets = merge(
                local = local?.subnet,
                localAddress = anchor,
                connected = reported?.connected.orEmpty(),
                routed = reported?.routed.orEmpty(),
                answered = answered.toList(),
                dnsServers = local?.dnsServers.orEmpty(),
                dhcpServer = local?.dhcpServer,
                ownRoutes = local?.extraRoutes.orEmpty(),
            ),
            snmp = outcome,
            sweptSpace = space,
            candidatesProbed = candidates.size,
            gatewaysAnswered = answered.size,
            icmpUnavailable = icmpUnavailable,
        )
    }

    /** The space [breadth] asks the sweep to cover around [anchor]. */
    fun sweepSpace(anchor: IPv4, breadth: Breadth): IPv4Subnet = when (breadth) {
        Breadth.NEARBY -> IPv4Subnet.of(anchor, SWEEP_PREFIX_LENGTH)
        Breadth.WIDE -> enclosingPrivateBlock(anchor)
    }

    /** TCP knock on the addresses the ping sweep left silent. */
    private suspend fun knockGateways(candidates: List<IPv4>): List<IPv4> {
        val answered = LinkedHashSet<IPv4>()
        val jobs: List<suspend () -> IPv4?> = candidates.flatMap { host ->
            GATEWAY_PORTS.map { port ->
                suspend {
                    // A refusal is as good as an accept: something is listening
                    // at that address, which is all the sweep needs to know.
                    val result = TcpProbe.connect(host, port, KNOCK_TIMEOUT)
                    if (result == TcpOutcome.OPEN || result == TcpOutcome.REFUSED) host else null
                }
            }
        }
        runJobs(jobs, SystemLimits.socketBudget(KNOCK_CONCURRENCY)) { host ->
            if (host != null) answered += host
        }
        return answered.toList()
    }

    /**
     * Folds the three sources into one list, with the I/O left outside.
     *
     * Every judgement the feature makes lives here — what counts as a LAN, which
     * guesses a reported subnet swallows, what order the menu comes in — so it can
     * be exercised without a network.
     */
    internal fun merge(
        local: IPv4Subnet?,
        localAddress: IPv4?,
        connected: Map<IPv4Subnet, IPv4>,
        routed: List<IPv4Subnet>,
        answered: List<IPv4>,
        dnsServers: List<IPv4> = emptyList(),
        dhcpServer: IPv4? = null,
        ownRoutes: List<IPv4Subnet> = emptyList(),
    ): List<DiscoveredSubnet> {
        val found = LinkedHashMap<IPv4Subnet, DiscoveredSubnet>()

        fun record(subnet: IPv4Subnet, source: DiscoveredSubnet.Source, evidence: IPv4?) {
            if (!isPlausibleLan(subnet)) return
            val existing = found[subnet]
            found[subnet] = DiscoveredSubnet(
                subnet = subnet,
                sources = (existing?.sources ?: emptySet()) + source,
                evidence = existing?.evidence ?: evidence,
            )
        }

        if (local != null) record(local, DiscoveredSubnet.Source.LOCAL, localAddress)
        for ((subnet, address) in connected) {
            record(subnet, DiscoveredSubnet.Source.ROUTER, address)
        }
        for (subnet in routed) {
            record(subnet, DiscoveredSubnet.Source.ROUTED, subnet.likelyGateway)
        }
        for (subnet in ownRoutes) {
            record(subnet, DiscoveredSubnet.Source.ROUTE, subnet.likelyGateway)
        }
        // A server's address names its segment but not its mask, so these take the
        // same /24 assumption the ping sweep does, and merge with a reported
        // subnet when one turns out to cover them.
        for (address in dnsServers) {
            record(IPv4Subnet.of(address, 24), DiscoveredSubnet.Source.DNS, address)
        }
        if (dhcpServer != null) {
            record(IPv4Subnet.of(dhcpServer, 24), DiscoveredSubnet.Source.DHCP, dhcpServer)
        }
        for (address in answered) {
            record(IPv4Subnet.of(address, 24), DiscoveredSubnet.Source.PING, address)
        }

        return collapse(found.values)
    }

    /**
     * Host numbers worth probing in each /24 of the swept space.
     *
     * [observedGateway] is the strongest evidence available and always goes in
     * first: whatever number this network gave its own router is the number its
     * other routers almost certainly use. The conventional .1 follows, and .254
     * only on a narrow sweep — over a /8 that third probe is another 65,536
     * packets to catch the minority of sites that number from the top.
     */
    private fun candidateHosts(breadth: Breadth, observedGateway: IPv4?): List<Int> {
        val hosts = LinkedHashSet<Int>()
        observedGateway?.octets?.get(3)?.takeIf { it in 1..254 }?.let { hosts += it }
        hosts += GATEWAY_HOSTS[0]
        if (breadth == Breadth.NEARBY) hosts += GATEWAY_HOSTS[1]
        return hosts.toList()
    }

    /** The likely router addresses of every /24 in the swept space. */
    fun gatewayCandidates(
        anchor: IPv4,
        breadth: Breadth = Breadth.NEARBY,
        observedGateway: IPv4? = null,
    ): List<IPv4> {
        val space = sweepSpace(anchor, breadth)
        val hosts = candidateHosts(breadth, observedGateway)
        val subnetCount = 1 shl (24 - space.prefixLength).coerceIn(0, 16)
        val candidates = ArrayList<IPv4>(subnetCount * hosts.size)
        for (index in 0 until subnetCount) {
            val base = space.network.raw + (index shl 8)
            for (host in hosts) candidates += IPv4(base + host)
        }
        return candidates
    }

    /**
     * Drops a guessed /24 that a reported subnet already covers.
     *
     * A ping can only ever conclude "/24", because that is the assumption it was
     * built on. When SNMP has told us the same address lives in a /22, the /22 is
     * the truth and the /24 is a duplicate of part of it — so the sources merge
     * into the wider record rather than listing the segment twice.
     */
    private fun collapse(all: Collection<DiscoveredSubnet>): List<DiscoveredSubnet> {
        val results = LinkedHashMap<IPv4Subnet, DiscoveredSubnet>()
        for (entry in all) results[entry.subnet] = entry

        val reported = all.filterNot { entry ->
            entry.sources.all { it in ASSUMED_PREFIX_SOURCES }
        }
        for (entry in all) {
            // Only an assumed /24 with nothing authoritative behind it folds away;
            // a subnet something reported the real mask for stands on its own.
            if (!entry.sources.all { it in ASSUMED_PREFIX_SOURCES }) continue
            val covering = reported.firstOrNull { candidate ->
                candidate.subnet.prefixLength in
                    WIDEST_ABSORBING_PREFIX until entry.subnet.prefixLength &&
                    entry.subnet.network in candidate.subnet
            } ?: continue

            results.remove(entry.subnet)
            val current = results[covering.subnet] ?: covering
            results[covering.subnet] = current.copy(
                sources = current.sources + entry.sources,
                evidence = current.evidence ?: entry.evidence,
            )
        }

        // Our own segment first, then in address order: the list is a menu of scan
        // targets, and the one we are on is the obvious starting point.
        return results.values.sortedWith(
            compareByDescending<DiscoveredSubnet> { it.isLocal }.thenBy { it.subnet.network },
        )
    }

    /** Sources that can only ever assume a /24, so a real mask supersedes them. */
    private val ASSUMED_PREFIX_SOURCES = setOf(
        DiscoveredSubnet.Source.PING,
        DiscoveredSubnet.Source.DNS,
        DiscoveredSubnet.Source.DHCP,
    )

    private const val KNOCK_TIMEOUT = 0.6
    private const val KNOCK_CONCURRENCY = 160

    /**
     * Whether a reported network is one an asset scan should offer to sweep.
     *
     * Host routes, summaries too wide to mean a segment, and anything outside
     * private space are noise here even when the router genuinely holds them.
     */
    private fun isPlausibleLan(subnet: IPv4Subnet): Boolean =
        subnet.prefixLength in 8..30 &&
            subnet.network.isPrivate &&
            !subnet.network.isLoopback
}
