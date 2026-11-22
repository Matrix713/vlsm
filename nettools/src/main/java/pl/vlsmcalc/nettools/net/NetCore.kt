package pl.vlsmcalc.nettools.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

/** Czysta logika narzędzi sieciowych (testowalna na JVM). */

enum class PortState { OPEN, CLOSED, FILTERED }

data class PortResult(val port: Int, val state: PortState, val millis: Long)

object Ports {
    val SERVICES = mapOf(
        20 to "FTP-data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        67 to "DHCP", 69 to "TFTP", 80 to "HTTP", 110 to "POP3", 123 to "NTP", 135 to "RPC",
        139 to "NetBIOS", 143 to "IMAP", 161 to "SNMP", 179 to "BGP", 389 to "LDAP", 443 to "HTTPS",
        445 to "SMB", 465 to "SMTPS", 514 to "Syslog", 554 to "RTSP", 587 to "SMTP sub", 636 to "LDAPS",
        830 to "NETCONF", 993 to "IMAPS", 995 to "POP3S", 1433 to "MSSQL", 1521 to "Oracle",
        1723 to "PPTP", 1883 to "MQTT", 2000 to "SCCP", 3306 to "MySQL", 3389 to "RDP",
        5060 to "SIP", 5432 to "PostgreSQL", 5900 to "VNC", 6379 to "Redis", 8000 to "HTTP-alt",
        8080 to "HTTP-proxy", 8291 to "Winbox", 8443 to "HTTPS-alt", 8883 to "MQTTS", 9000 to "HTTP-alt",
        9100 to "Drukarka", 62078 to "iPhone",
    )

    val COMMON = listOf(21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 993, 995, 1433, 3306, 3389, 5900, 8080, 8443)
    val NETWORK_GEAR = listOf(22, 23, 80, 161, 179, 443, 514, 830, 2000, 8080, 8291, 8443)
    val WEB = listOf(80, 443, 8000, 8080, 8443, 9000)

    fun name(port: Int): String = SERVICES[port] ?: ""
}

object Probe {
    /**
     * Próba połączenia TCP. Odmowa (RST) oznacza, że host żyje, ale port jest zamknięty;
     * brak odpowiedzi – port filtrowany lub host nieosiągalny.
     */
    fun tcp(address: InetAddress, port: Int, timeoutMs: Int): PortResult {
        val start = System.nanoTime()
        val state = try {
            Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }
            PortState.OPEN
        } catch (_: ConnectException) {
            PortState.CLOSED
        } catch (_: SocketTimeoutException) {
            PortState.FILTERED
        } catch (_: NoRouteToHostException) {
            PortState.FILTERED
        } catch (_: IOException) {
            PortState.FILTERED
        }
        return PortResult(port, state, (System.nanoTime() - start) / 1_000_000)
    }

    /** Skanuje porty równolegle; [onResult] wywoływane po każdym porcie (postęp). */
    suspend fun scanPorts(
        address: InetAddress,
        ports: List<Int>,
        timeoutMs: Int = 800,
        parallelism: Int = 32,
        onResult: (PortResult) -> Unit = {},
    ): List<PortResult> = coroutineScope {
        val gate = Semaphore(parallelism)
        ports.map { port ->
            async(Dispatchers.IO) {
                gate.withPermit { tcp(address, port, timeoutMs).also(onResult) }
            }
        }.awaitAll().sortedBy { it.port }
    }

    val DISCOVERY_PORTS = listOf(80, 443, 22, 445, 139, 53, 8080, 62078, 554, 23)

    /** Czy host odpowiada: dowolny port otwarty LUB aktywnie odrzucony. Zwraca otwarte porty. */
    suspend fun discover(address: InetAddress, timeoutMs: Int = 400, ports: List<Int> = DISCOVERY_PORTS): Pair<Boolean, List<Int>> =
        coroutineScope {
            val results = ports.map { p -> async(Dispatchers.IO) { tcp(address, p, timeoutMs) } }.awaitAll()
            val alive = results.any { it.state != PortState.FILTERED }
            alive to results.filter { it.state == PortState.OPEN }.map { it.port }.sorted()
        }
}

/** Adres MAC i pakiet Wake-on-LAN. */
object Mac {
    /** Akceptuje aa:bb:cc:dd:ee:ff, aa-bb-..., aabb.ccdd.eeff (Cisco) i aabbccddeeff. */
    fun parse(text: String): ByteArray? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val hex = trimmed.replace(Regex("[:.\\-\\s]"), "")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val valid = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$|^([0-9A-Fa-f]{4}\\.){2}[0-9A-Fa-f]{4}$|^[0-9A-Fa-f]{12}$")
        if (!valid.matches(trimmed)) return null
        return ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun format(mac: ByteArray): String = mac.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    fun formatCisco(mac: ByteArray): String {
        val hex = mac.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return hex.chunked(4).joinToString(".")
    }

    /** 6 × 0xFF + 16 × MAC = 102 bajty. */
    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6)
        val packet = ByteArray(102) { 0xFF.toByte() }
        for (i in 0 until 16) System.arraycopy(mac, 0, packet, 6 + i * 6, 6)
        return packet
    }
}

/** Parsowanie wyniku systemowego polecenia ping. */
object PingParser {
    data class Reply(val seq: Int, val ttl: Int?, val millis: Double)
    data class Summary(val sent: Int, val received: Int)

    private val reply = Regex("""icmp_seq=(\d+)(?:\s+ttl=(\d+))?\s+time=([\d.]+)\s*ms""")
    private val summary = Regex("""(\d+) packets transmitted, (\d+) (?:packets )?received""")

    fun reply(line: String): Reply? = reply.find(line)?.let {
        Reply(it.groupValues[1].toInt(), it.groupValues[2].toIntOrNull(), it.groupValues[3].toDouble())
    }

    fun summary(line: String): Summary? = summary.find(line)?.let {
        Summary(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }

    fun isError(line: String): Boolean {
        val l = line.lowercase()
        return "unreachable" in l || "unknown host" in l || "permission denied" in l ||
            "operation not permitted" in l || "network is unreachable" in l || "bad address" in l
    }
}

data class PingStats(val sent: Int, val received: Int, val min: Double?, val avg: Double?, val max: Double?) {
    val lossPercent: Int get() = if (sent == 0) 0 else ((sent - received) * 100 + sent / 2) / sent

    companion object {
        fun of(sent: Int, times: List<Double>) = PingStats(
            sent, times.size, times.minOrNull(), times.takeIf { it.isNotEmpty() }?.average(), times.maxOrNull(),
        )
    }
}
