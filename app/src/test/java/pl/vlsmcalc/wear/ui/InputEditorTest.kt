package pl.vlsmcalc.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class InputEditorTest {
    private fun type(keys: String, mode: InputMode = InputMode.IP_CIDR, start: String = ""): String =
        keys.fold(start) { acc, c -> InputEditor.append(acc, c, mode) }

    @Test fun autoDotsWhenOctetFull() {
        assertEquals("192.168.100.200", type("192168100200", InputMode.IP))
        assertEquals("192.168.1.0", type("192168.1.0", InputMode.IP))
    }

    @Test fun autoDotOnOverflow() {
        assertEquals("25.6", type("256", InputMode.IP))
        assertEquals("10.0.0.1", type("10.001", InputMode.IP))
        assertEquals("100.1", type("1001", InputMode.IP))
    }

    @Test fun autoSlashInCidrMode() {
        assertEquals("192.168.1.100/24", type("1921681.10024"))
        assertEquals("10.0.0.0/8", type("10.0.0.0/8"))
        // W trybie IP nie ma prefiksu
        assertEquals("10.0.0.255", type("10.0.0.2559", InputMode.IP))
    }

    @Test fun prefixLimits() {
        assertEquals("10.0.0.0/32", type("10.0.0.0/329"))
        assertEquals("10.0.0.0/0", type("10.0.0.0/01"))
        assertEquals("10.0.0.0/3", type("10.0.0.0/3/"))
    }

    @Test fun dotMaskAfterSlash() {
        assertEquals("10.0.0.0/255.255.255.0", type("10.0.0.0/255.255.255.0"))
    }

    @Test fun ignoresInvalidPunctuation() {
        assertEquals("1.", type(".1.", InputMode.IP))
        assertEquals("1.2.3.4", type("1..2.3.4.", InputMode.IP))
        assertEquals("1.2", type("1/.2", InputMode.IP))
    }

    @Test fun numbers() {
        assertEquals("120", type("0120", InputMode.NUMBER))
        assertEquals("1234567890", type("12345678901", InputMode.NUMBER))
        assertEquals("5", type("5./", InputMode.NUMBER))
    }
}
