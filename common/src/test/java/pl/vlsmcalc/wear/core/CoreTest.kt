package pl.vlsmcalc.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpTest {
    @Test fun parseAndFormat() {
        assertEquals("192.168.1.10", Ip.format(Ip.parse("192.168.1.10")!!))
        assertEquals(0L, Ip.parse("0.0.0.0"))
        assertEquals(Ip.MAX, Ip.parse("255.255.255.255"))
        assertNull(Ip.parse("256.1.1.1"))
        assertNull(Ip.parse("1.1.1"))
        assertNull(Ip.parse("1.1.1.1.1"))
        assertNull(Ip.parse("1..1.1"))
        assertNull(Ip.parse("a.b.c.d"))
        assertNull(Ip.parse("1234.1.1.1"))
    }

    @Test fun masks() {
        assertEquals("255.255.255.0", Ip.format(Ip.mask(24)))
        assertEquals("255.255.255.192", Ip.format(Ip.mask(26)))
        assertEquals("0.0.0.0", Ip.format(Ip.mask(0)))
        assertEquals("255.255.255.255", Ip.format(Ip.mask(32)))
        assertEquals("0.0.0.63", Ip.format(Ip.wildcard(26)))
        assertEquals(26, Ip.prefixOfMask(Ip.parse("255.255.255.192")!!))
        assertEquals(0, Ip.prefixOfMask(0))
        assertEquals(32, Ip.prefixOfMask(Ip.MAX))
        assertNull(Ip.prefixOfMask(Ip.parse("255.0.255.0")!!))
    }

    @Test fun prefixForHosts() {
        assertEquals(30, Ip.prefixForHosts(1))
        assertEquals(30, Ip.prefixForHosts(2))
        assertEquals(29, Ip.prefixForHosts(3))
        assertEquals(26, Ip.prefixForHosts(62))
        assertEquals(25, Ip.prefixForHosts(63))
        assertEquals(24, Ip.prefixForHosts(254))
        assertEquals(23, Ip.prefixForHosts(255))
        assertEquals(32, Ip.prefixForHosts(1, allowSmall = true))
        assertEquals(31, Ip.prefixForHosts(2, allowSmall = true))
        assertEquals(29, Ip.prefixForHosts(3, allowSmall = true))
        assertNull(Ip.prefixForHosts(0))
    }

    @Test fun shortForm() {
        val net = Ip.parse("192.168.1.0")!!
        assertEquals(".62", Ip.shortForm(Ip.parse("192.168.1.62")!!, net))
        assertEquals(".255.254", Ip.shortForm(Ip.parse("10.0.255.254")!!, Ip.parse("10.0.0.0")!!))
        assertEquals("11.0.0.1", Ip.shortForm(Ip.parse("11.0.0.1")!!, Ip.parse("10.0.0.0")!!))
    }

    @Test fun binary() {
        assertEquals("11000000.10101000.00000001.00000000", Ip.toBinary(Ip.parse("192.168.1.0")!!))
        assertEquals("11111111.11111111.11111111.11|000000", Ip.toBinaryWithPrefix(Ip.mask(26), 26))
        assertEquals("11111111.11111111.11111111|00000000", Ip.toBinaryWithPrefix(Ip.mask(24), 24))
    }
}

class CidrTest {
    @Test fun parseVariants() {
        assertEquals(Cidr(Ip.parse("192.168.1.0")!!, 24), Cidr.parse("192.168.1.0/24"))
        assertEquals(26, Cidr.parse("10.0.0.0/255.255.255.192")!!.prefix)
        assertEquals(8, Cidr.parse("10.1.2.3")!!.prefix)
        assertEquals(16, Cidr.parse("172.16.5.4")!!.prefix)
        assertEquals(24, Cidr.parse("200.1.1.1")!!.prefix)
        assertEquals(20, Cidr.parse("10.1.2.3", defaultPrefix = 20)!!.prefix)
        assertNull(Cidr.parse("10.0.0.0/33"))
        assertNull(Cidr.parse("10.0.0.0/"))
        assertNull(Cidr.parse("10.0.0.0/255.0.255.0"))
    }

