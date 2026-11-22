package pl.vlsmcalc.nettools

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.vlsmcalc.nettools.net.Mac
import pl.vlsmcalc.nettools.net.NetInfo
import pl.vlsmcalc.nettools.net.NetManager
import pl.vlsmcalc.nettools.net.PingParser
import pl.vlsmcalc.nettools.net.PingStats
import pl.vlsmcalc.nettools.net.PortResult
import pl.vlsmcalc.nettools.net.PortState
import pl.vlsmcalc.nettools.net.Ports
import pl.vlsmcalc.nettools.net.Probe
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.ui.InputRequest
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

data class PingEntry(val seq: Int, val millis: Double?, val text: String, val ok: Boolean)

data class PingUi(
    val running: Boolean = false,
    val target: String = "",
    val entries: List<PingEntry> = emptyList(),
    val stats: PingStats? = null,
    val error: String? = null,
)

data class PortsUi(
    val running: Boolean = false,
    val target: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val results: List<PortResult> = emptyList(),
    val error: String? = null,
) {
    val open: List<PortResult> get() = results.filter { it.state == PortState.OPEN }.sortedBy { it.port }
}

data class DnsUi(
    val running: Boolean = false,
    val query: String = "",
    val v4: List<String> = emptyList(),
    val v6: List<String> = emptyList(),
    val ptr: String? = null,
    val millis: Long? = null,
    val error: String? = null,
)

data class LanHost(val ip: Long, val name: String?, val open: List<Int>, val isSelf: Boolean, val isGateway: Boolean)

data class LanUi(
    val running: Boolean = false,
    val subnet: Cidr? = null,
    val done: Int = 0,
    val total: Int = 0,
    val hosts: List<LanHost> = emptyList(),
    val error: String? = null,
)

enum class HostTarget { PING, PORTS, DNS }

class NetViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = NetStorage(app)
    val net = NetManager(app)

    var prefs by mutableStateOf(storage.load())
        private set
    var input: InputRequest? by mutableStateOf(null)
        private set
    var searchQuery by mutableStateOf("")

    var netInfo by mutableStateOf<NetInfo?>(null)
        private set
    var wifiForced by mutableStateOf(false)
        private set
    var wifiBusy by mutableStateOf(false)
        private set
    var wifiMessage by mutableStateOf<String?>(null)
        private set

    var ping by mutableStateOf(PingUi())
        private set
    var ports by mutableStateOf(PortsUi())
        private set
    var dns by mutableStateOf(DnsUi())
        private set
    var lan by mutableStateOf(LanUi())
        private set
    var wolStatus by mutableStateOf<String?>(null)
        private set

    private var pingJob: Job? = null
    private var pingProcess: Process? = null
    private var portsJob: Job? = null
    private var lanJob: Job? = null

    private fun update(block: NetPrefs.() -> NetPrefs) {
        prefs = prefs.block()
        storage.save(prefs)
    }

    fun openInput(request: InputRequest) {
        input = request
    }

    // ---------- Ulubione ----------

    fun isFavorite(id: String) = id in prefs.favorites

    fun toggleFavorite(id: String) = update {
        copy(favorites = if (id in favorites) favorites - id else listOf(id) + favorites)
    }

    // ---------- Hosty ----------

    fun hostFor(target: HostTarget) = when (target) {
        HostTarget.PING -> prefs.pingHost
        HostTarget.PORTS -> prefs.portsHost
        HostTarget.DNS -> prefs.dnsHost
    }

    fun setHost(target: HostTarget, host: String) {
        val h = host.trim()
        if (h.isEmpty()) return
        update {
            val recent = (listOf(h) + recentHosts.filter { it != h }).take(8)
            when (target) {
                HostTarget.PING -> copy(pingHost = h, recentHosts = recent)
                HostTarget.PORTS -> copy(portsHost = h, recentHosts = recent)
                HostTarget.DNS -> copy(dnsHost = h, recentHosts = recent)
            }
        }
    }

    fun setGeneratorNet(text: String) = update { copy(generatorNet = text) }
    fun cyclePingMode() = update { copy(pingMode = PingMode.entries[(pingMode.ordinal + 1) % PingMode.entries.size]) }
    fun cyclePingCount() = update {
        val options = listOf(5, 10, 30, 0)
        copy(pingCount = options[(options.indexOf(pingCount) + 1).mod(options.size)])
    }
    fun setTcpPort(p: Int) = update { copy(tcpPort = p) }
    fun cyclePortPreset() = update { copy(portPreset = PortPreset.entries[(portPreset.ordinal + 1) % PortPreset.entries.size]) }
    fun setSinglePort(p: Int) = update { copy(singlePort = p) }

    // ---------- Moja sieć ----------

    fun refreshInfo() {
        netInfo = runCatching { net.info() }.getOrNull()
    }

    fun setForceWifi(on: Boolean) {
        if (!on) {
            net.releaseWifi()
            wifiForced = false
            wifiMessage = null
            refreshInfo()
            return
        }
        viewModelScope.launch { ensureWifi() }
    }

    /** Włącza i przypina Wi-Fi. Zwraca false, gdy się nie udało. */
    private suspend fun ensureWifi(): Boolean {
        if (wifiForced && net.wifiNetwork != null) return true
        wifiBusy = true
        wifiMessage = "Łączenie z Wi-Fi…"
        val network = net.acquireWifi()
        wifiBusy = false
        wifiForced = network != null
        wifiMessage = if (network == null) "Brak Wi-Fi. Włącz Wi-Fi w ustawieniach zegarka." else null
        refreshInfo()
        return network != null
    }

    // ---------- Ping ----------

    fun startPing() {
        stopPing()
        val host = prefs.pingHost
        ping = PingUi(running = true, target = host)
        pingJob = viewModelScope.launch {
            try {
                if (prefs.pingMode == PingMode.ICMP) icmpPing(host) else tcpPing(host)
            } finally {
                ping = ping.copy(running = false)
            }
        }
    }

    fun stopPing() {
        pingProcess?.destroy()
        pingProcess = null
        pingJob?.cancel()
        pingJob = null
        if (ping.running) ping = ping.copy(running = false)
    }

    private fun addPing(entry: PingEntry, sent: Int) {
        val entries = (ping.entries + entry).takeLast(200)
        ping = ping.copy(entries = entries, stats = PingStats.of(sent, entries.mapNotNull { it.millis }))
    }

    private suspend fun icmpPing(host: String) = withContext(Dispatchers.IO) {
        val args = mutableListOf("/system/bin/ping", "-W", "2")
        if (prefs.pingCount > 0) args += listOf("-c", prefs.pingCount.toString())
        args += host
        val process = try {
            ProcessBuilder(args).redirectErrorStream(true).start()
        } catch (e: Exception) {
            ping = ping.copy(error = "Systemowy ping niedostępny – użyj trybu TCP")
            return@withContext
        }
        pingProcess = process
        var maxSeq = 0
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    ensureActive()
                    val reply = PingParser.reply(line)
                    val summary = PingParser.summary(line)
                    when {
                        reply != null -> {
                            maxSeq = maxOf(maxSeq, reply.seq)
                            addPing(PingEntry(reply.seq, reply.millis, "ttl=${reply.ttl ?: "?"}", true), maxSeq)
                        }
                        summary != null -> ping = ping.copy(
                            stats = PingStats.of(summary.sent, ping.entries.mapNotNull { it.millis }),
                        )
                        PingParser.isError(line) -> {
                            maxSeq++
                            addPing(PingEntry(maxSeq, null, line.substringAfter(": ").take(60), false), maxSeq)
                        }
                    }
                }
            }
            if (ping.entries.isEmpty() && isActive) {
                ping = ping.copy(error = "Brak odpowiedzi ICMP. Spróbuj TCP lub wymuś Wi-Fi.")
            }
        } catch (_: Exception) {
            // Przerwane przez użytkownika
        } finally {
            process.destroy()
        }
    }

    private suspend fun tcpPing(host: String) {
        val address = try {
            net.resolve(host).firstOrNull { it is Inet4Address } ?: net.resolve(host).first()
        } catch (e: Exception) {
            ping = ping.copy(error = "Nie znaleziono hosta: $host")
            return
        }
        ping = ping.copy(target = "$host (${address.hostAddress}:${prefs.tcpPort})")
        var seq = 0
        while (prefs.pingCount == 0 || seq < prefs.pingCount) {
            seq++
            val r = withContext(Dispatchers.IO) { Probe.tcp(address, prefs.tcpPort, 2000) }
            val entry = when (r.state) {
                PortState.OPEN -> PingEntry(seq, r.millis.toDouble(), "port otwarty", true)
                PortState.CLOSED -> PingEntry(seq, r.millis.toDouble(), "RST (port zamknięty)", true)
                PortState.FILTERED -> PingEntry(seq, null, "timeout", false)
            }
            addPing(entry, seq)
            delay(1000)
        }
    }

    // ---------- Porty ----------

    fun portsToScan(): List<Int> = when (prefs.portPreset) {
        PortPreset.COMMON -> Ports.COMMON
        PortPreset.NETWORK -> Ports.NETWORK_GEAR
        PortPreset.WEB -> Ports.WEB
        PortPreset.WELL_KNOWN -> (1..1024).toList()
        PortPreset.SINGLE -> listOf(prefs.singlePort)
    }

    fun startPortScan() {
        portsJob?.cancel()
        val host = prefs.portsHost
        val list = portsToScan()
        ports = PortsUi(running = true, target = host, total = list.size)
        portsJob = viewModelScope.launch {
            try {
                val address = try {
                    net.resolve(host).first()
                } catch (e: Exception) {
                    ports = ports.copy(error = "Nie znaleziono hosta: $host")
                    return@launch
                }
                ports = ports.copy(target = "$host (${address.hostAddress})")
                // Wyniki spływają z wielu wątków – zbieramy je w bezpiecznej liście i odświeżamy UI na głównym wątku
                val acc = java.util.Collections.synchronizedList(mutableListOf<PortResult>())
                val results = Probe.scanPorts(address, list, timeoutMs = 800, parallelism = 48) { r ->
                    acc.add(r)
                    viewModelScope.launch(Dispatchers.Main) {
                        val snapshot = synchronized(acc) { acc.toList() }
                        if (ports.running) ports = ports.copy(done = snapshot.size, results = snapshot)
                    }
                }
                ports = ports.copy(done = list.size, results = results)
            } finally {
                ports = ports.copy(running = false)
            }
        }
    }

    fun stopPortScan() {
        portsJob?.cancel()
        ports = ports.copy(running = false)
    }

    // ---------- DNS ----------

    fun runDns() {
        val q = prefs.dnsHost
        dns = DnsUi(running = true, query = q)
        viewModelScope.launch {
            val start = System.nanoTime()
            try {
                if (Ip.parse(q) != null) {
                    val name = withContext(Dispatchers.IO) {
                        withTimeoutOrNull(5000) { InetAddress.getByName(q).canonicalHostName }
                    }
                    dns = dns.copy(ptr = if (name == null || name == q) "brak rekordu PTR" else name)
                } else {
                    val all = net.resolve(q)
                    dns = dns.copy(
                        v4 = all.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                        v6 = all.filter { it !is Inet4Address }.mapNotNull { it.hostAddress?.substringBefore('%') },
                    )
                }
                dns = dns.copy(millis = (System.nanoTime() - start) / 1_000_000)
            } catch (e: Exception) {
                dns = dns.copy(error = "Nie rozwiązano: $q")
            } finally {
                dns = dns.copy(running = false)
            }
        }
    }

    // ---------- Skaner LAN ----------

    fun startLanScan() {
        lanJob?.cancel()
        lan = LanUi(running = true)
        lanJob = viewModelScope.launch {
            try {
                if (!ensureWifi()) {
                    lan = lan.copy(error = "Skaner LAN wymaga Wi-Fi. Włącz Wi-Fi w zegarku.")
                    return@launch
                }
                val info = net.info(net.wifiNetwork)
                val own = info?.ipv4?.firstOrNull()
                if (own == null) {
                    lan = lan.copy(error = "Brak adresu IPv4 w sieci Wi-Fi")
                    return@launch
                }
                // Duże sieci skanujemy w obrębie /24 wokół własnego adresu
                val subnet = if (own.prefix < 24) Cidr(own.address, 24).normalized() else own.normalized()
                val gateway = info.gateway?.let { Ip.parse(it) }
                val targets = (subnet.firstHost..subnet.lastHost).toList()
                lan = lan.copy(subnet = subnet, total = targets.size)
                val gate = Semaphore(32)
                val found = targets.map { ip ->
                    async {
                        gate.withPermit {
                            val host = if (ip == own.address) {
                                LanHost(ip, "ten zegarek", emptyList(), isSelf = true, isGateway = false)
                            } else {
                                val addr = InetAddress.getByAddress(Ip.octets(ip).map { it.toByte() }.toByteArray())
                                var (alive, open) = Probe.discover(addr)
                                if (!alive) alive = withContext(Dispatchers.IO) { runCatching { addr.isReachable(400) }.getOrDefault(false) }
                                if (alive || ip == gateway) LanHost(ip, null, open, false, ip == gateway) else null
                            }
                            withContext(Dispatchers.Main) {
                                lan = lan.copy(
                                    done = lan.done + 1,
                                    hosts = if (host != null) (lan.hosts + host).sortedBy { it.ip } else lan.hosts,
                                )
                            }
                            host
                        }
                    }
                }.awaitAll().filterNotNull()
                // Nazwy (odwrotny DNS / mDNS z routera) – równolegle, z limitem czasu
                val names = Semaphore(8)
                val named = found.map { h ->
                    async {
                        if (h.isSelf) h else names.withPermit {
                            val name = withContext(Dispatchers.IO) {
                                withTimeoutOrNull(2000) {
                                    val ip = Ip.format(h.ip)
                                    InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                                }
                            }
                            h.copy(name = name)
                        }
                    }
                }.awaitAll()
                lan = lan.copy(hosts = named.sortedBy { it.ip })
            } finally {
                lan = lan.copy(running = false)
            }
        }
    }

    fun stopLanScan() {
        lanJob?.cancel()
        lan = lan.copy(running = false)
    }

    // ---------- Wake-on-LAN ----------

    fun addWol(name: String, mac: String): String? {
        val bytes = Mac.parse(mac) ?: return "Błędny MAC"
        update { copy(wol = wol + WolDevice(name.ifBlank { "Urządzenie ${wol.size + 1}" }, Mac.format(bytes))) }
        return null
    }

    fun removeWol(index: Int) = update { copy(wol = wol.filterIndexed { i, _ -> i != index }) }

    fun sendWol(device: WolDevice) {
        wolStatus = "Wysyłanie…"
        viewModelScope.launch {
            val mac = Mac.parse(device.mac)
            if (mac == null) {
                wolStatus = "Błędny MAC"
                return@launch
            }
            ensureWifi()
            val own = net.info()?.ipv4?.firstOrNull()
            val targets = buildSet {
                add("255.255.255.255")
                own?.let { add(Ip.format(it.broadcast)) }
            }
            wolStatus = try {
                withContext(Dispatchers.IO) {
                    val packet = Mac.magicPacket(mac)
                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        repeat(3) {
                            for (t in targets) for (port in listOf(9, 7)) {
                                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), port))
                            }
                        }
                    }
                }
                "Wysłano do ${device.name} ✓"
            } catch (e: Exception) {
                "Błąd wysyłania: ${e.message ?: "brak sieci"}"
            }
        }
    }

    override fun onCleared() {
        stopPing()
        net.releaseWifi()
    }
}
