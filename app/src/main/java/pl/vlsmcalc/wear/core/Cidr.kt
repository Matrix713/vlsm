package pl.vlsmcalc.wear.core

enum class AddressType(val label: String) {
    PRIVATE("Prywatny (RFC 1918)"),
    PUBLIC("Publiczny"),
    LOOPBACK("Loopback"),
    LINK_LOCAL("Link-local (APIPA)"),
    CGNAT("CGNAT (RFC 6598)"),
    MULTICAST("Multicast"),
    RESERVED("Zarezerwowany"),
    THIS_NETWORK("Ta sieć (0/8)"),
    BROADCAST("Broadcast ograniczony"),
    DOCUMENTATION("Dokumentacja (TEST-NET)"),
}

/** Adres IPv4 z prefiksem. [address] nie musi być adresem sieci. */
data class Cidr(val address: Long, val prefix: Int) {
    init {
        require(address in 0..Ip.MAX) { "address" }
        require(prefix in 0..32) { "prefix" }
    }

    val mask: Long get() = Ip.mask(prefix)
    val wildcard: Long get() = Ip.wildcard(prefix)
    val network: Long get() = address and mask
    val broadcast: Long get() = network or wildcard
    val size: Long get() = Ip.blockSize(prefix)
    val usableHosts: Long get() = Ip.usableHosts(prefix)
    val firstHost: Long get() = if (prefix >= 31) network else network + 1
    val lastHost: Long get() = if (prefix >= 31) broadcast else broadcast - 1
    val isNetworkAddress: Boolean get() = address == network

    /** Ta sama sieć, ale z adresem znormalizowanym do adresu sieci. */
    fun normalized(): Cidr = if (isNetworkAddress) this else Cidr(network, prefix)

    operator fun contains(ip: Long): Boolean = ip in network..broadcast

    fun contains(other: Cidr): Boolean = other.network >= network && other.broadcast <= broadcast

    fun overlaps(other: Cidr): Boolean = network <= other.broadcast && other.network <= broadcast

    val ipClass: Char
        get() {
            val first = (address shr 24).toInt()
            return when {
                first < 128 -> 'A'
                first < 192 -> 'B'
                first < 224 -> 'C'
                first < 240 -> 'D'
                else -> 'E'
            }
        }

    val addressType: AddressType get() = typeOf(address)

    override fun toString(): String = "${Ip.format(address)}/$prefix"

    fun networkString(): String = "${Ip.format(network)}/$prefix"

    companion object {
        private fun block(text: String): Cidr = parse(text)!!

        private val SPECIAL: List<Pair<Cidr, AddressType>> by lazy {
            listOf(
                block("255.255.255.255/32") to AddressType.BROADCAST,
                block("0.0.0.0/8") to AddressType.THIS_NETWORK,
                block("10.0.0.0/8") to AddressType.PRIVATE,
                block("172.16.0.0/12") to AddressType.PRIVATE,
                block("192.168.0.0/16") to AddressType.PRIVATE,
                block("127.0.0.0/8") to AddressType.LOOPBACK,
                block("169.254.0.0/16") to AddressType.LINK_LOCAL,
                block("100.64.0.0/10") to AddressType.CGNAT,
                block("192.0.2.0/24") to AddressType.DOCUMENTATION,
                block("198.51.100.0/24") to AddressType.DOCUMENTATION,
                block("203.0.113.0/24") to AddressType.DOCUMENTATION,
                block("224.0.0.0/4") to AddressType.MULTICAST,
                block("240.0.0.0/4") to AddressType.RESERVED,
            )
        }

        fun typeOf(ip: Long): AddressType =
            SPECIAL.firstOrNull { ip in it.first }?.second ?: AddressType.PUBLIC

        /** Domyślny prefiks klasowy dla adresu (A=/8, B=/16, C=/24, pozostałe /32). */
        fun classfulPrefix(ip: Long): Int {
            val first = (ip shr 24).toInt()
            return when {
                first < 128 -> 8
                first < 192 -> 16
                first < 224 -> 24
                else -> 32
            }
        }

        /**
         * Parsuje "a.b.c.d/p", "a.b.c.d/m.m.m.m" lub "a.b.c.d".
         * Bez prefiksu używa [defaultPrefix] albo prefiksu klasowego, gdy ten jest null.
         */
        fun parse(text: String, defaultPrefix: Int? = null): Cidr? {
            val trimmed = text.trim()
            val slash = trimmed.indexOf('/')
            val ipPart = if (slash >= 0) trimmed.substring(0, slash) else trimmed
            val ip = Ip.parse(ipPart) ?: return null
            if (slash < 0) return Cidr(ip, defaultPrefix ?: classfulPrefix(ip))
            val rest = trimmed.substring(slash + 1)
            if (rest.isEmpty()) return null
            val prefix = if (rest.contains('.')) {
                val mask = Ip.parse(rest) ?: return null
                Ip.prefixOfMask(mask) ?: return null
            } else {
                if (rest.length > 2 || !rest.all { it.isDigit() }) return null
                rest.toInt().takeIf { it in 0..32 } ?: return null
            }
            return Cidr(ip, prefix)
        }
    }
}
