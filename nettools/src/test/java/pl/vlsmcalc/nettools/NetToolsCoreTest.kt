package pl.vlsmcalc.nettools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vlsmcalc.nettools.ios.CommandGenerator
import pl.vlsmcalc.nettools.ios.IosDatabase
import pl.vlsmcalc.nettools.ios.IosSearch
import pl.vlsmcalc.nettools.ios.Mode
import pl.vlsmcalc.nettools.net.Mac
import pl.vlsmcalc.nettools.net.PingParser
import pl.vlsmcalc.nettools.net.PingStats
import pl.vlsmcalc.nettools.net.PortState
import pl.vlsmcalc.nettools.net.Probe
import pl.vlsmcalc.wear.core.Cidr
import java.net.InetAddress
import java.net.ServerSocket

class IosDatabaseTest {
    @Test fun everyCategoryHasCommands() {
        IosDatabase.categories.forEach { assertTrue("pusta kategoria ${it.id}", IosDatabase.inCategory(it.id).isNotEmpty()) }
    }

    @Test fun everyCommandHasKnownCategory() {
        val ids = IosDatabase.categories.map { it.id }.toSet()
        IosDatabase.commands.forEach { assertTrue("${it.syntax} -> ${it.category}", it.category in ids) }
    }

    @Test fun idsAreUnique() {
        val dupes = IosDatabase.commands.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("Duplikaty: $dupes", dupes.isEmpty())
    }

    @Test fun descriptionsPresent() {
        IosDatabase.commands.forEach {
            assertTrue(it.syntax, it.title.isNotBlank() && it.description.isNotBlank())
        }
        assertTrue(IosDatabase.commands.size > 200)
    }

    @Test fun templatesAreMultiline() {
        val templates = IosDatabase.inCategory("configs")
        assertTrue(templates.all { it.isTemplate })
        val ssh = templates.first { it.title == "Dostęp SSH" }
        assertTrue(ssh.syntax.lines().contains(" transport input ssh"))
        assertEquals(Mode.CONF, ssh.mode)
    }
}

class IosSearchTest {
    @Test fun abbreviations() {
        assertTrue(IosSearch.matchesAbbreviation("sh ip int br", "show ip interface brief"))
        assertTrue(IosSearch.matchesAbbreviation("conf t", "configure terminal"))
        assertFalse(IosSearch.matchesAbbreviation("ip int", "show ip interface brief"))
        assertEquals("show ip interface brief", IosSearch.search("sh ip int br").first().syntax)
        assertEquals("configure terminal", IosSearch.search("conf t").first().syntax)
    }

    @Test fun polishDescriptionsWithoutDiacritics() {
        val results = IosSearch.search("trasa domyslna").map { it.syntax }
        assertTrue(results.toString(), "ip route 0.0.0.0 0.0.0.0 <next-hop>" in results)
        assertTrue(IosSearch.search("haslo").isNotEmpty())
    }

    @Test fun keywords() {
        assertTrue(IosSearch.search("overload").any { it.syntax.contains("overload") })
        assertTrue(IosSearch.search("portfast").first().syntax.contains("portfast"))
        assertTrue(IosSearch.search("").isEmpty())
        assertTrue(IosSearch.search("zzzxxq").isEmpty())
    }
}

class GeneratorTest {
    @Test fun lan24() {
        val blocks = CommandGenerator.generate(Cidr.parse("192.168.10.0/24")!!).associate { it.title to it.lines }
        assertEquals(listOf("ip address 192.168.10.1 255.255.255.0"), blocks["Interfejs"])
        assertEquals(" network 192.168.10.0 0.0.0.255 area 0", blocks["OSPF"]!![1])
        assertEquals("access-list 10 permit 192.168.10.0 0.0.0.255", blocks["ACL"]!![0])
        assertEquals("ip dhcp excluded-address 192.168.10.1 192.168.10.10", blocks["DHCP"]!![0])
        assertEquals(" network 192.168.10.0 mask 255.255.255.0", blocks["BGP"]!![1])
    }

