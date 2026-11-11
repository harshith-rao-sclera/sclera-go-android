//
//  NetworkScanner.kt
//  AssetOnBoard
//
//  Orchestrates every probe and merges the results into one device list.
//
//  The pipeline is deliberately layered, because no single technique sees the
//  whole network: ICMP finds most hosts, a TCP knock catches the ones that drop
//  pings, mDNS finds devices that never answer either, and the enrichment pass
//  turns bare addresses into named, classified devices.
//
//  State is published through StateFlows rather than an @Observable object, so
//  probe results can be merged from any dispatcher without hopping to the main
//  thread for every one of the thousands a scan produces.
//

package com.example.sclerago.scan.core

import android.content.Context
import com.example.sclerago.scan.data.OuiDatabase
import com.example.sclerago.scan.model.DiscoveryMethod
import com.example.sclerago.scan.model.NetworkDevice
import com.example.sclerago.scan.model.ScanReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NetworkScanner(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    sealed interface Phase {
        val label: String

        /** True for the phases that make up an active scan. */
        val isScanning: Boolean
            get() = this is Preparing || this is Sweeping || this is NameSweep ||
                this is Knocking || this is Enriching

        data object Idle : Phase {
            override val label = "Ready"
        }

        data object Preparing : Phase {
            override val label = "Preparing"
        }

        data object Sweeping : Phase {
            override val label = "Pinging subnet"
        }

        data object NameSweep : Phase {
            override val label = "Asking Windows hosts"
        }

        data object Knocking : Phase {
            override val label = "Probing quiet hosts"
        }

        data object Enriching : Phase {
            override val label = "Identifying devices"
        }

        data object Finished : Phase {
            override val label = "Scan complete"
        }

        data class Failed(override val label: String) : Phase
    }

    // MARK: - Published state

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusDetail = MutableStateFlow("")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _devices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val devices: StateFlow<List<NetworkDevice>> = _devices.asStateFlow()

    private val _interfaceInfo = MutableStateFlow<LocalInterface?>(null)
    val interfaceInfo: StateFlow<LocalInterface?> = _interfaceInfo.asStateFlow()

    private val _subnet = MutableStateFlow<IPv4Subnet?>(null)
    val subnet: StateFlow<IPv4Subnet?> = _subnet.asStateFlow()

    private val _gateway = MutableStateFlow<IPv4?>(null)
    val gateway: StateFlow<IPv4?> = _gateway.asStateFlow()

    /**
     * True when [subnet] is a range the user asked for rather than the one this
     * device sits on, so the UI can say the results depend on the router
     * forwarding for us.
     */
    private val _offLinkTarget = MutableStateFlow(false)
    val offLinkTarget: StateFlow<Boolean> = _offLinkTarget.asStateFlow()

    /** Set when mDNS could not be brought up, the analogue of iOS's Local Network refusal. */
    private val _mdnsUnavailable = MutableStateFlow(false)
    val mdnsUnavailable: StateFlow<Boolean> = _mdnsUnavailable.asStateFlow()

    /**
     * Set when mDNS is running but had to fall back to an ephemeral port.
     *
     * Queries are still answered; what is lost is the unsolicited announcements
     * broadcast to 5353, which are how devices that ignore direct queries get
     * found at all.
     */
    private val _mdnsPassiveOnlyDegraded = MutableStateFlow(false)
    val mdnsPassiveOnlyDegraded: StateFlow<Boolean> = _mdnsPassiveOnlyDegraded.asStateFlow()

    /** The last completed sweep's network section, for export. */
    private val _lastSection = MutableStateFlow<ScanReport.NetworkSection?>(null)
    val lastNetworkSection: StateFlow<ScanReport.NetworkSection?> = _lastSection.asStateFlow()

    private val _arpAvailable = MutableStateFlow(false)
    val arpAvailable: StateFlow<Boolean> = _arpAvailable.asStateFlow()

    /** Subnets the last discovery run turned up, for the target picker. */
    private val _discoveredSubnets = MutableStateFlow<List<DiscoveredSubnet>>(emptyList())
    val discoveredSubnets: StateFlow<List<DiscoveredSubnet>> = _discoveredSubnets.asStateFlow()

    private val _discoveringSubnets = MutableStateFlow(false)
    val discoveringSubnets: StateFlow<Boolean> = _discoveringSubnets.asStateFlow()

    private val _discoveryStatus = MutableStateFlow("")
    val subnetDiscoveryStatus: StateFlow<String> = _discoveryStatus.asStateFlow()

    private val _snmpResult = MutableStateFlow<SnmpResult?>(null)
    val snmpResult: StateFlow<SnmpResult?> = _snmpResult.asStateFlow()

    private val _snmpMacCount = MutableStateFlow(0)
    val snmpMacCount: StateFlow<Int> = _snmpMacCount.asStateFlow()

    /** Read-only community strings tried against the gateway, in order. */
    var snmpCommunities: List<String> = listOf("public")

    /**
     * Forces the LAN scan onto an interface of this kind (Wi-Fi or Ethernet).
     *
     * Null keeps the historical behaviour of scanning whatever network is active.
     * Set it before [refreshInterface] or [start] to pin the scan to a wired or
     * wireless segment when the device is attached to both.
     */
    var preferredKind: LocalInterface.Kind? = null

    /**
     * Scans this range instead of the one the chosen interface is attached to.
     *
     * Null keeps the default behaviour of sweeping the local segment. Any other
     * subnet is swept over the same interface, which only reaches it if the
     * router forwards between the two — the usual case on a corporate LAN with
     * one VLAN per floor, and never the case behind a home router's guest
     * network. Set it before [refreshInterface] or [start].
     */
    var customSubnet: IPv4Subnet? = null

    /**
     * The router of [customSubnet], when something already established it.
     *
     * Discovery learns the real address by getting an answer from it, and
     * guessing x.x.x.1 afterwards would throw that away — on a network that
     * numbers its gateways anywhere else, the guess is simply wrong, and it is
     * also the address the SNMP query goes to.
     */
    var customGateway: IPv4? = null

    val isScanning: Boolean get() = _phase.value.isScanning

    /** Hosts that answered something, newest information merged in. */
    fun sortedDevices(): List<NetworkDevice> = _devices.value.sortedBy { it.ip }

    var startedAt: Long? = null
        private set

    var finishedAt: Long? = null
        private set

    // MARK: - Private state

    private var activeDepth: ScanDepth = ScanDepth.STANDARD
    private val indexLock = Mutex()
    private val index = LinkedHashMap<IPv4, NetworkDevice>()

    /** Addresses whose record changed since the last publish. */
    private val dirty = LinkedHashSet<IPv4>()
    private var publishPending = false
    private var lastPublish = 0L

    private var scanJob: Job? = null
    private var discoveryJob: Job? = null
    private var mdnsJob: Job? = null
    private var mdns: MdnsDiscovery? = null
    private val sweeper = IcmpSweeper()

    // MARK: - Passive discovery

    /**
     * Starts mDNS browsing without an active scan, so the list is already
     * filling in by the time the user reaches for the Scan button.
     */
    fun startPassiveDiscovery() {
        if (mdnsJob != null) return
        refreshInterface()

        val discovery = MdnsDiscovery(context)
        mdns = discovery
        mdnsJob = scope.launch {
            discovery.start().collect { service -> apply(service) }
        }

        // Give the responder a moment, then report whether it came up at all.
        scope.launch {
            delay(3000)
            _mdnsUnavailable.value = discovery.unavailable
            _mdnsPassiveOnlyDegraded.value =
                !discovery.unavailable && discovery.passiveListeningUnavailable
        }
    }

    /** The scannable Wi-Fi / Ethernet interfaces the device is attached to. */
    fun availableInterfaces(): List<LocalInterface> =
        NetworkInterfaceScanner.scannable(context)

    fun refreshInterface() {
        val candidate = NetworkInterfaceScanner.preferredScanInterface(context, preferredKind)
        _interfaceInfo.value = candidate

        val target = customSubnet
        if (candidate == null && target == null) {
            _subnet.value = null
            _offLinkTarget.value = false
            return
        }

        val full = target ?: candidate!!.subnet
        // A local sweep is centred on this device; a range the user named is
        // clamped from its own base, because there is no address of ours inside
        // it to centre on.
        val anchor = if (target != null) target.network else candidate!!.address
        val resolved = full.clamped(anchor, maxHosts = MAX_SWEEP_HOSTS)
        // Joining a different network, or retargeting, invalidates the gateway.
        if (_subnet.value != resolved) _gateway.value = null
        _subnet.value = resolved
        _offLinkTarget.value =
            target != null && (candidate == null || !full.contains(candidate.address))

        // The platform usually knows the real gateway outright; the conventional
        // x.x.x.1 is only a placeholder for when it does not. For a range off our
        // own link the platform's gateway is the wrong one — it routes us out,
        // it does not serve that segment — so the placeholder stands in.
        val known = customGateway?.takeIf { target != null && full.contains(it) }
        val discovered = candidate?.routerAddress ?: GatewayDiscovery.discover(context)
        _gateway.value = when {
            known != null -> known
            discovered != null && full.contains(discovered) -> discovered
            else -> _gateway.value ?: full.likelyGateway
        }
    }

    // MARK: - Subnet discovery

    /**
     * Looks for the other subnets on this network, to fill the target picker.
     *
     * Deliberately not part of a scan: it is a question about the shape of the
     * network rather than its contents, the answer changes on the timescale of
     * cabling rather than of DHCP leases, and a sweep the user did not ask for is
     * not something to slip into every scan.
     */
    fun discoverSubnets(breadth: SubnetDiscovery.Breadth = SubnetDiscovery.Breadth.NEARBY) {
        if (isScanning || _discoveringSubnets.value) return
        refreshInterface()

        // Our own default route, not [gateway] — that one follows the custom
        // target, and the router to interrogate is the one this device is behind.
        val info = _interfaceInfo.value
        val router = info?.routerAddress ?: GatewayDiscovery.discover(context)

        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            _discoveringSubnets.value = true
            _discoveryStatus.value = "Looking for other subnets…"
            try {
                val report = SubnetDiscovery.discover(
                    local = info,
                    router = router,
                    communities = snmpCommunities,
                    breadth = breadth,
                    sweeper = sweeper,
                    onStatus = { _discoveryStatus.value = it },
                )
                _discoveredSubnets.value = report.subnets
                _discoveryStatus.value = report.summary
            } finally {
                _discoveringSubnets.value = false
                discoveryJob = null
            }
        }
    }

    fun stopSubnetDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discoveringSubnets.value = false
        _discoveryStatus.value = "Stopped"
    }

    // MARK: - Scanning

    fun start(depth: ScanDepth) {
        if (isScanning) return
        startPassiveDiscovery()

        scanJob?.cancel()
        scanJob = scope.launch { run(depth) }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        if (isScanning) {
            _phase.value = Phase.Finished
            finishedAt = System.currentTimeMillis()
            _statusDetail.value = "Stopped"
        }
    }

    fun reset() {
        stop()
        scope.launch {
            _snmpResult.value = null
            _snmpMacCount.value = 0
            indexLock.withLock {
                index.clear()
                dirty.clear()
            }
            mdns?.reset()
            _devices.value = emptyList()
            _progress.value = 0f
            _phase.value = Phase.Idle
            _statusDetail.value = ""
            _lastSection.value = null
            _discoveredSubnets.value = emptyList()
            _discoveryStatus.value = ""
        }
    }

    /** Frees the multicast socket and lock; call from the owner's teardown. */
    suspend fun shutdown() {
        scanJob?.cancelAndJoin()
        scanJob = null
        discoveryJob?.cancelAndJoin()
        discoveryJob = null
        mdnsJob?.cancelAndJoin()
        mdnsJob = null
        mdns = null
    }

    private suspend fun run(depth: ScanDepth) {
        activeDepth = depth
        startedAt = System.currentTimeMillis()
        finishedAt = null
        _phase.value = Phase.Preparing
        _progress.value = 0f
        _statusDetail.value = "Looking for an active network interface…"

        refreshInterface()
        val subnet = _subnet.value
        if (_interfaceInfo.value == null || subnet == null) {
            _phase.value = Phase.Failed("No Wi-Fi or Ethernet connection found.")
            return
        }

        // Keep whatever mDNS already found; drop stale active results. When the
        // sweep is aimed elsewhere, drop the passive finds too: they are hosts on
        // our own link, and listing them under another range's results is a lie.
        val targetOnly = customSubnet != null
        indexLock.withLock {
            val stale = index.filterValues { it.bonjourServices.isEmpty() }.keys.toList() +
                if (targetOnly) index.keys.filterNot { it in subnet } else emptyList()
            for (key in stale) {
                index.remove(key)
                dirty.remove(key)
            }
        }

        val targets = subnet.hostAddresses
        _statusDetail.value = "$subnet · ${targets.size} addresses"

        // ---- Stage 1: ICMP sweep -------------------------------------------
        _phase.value = Phase.Sweeping
        val alive = LinkedHashSet<IPv4>()

        try {
            val replies = sweeper.sweep(targets, rounds = 2, timeout = 1.4)
            for ((address, reply) in replies) {
                alive += address
                store(
                    NetworkDevice(
                        ip = address,
                        roundTripTime = reply.roundTripTime,
                        ttl = reply.ttl.takeIf { it > 0 },
                        discoveryMethods = setOf(DiscoveryMethod.ICMP),
                    ),
                )
            }
        } catch (_: IcmpUnavailableException) {
            // An unavailable ICMP socket is survivable; the TCP knock still runs.
            _statusDetail.value = "Ping unavailable — falling back to TCP discovery"
        }
        if (!currentCoroutineContext().isActive) return finish()
        _progress.value = 0.18f
        _statusDetail.value =
            "${alive.size} host${if (alive.size == 1) "" else "s"} answered ping"

        // ---- Stage 2: NetBIOS name sweep -----------------------------------
        // Windows blocks ICMP out of the box and a stock install listens on none
        // of the usual TCP ports, so a ping-and-knock sweep walks straight past
        // it. It does answer a unicast NBSTAT query though, and that single
        // datagram returns the machine name and its MAC — which is why this runs
        // across the entire subnet rather than only over hosts already found.
        _phase.value = Phase.NameSweep
        _statusDetail.value = "Querying ${targets.size} addresses for NetBIOS names…"

        val netbiosJobs: List<suspend () -> Pair<IPv4, NetBiosInfo>?> = targets.map { host ->
            { NetBiosProbe.query(host)?.let { host to it } }
        }

        var netbiosFound = 0
        var netbiosDone = 0
        val netbiosTotal = maxOf(netbiosJobs.size, 1)
        runJobs(netbiosJobs, depth.concurrency) { result ->
            netbiosDone += 1
            if (netbiosDone % 40 == 0) {
                _progress.value = 0.18f + 0.12f * (netbiosDone.toFloat() / netbiosTotal)
            }
            if (result != null) {
                netbiosFound += 1
                alive += result.first
                apply(Enrichment.NetBios(result.first, result.second))
            }
        }
        if (!currentCoroutineContext().isActive) return finish()
        _progress.value = 0.3f
        _statusDetail.value =
            "$netbiosFound Windows host${if (netbiosFound == 1) "" else "s"} named"

        // ---- Stage 3: TCP knock on everything that stayed silent -----------
        _phase.value = Phase.Knocking
        val quiet = targets.filter { it !in alive }
        val knockPorts = ServiceCatalog.discoveryKnockPorts.take(depth.knockPortCount)

        val knockJobs: List<suspend () -> IPv4?> = quiet.flatMap { host ->
            knockPorts.map { port ->
                suspend {
                    val outcome = TcpProbe.connect(host, port, depth.connectTimeout)
                    // A refusal is just as good as an accept: something is there.
                    if (outcome == TcpOutcome.OPEN || outcome == TcpOutcome.REFUSED) host else null
                }
            }
        }

        var knockCompleted = 0
        val knockTotal = maxOf(knockJobs.size, 1)
        runJobs(knockJobs, depth.concurrency) { host ->
            knockCompleted += 1
            if (knockCompleted % 25 == 0) {
                _progress.value = 0.3f + 0.1f * (knockCompleted.toFloat() / knockTotal)
            }
            if (host != null && host !in alive) {
                alive += host
                store(
                    NetworkDevice(ip = host, discoveryMethods = setOf(DiscoveryMethod.TCP)),
                )
            }
        }
        if (!currentCoroutineContext().isActive) return finish()

        // Anything mDNS found on this subnet counts as alive too.
        indexLock.withLock {
            for ((address, device) in index) {
                if (device.bonjourServices.isNotEmpty() && subnet.contains(address)) {
                    alive += address
                }
            }
        }
        _progress.value = 0.4f

        // ---- Stage 3a: ARP cache (emulators and older Android only) --------
        val arpEntries = ArpTable.current()
        _arpAvailable.value = arpEntries.isNotEmpty()
        for ((address, mac) in arpEntries) {
            if (!subnet.contains(address)) continue
            alive += address
            store(
                NetworkDevice(
                    ip = address,
                    macAddress = mac,
                    discoveryMethods = setOf(DiscoveryMethod.ARP),
                ),
            )
        }

        // ---- Stage 3b: ask the router for its ARP table --------------------
        // The gateway is the only host that knows every IP-to-MAC binding on the
        // subnet, and Android keeps its own ARP cache out of reach. When SNMP is
        // enabled this fills in MACs for devices that answer nothing else.
        _snmpResult.value = null
        _snmpMacCount.value = 0
        val router = _gateway.value
        if (router != null && snmpCommunities.isNotEmpty()) {
            _statusDetail.value = "Querying $router over SNMP…"
            val result = SnmpClient.readArpTable(router, snmpCommunities)
            if (result != null) {
                _snmpResult.value = result
                var contributed = 0
                for ((address, mac) in result.arpTable) {
                    if (!subnet.contains(address)) continue
                    alive += address
                    if (currentMac(address) == null) contributed += 1
                    store(
                        NetworkDevice(
                            ip = address,
                            macAddress = mac,
                            discoveryMethods = setOf(DiscoveryMethod.SNMP),
                        ),
                    )
                }
                _snmpMacCount.value = contributed
            }
        }
        if (!currentCoroutineContext().isActive) return finish()

        // ---- Stage 4: enrich every live host -------------------------------
        _phase.value = Phase.Enriching
        val hosts = alive.sorted()
        _statusDetail.value =
            "Identifying ${hosts.size} device${if (hosts.size == 1) "" else "s"}…"

        val ports = depth.ports
        val connectTimeout = depth.connectTimeout
        val jobs = mutableListOf<suspend () -> Enrichment?>()

        // Snapshot which hosts already answered NetBIOS, rather than taking the
        // index lock once per host while building the plan.
        val alreadyNamed = indexLock.withLock {
            index.filterValues { it.netbios != null }.keys.toSet()
        }

        for (host in hosts) {
            jobs += { NameResolution.reverseLookup(host)?.let { Enrichment.Hostname(host, it) } }
            // The name sweep already covered every address; only retry hosts it
            // did not answer for, in case the first datagram was simply lost.
            if (host !in alreadyNamed) {
                jobs += { NetBiosProbe.query(host)?.let { Enrichment.NetBios(host, it) } }
            }
            for (definition in ports) {
                jobs += {
                    ServiceProbe.probe(host, definition, connectTimeout)
                        ?.let { Enrichment.Port(host, it) }
                }
            }
        }

        var completed = 0
        val total = maxOf(jobs.size, 1)
        val hostCount = hosts.size
        runJobs(jobs, depth.concurrency) { result ->
            completed += 1
            if (completed % 25 == 0) {
                _progress.value = 0.4f + 0.58f * (completed.toFloat() / total)
                _statusDetail.value = "$hostCount hosts · $completed/$total probes"
            }
            if (result != null) apply(result)
        }

        finish()
    }

    private fun finish() {
        scope.launch {
            indexLock.withLock { dirty.addAll(index.keys) }
            publish()

            _progress.value = 1f
            _phase.value = Phase.Finished
            finishedAt = System.currentTimeMillis()
            val count = _devices.value.size
            _statusDetail.value = "$count device${if (count == 1) "" else "s"} found"

            val info = _interfaceInfo.value
            val subnet = _subnet.value
            if (info != null && subnet != null) {
                _lastSection.value = ScanReport.NetworkSection(
                    interfaceName = info.name,
                    interfaceKind = info.kind.label,
                    localAddress = info.address,
                    subnet = subnet.toString(),
                    subnetSource = subnetSource(),
                    gateway = _gateway.value,
                    depth = activeDepth.label,
                    devices = sortedDevices(),
                )
            }
        }
        scanJob = null
    }

    /** Builds the network section from the current results, even mid-scan. */
    fun currentNetworkSection(depth: ScanDepth): ScanReport.NetworkSection? {
        val info = _interfaceInfo.value ?: return null
        val subnet = _subnet.value ?: return null
        return ScanReport.NetworkSection(
            interfaceName = info.name,
            interfaceKind = info.kind.label,
            localAddress = info.address,
            subnet = subnet.toString(),
            subnetSource = subnetSource(),
            gateway = _gateway.value,
            depth = depth.label,
            devices = sortedDevices(),
        )
    }

    /** Whether the exported sweep covered our own segment or a named range. */
    private fun subnetSource(): String = if (customSubnet == null) "interface" else "custom"


    // MARK: - Merging

    private sealed interface Enrichment {
        data class Hostname(val host: IPv4, val name: String) : Enrichment
        data class NetBios(val host: IPv4, val info: NetBiosInfo) : Enrichment
        data class Port(val host: IPv4, val observation: PortObservation) : Enrichment
    }

    private suspend fun apply(enrichment: Enrichment) {
        when (enrichment) {
            is Enrichment.Hostname -> {
                val tidied = NameResolution.tidy(enrichment.name)
                store(
                    NetworkDevice(
                        ip = enrichment.host,
                        hostnames = listOf(tidied),
                        discoveryMethods = setOf(DiscoveryMethod.REVERSE_DNS),
                    ),
                )
            }

            is Enrichment.NetBios -> {
                val mac = enrichment.info.macAddress
                store(
                    NetworkDevice(
                        ip = enrichment.host,
                        netbios = enrichment.info,
                        macAddress = mac,
                        vendor = mac?.let { OuiDatabase.vendor(it) },
                        discoveryMethods = setOf(DiscoveryMethod.NETBIOS),
                    ),
                )
            }

            is Enrichment.Port -> store(
                NetworkDevice(
                    ip = enrichment.host,
                    openPorts = listOf(enrichment.observation),
                    discoveryMethods = setOf(DiscoveryMethod.TCP),
                ),
            )
        }
    }

    private suspend fun apply(service: BonjourService) {
        // A service with no resolved address cannot be attributed to a host.
        val address = service.addresses.firstOrNull() ?: return
        // mDNS only ever hears our own link. While the sweep is pointed at another
        // range, those announcements are noise against the results being gathered.
        val target = _subnet.value
        if (customSubnet != null && target != null && address !in target) return
        val mac = service.advertisedMac

        store(
            NetworkDevice(
                ip = address,
                bonjourServices = listOf(service),
                macAddress = mac,
                vendor = mac?.let { OuiDatabase.vendor(it) },
                discoveryMethods = setOf(DiscoveryMethod.BONJOUR),
            ),
        )
    }

    /**
     * Merges a sighting into the index. Deliberately cheap: fingerprinting and
     * the UI-visible list are deferred to [publish], because this runs once per
     * probe result and a scan produces thousands of them.
     */
    private suspend fun store(device: NetworkDevice) {
        indexLock.withLock {
            val existing = index[device.ip]
            index[device.ip] = existing?.mergedWith(device) ?: device
            dirty += device.ip
        }
        schedulePublish()
    }

    private suspend fun currentMac(address: IPv4): MACAddress? =
        indexLock.withLock { index[address]?.macAddress }

    /**
     * Coalesces UI updates to a few per second.
     *
     * Publishing on every result meant re-fingerprinting and re-diffing the
     * whole list thousands of times, which is enough to drop frames on a busy
     * subnet even with the work off the main thread.
     */
    private fun schedulePublish() {
        synchronized(this) {
            if (publishPending) return
            publishPending = true
        }
        val delayMillis = (PUBLISH_INTERVAL_MS - (System.currentTimeMillis() - lastPublish))
            .coerceAtLeast(0L)

        scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            synchronized(this@NetworkScanner) { publishPending = false }
            publish()
        }
    }

    private suspend fun publish() {
        lastPublish = System.currentTimeMillis()
        val gateway = _gateway.value
        val localAddress = _interfaceInfo.value?.address

        val snapshot = indexLock.withLock {
            for (address in dirty) {
                var device = index[address] ?: continue
                device = device.copy(
                    isGateway = device.ip == gateway,
                    isThisDevice = device.ip == localAddress,
                    vendor = device.vendor
                        ?: device.macAddress?.let { OuiDatabase.vendor(it) },
                )
                index[address] = DeviceFingerprinter.classify(device)
            }
            dirty.clear()
            index.values.toList()
        }
        _devices.value = snapshot
    }

    companion object {
        private const val PUBLISH_INTERVAL_MS = 300L

        /**
         * Ceiling on the addresses one sweep will walk.
         *
         * A /16 is 65k probes several times over; a range wider than this is
         * narrowed rather than refused, and the header shows what was actually
         * swept.
         */
        const val MAX_SWEEP_HOSTS = 4096

        /** Convenience factory for a scanner that owns its own coroutine scope. */
        fun standalone(context: Context): NetworkScanner =
            NetworkScanner(context, CoroutineScope(SupervisorJob()))
    }
}