    @Test fun details() {
        val c = Cidr.parse("192.168.10.77/26")!!
        assertEquals("192.168.10.64", Ip.format(c.network))
        assertEquals("192.168.10.127", Ip.format(c.broadcast))
        assertEquals("192.168.10.65", Ip.format(c.firstHost))
        assertEquals("192.168.10.126", Ip.format(c.lastHost))
        assertEquals(62L, c.usableHosts)
        assertEquals(64L, c.size)
        assertEquals('C', c.ipClass)
        assertEquals(AddressType.PRIVATE, c.addressType)
        assertFalse(c.isNetworkAddress)
    }

    @Test fun edgePrefixes() {
        val p31 = Cidr.parse("10.0.0.0/31")!!
        assertEquals(2L, p31.usableHosts)
        assertEquals(p31.network, p31.firstHost)
        assertEquals(p31.broadcast, p31.lastHost)
        val p32 = Cidr.parse("10.0.0.5/32")!!
        assertEquals(1L, p32.usableHosts)
        val all = Cidr.parse("0.0.0.0/0")!!
        assertEquals(1L shl 32, all.size)
        assertEquals(Ip.MAX, all.broadcast)
    }

    @Test fun types() {
        assertEquals(AddressType.PUBLIC, Cidr.typeOf(Ip.parse("8.8.8.8")!!))
        assertEquals(AddressType.LOOPBACK, Cidr.typeOf(Ip.parse("127.0.0.1")!!))
        assertEquals(AddressType.CGNAT, Cidr.typeOf(Ip.parse("100.100.0.1")!!))
        assertEquals(AddressType.PRIVATE, Cidr.typeOf(Ip.parse("172.31.255.255")!!))
        assertEquals(AddressType.PUBLIC, Cidr.typeOf(Ip.parse("172.32.0.0")!!))
        assertEquals(AddressType.MULTICAST, Cidr.typeOf(Ip.parse("239.1.1.1")!!))
    }
}

class VlsmTest {
    private fun req(vararg hosts: Long) = hosts.mapIndexed { i, h -> SubnetRequest("S$i", h) }

    @Test fun classicExample() {
        val base = Cidr.parse("192.168.1.0/24")!!
        val out = Vlsm.calculate(base, req(20, 50, 2, 10)) as VlsmOutcome.Success
        val r = out.result
        val byName = r.allocations.associate { it.request.name to it.subnet.toString() }
        assertEquals("192.168.1.0/26", byName["S1"])
        assertEquals("192.168.1.64/27", byName["S0"])
        assertEquals("192.168.1.96/28", byName["S3"])
        assertEquals("192.168.1.112/30", byName["S2"])
        assertEquals(116L, r.usedAddresses)
        assertEquals(listOf("192.168.1.116/30", "192.168.1.120/29", "192.168.1.128/25"), r.free.map { it.toString() })
        // Wolne + użyte = cała sieć
        assertEquals(256L, r.usedAddresses + r.free.sumOf { it.size })
    }

    @Test fun baseIsNormalized() {
        val out = Vlsm.calculate(Cidr.parse("10.0.0.77/24")!!, req(100)) as VlsmOutcome.Success
        assertEquals("10.0.0.0/25", out.result.allocations[0].subnet.toString())
    }

    @Test fun doesNotFitSuggestsPrefix() {
        val out = Vlsm.calculate(Cidr.parse("192.168.1.0/24")!!, req(200, 100)) as VlsmOutcome.DoesNotFit
        assertEquals(23, out.suggestedPrefix)
        assertEquals(384L, out.neededAddresses)
        assertEquals("S1", out.failedAt)
    }

    @Test fun keepOrderAligns() {
        val out = Vlsm.calculate(Cidr.parse("10.0.0.0/24")!!, req(2, 100), largestFirst = false) as VlsmOutcome.Success
        val list = out.result.allocations
        assertEquals("10.0.0.0/30", list[0].subnet.toString())
        assertEquals("10.0.0.128/25", list[1].subnet.toString())
        assertEquals(listOf("10.0.0.4/30", "10.0.0.8/29", "10.0.0.16/28", "10.0.0.32/27", "10.0.0.64/26"),
            out.result.free.map { it.toString() })
    }