    @Test fun hostAndInterfaceAddress() {
        val blocks = CommandGenerator.generate(Cidr.parse("10.0.0.5/30")!!).associate { it.title to it.lines }
        assertEquals("ip address 10.0.0.5 255.255.255.252", blocks["Interfejs"]!![0])
        assertEquals(" network 10.0.0.4 0.0.0.3 area 0", blocks["OSPF"]!![1])
        assertEquals("host 10.0.0.5", CommandGenerator.aclAddress(Cidr.parse("10.0.0.5/32")!!))
        assertEquals("any", CommandGenerator.aclAddress(Cidr.parse("0.0.0.0/0")!!))
        // /31 i /32 bez DHCP
        assertNull(CommandGenerator.generate(Cidr.parse("10.0.0.0/31")!!).firstOrNull { it.title == "DHCP" })
    }
}

class NetCoreTest {
    @Test fun macFormats() {
        val expected = byteArrayOf(0x00, 0x50, 0x79, 0x66, 0x68, 0x00)
        assertArrayEquals(expected, Mac.parse("00:50:79:66:68:00"))
        assertArrayEquals(expected, Mac.parse("00-50-79-66-68-00"))
        assertArrayEquals(expected, Mac.parse("0050.7966.6800"))
        assertArrayEquals(expected, Mac.parse("005079666800"))
        assertNull(Mac.parse("00:50:79:66:68"))
        assertNull(Mac.parse("00:50:79:66:68:zz"))
        assertNull(Mac.parse("0050:7966:6800"))
        assertEquals("00:50:79:66:68:00", Mac.format(expected))
        assertEquals("0050.7966.6800", Mac.formatCisco(expected))
    }

    @Test fun magicPacket() {
        val mac = Mac.parse("AA:BB:CC:DD:EE:FF")!!
        val p = Mac.magicPacket(mac)
        assertEquals(102, p.size)
        assertTrue(p.take(6).all { it == 0xFF.toByte() })
        for (i in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test fun pingParsing() {
        val r = PingParser.reply("64 bytes from 8.8.8.8: icmp_seq=3 ttl=117 time=12.4 ms")!!
        assertEquals(3, r.seq); assertEquals(117, r.ttl); assertEquals(12.4, r.millis, 0.001)
        val s = PingParser.summary("4 packets transmitted, 3 received, 25% packet loss, time 3004ms")!!
        assertEquals(4, s.sent); assertEquals(3, s.received)
        assertNull(PingParser.reply("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data."))
        assertTrue(PingParser.isError("From 10.0.0.1 icmp_seq=1 Destination Host Unreachable"))
        assertTrue(PingParser.isError("ping: unknown host foo.bar"))
        val st = PingStats.of(4, listOf(10.0, 20.0, 30.0))
        assertEquals(25, st.lossPercent); assertEquals(20.0, st.avg!!, 0.001)
    }

    @Test fun portProbesOnLocalhost() = runBlocking {
        val lo = InetAddress.getLoopbackAddress()
        ServerSocket(0, 50, lo).use { server ->
            val closedPort = ServerSocket(0, 50, lo).use { it.localPort }
            assertEquals(PortState.OPEN, Probe.tcp(lo, server.localPort, 1000).state)
            assertEquals(PortState.CLOSED, Probe.tcp(lo, closedPort, 1000).state)
            val results = Probe.scanPorts(lo, listOf(server.localPort, closedPort))
            assertEquals(listOf(PortState.OPEN, PortState.CLOSED), results.sortedBy { it.port != server.localPort }.map { it.state })
            val (alive, open) = Probe.discover(lo, ports = listOf(server.localPort, closedPort))
            assertTrue(alive)
            assertEquals(listOf(server.localPort), open)
        }
        assertNotNull(lo)
    }
}
