package pl.vlsmcalc.nettools.ios

import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip

data class GeneratedBlock(val title: String, val lines: List<String>)

/** Gotowe polecenia IOS dla podanej sieci – bez ręcznego liczenia maski i wildcard. */
object CommandGenerator {
    /** Zapis adresu z wildcard dla ACL (host / any / sieć wildcard). */
    fun aclAddress(c: Cidr): String = when (c.prefix) {
        32 -> "host ${Ip.format(c.address)}"
        0 -> "any"
        else -> "${Ip.format(c.network)} ${Ip.format(c.wildcard)}"
    }

    fun generate(input: Cidr): List<GeneratedBlock> {
        val c = input.normalized()
        val net = Ip.format(c.network)
        val mask = Ip.format(c.mask)
        val wc = Ip.format(c.wildcard)
        val gateway = Ip.format(c.firstHost)
        val blocks = mutableListOf<GeneratedBlock>()

        val ifAddress = if (input.isNetworkAddress && c.prefix < 31) gateway else Ip.format(input.address)
        blocks += GeneratedBlock("Interfejs", listOf("ip address $ifAddress $mask"))
        blocks += GeneratedBlock("OSPF", listOf("router ospf 1", " network $net $wc area 0"))
        blocks += GeneratedBlock("EIGRP", listOf("router eigrp 100", " network $net $wc"))
        blocks += GeneratedBlock("Trasa statyczna", listOf("ip route $net $mask <next-hop>"))
        blocks += GeneratedBlock(
            "ACL",
            listOf(
                "access-list 10 permit ${aclAddress(c)}",
                "access-list 100 permit ip ${aclAddress(c)} any",
            ),
        )
        if (c.prefix <= 30) {
            val lastExcluded = minOf(c.firstHost + 9, c.lastHost)
            blocks += GeneratedBlock(
                "DHCP",
                listOf(
                    "ip dhcp excluded-address $gateway ${Ip.format(lastExcluded)}",
                    "ip dhcp pool LAN",
                    " network $net $mask",
                    " default-router $gateway",
                    " dns-server 8.8.8.8",
                ),
            )
            blocks += GeneratedBlock(
                "PAT",
                listOf(
                    "access-list 1 permit ${aclAddress(c)}",
                    "ip nat inside source list 1 interface <WAN> overload",
                ),
            )
        }
        blocks += GeneratedBlock("BGP", listOf("router bgp <AS>", " network $net mask $mask"))
        blocks += GeneratedBlock("Prefix-list", listOf("ip prefix-list LISTA permit ${c.networkString()}"))
        return blocks
    }
}