    @Test fun exactFit() {
        val out = Vlsm.calculate(Cidr.parse("10.0.0.0/24")!!, req(126, 62, 30, 14, 6, 2, 2))
        assertTrue(out is VlsmOutcome.Success)
        assertTrue((out as VlsmOutcome.Success).result.free.isEmpty())
    }

    @Test fun p2pMode() {
        val out = Vlsm.calculate(Cidr.parse("10.0.0.0/29")!!, req(2, 2, 1), allowSmall = true) as VlsmOutcome.Success
        assertEquals(listOf("10.0.0.0/31", "10.0.0.2/31", "10.0.0.4/32"), out.result.allocations.map { it.subnet.toString() })
    }

    @Test fun invalid() {
        assertTrue(Vlsm.calculate(Cidr.parse("10.0.0.0/24")!!, emptyList()) is VlsmOutcome.Invalid)
        assertTrue(Vlsm.calculate(Cidr.parse("10.0.0.0/24")!!, req(0)) is VlsmOutcome.Invalid)
    }

    @Test fun hugeRequests() {
        val out = Vlsm.calculate(Cidr.parse("10.0.0.0/8")!!, req(20_000_000)) as VlsmOutcome.DoesNotFit
        assertEquals(7, out.suggestedPrefix)
        // Więcej hostów niż w całym IPv4 -> dane nieprawidłowe
        assertTrue(Vlsm.calculate(Cidr.parse("10.0.0.0/8")!!, req(5_000_000_000)) is VlsmOutcome.Invalid)
    }
}

class RangesFlsmSummaryTest {
    @Test fun rangeToCidrs() {
        val r = Ranges.toCidrs(Ip.parse("10.0.0.1")!!, Ip.parse("10.0.0.10")!!).map { it.toString() }
        assertEquals(listOf("10.0.0.1/32", "10.0.0.2/31", "10.0.0.4/30", "10.0.0.8/31", "10.0.0.10/32"), r)
        assertEquals(listOf("0.0.0.0/0"), Ranges.toCidrs(0, Ip.MAX).map { it.toString() })
    }

    @Test fun flsm() {
        val base = Cidr.parse("192.168.0.0/24")!!
        val byCount = (Flsm.plan(base, FlsmMode.BY_COUNT, 6) as Flsm.Outcome.Ok).plan
        assertEquals(27, byCount.newPrefix)
        assertEquals(8L, byCount.count)
        assertEquals("192.168.0.224/27", byCount.subnet(7).toString())
        val byHosts = (Flsm.plan(base, FlsmMode.BY_HOSTS, 50) as Flsm.Outcome.Ok).plan
        assertEquals(26, byHosts.newPrefix)
        assertTrue(Flsm.plan(base, FlsmMode.BY_PREFIX, 20) is Flsm.Outcome.Error)
        assertTrue(Flsm.plan(base, FlsmMode.BY_COUNT, 1000) is Flsm.Outcome.Error)
        assertEquals(24, (Flsm.plan(base, FlsmMode.BY_COUNT, 1) as Flsm.Outcome.Ok).plan.newPrefix)
    }

    @Test fun summary() {
        val nets = listOf("172.16.0.0/24", "172.16.1.0/24", "172.16.2.0/24", "172.16.3.0/24").map { Cidr.parse(it)!! }
        val s = Summarizer.summarize(nets)!!
        assertEquals("172.16.0.0/22", s.summary.toString())
        assertEquals(listOf("172.16.0.0/22"), s.aggregated.map { it.toString() })
        assertEquals(0L, s.extraAddresses)

        val gap = Summarizer.summarize(listOf(Cidr.parse("10.0.0.0/24")!!, Cidr.parse("10.0.3.0/24")!!))!!
        assertEquals("10.0.0.0/22", gap.summary.toString())
        assertEquals(512L, gap.extraAddresses)
        assertEquals(listOf("10.0.0.0/24", "10.0.3.0/24"), gap.aggregated.map { it.toString() })

        val overlap = Summarizer.summarize(listOf(Cidr.parse("10.0.0.0/16")!!, Cidr.parse("10.0.5.0/24")!!))!!
        assertEquals("10.0.0.0/16", overlap.summary.toString())
        assertEquals(listOf("10.0.0.0/16"), overlap.aggregated.map { it.toString() })
    }
}
