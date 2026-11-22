package pl.vlsmcalc.wear.core

/** Operacje na adresach IPv4 przechowywanych jako Long w zakresie 0..2^32-1. */
object Ip {
    const val MAX: Long = 0xFFFFFFFFL

    /** Parsuje adres w notacji kropkowej (np. "192.168.1.10"). Zwraca null dla błędnych danych. */
    fun parse(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            val octet = part.toInt()
            if (octet > 255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    fun format(value: Long): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"

    fun octets(value: Long): IntArray =
        intArrayOf(
            ((value shr 24) and 0xFF).toInt(),
            ((value shr 16) and 0xFF).toInt(),
            ((value shr 8) and 0xFF).toInt(),
            (value and 0xFF).toInt(),
        )

    fun mask(prefix: Int): Long {
        require(prefix in 0..32) { "prefix $prefix" }
        return if (prefix == 0) 0L else (MAX shl (32 - prefix)) and MAX
    }

    fun wildcard(prefix: Int): Long = mask(prefix).inv() and MAX

    /** Zwraca prefiks dla poprawnej (ciągłej) maski albo null. */
    fun prefixOfMask(mask: Long): Int? {
        if (mask < 0 || mask > MAX) return null
        val inverted = mask.inv() and MAX
        // Poprawna maska po odwróceniu ma postać 0..01..1, czyli inverted+1 jest potęgą dwójki.
        if ((inverted + 1) and inverted != 0L) return null
        return 32 - java.lang.Long.bitCount(inverted)
    }

    /** Zapis binarny z kropkami, np. 11000000.10101000.00000001.00000000 */
    fun toBinary(value: Long): String =
        octets(value).joinToString(".") { it.toString(2).padStart(8, '0') }

    /** Zapis binarny z separatorem "|" w miejscu granicy prefiksu (pomocny przy nauce). */
    fun toBinaryWithPrefix(value: Long, prefix: Int): String {
        val sb = StringBuilder()
        for (bit in 31 downTo 0) {
            val pos = 31 - bit
            if (pos == prefix && prefix in 1..31) sb.append('|')
            else if (pos > 0 && pos % 8 == 0) sb.append('.')
            sb.append(if ((value shr bit) and 1L == 1L) '1' else '0')
        }
        return sb.toString()
    }

    fun toHex(value: Long): String = "0x" + value.toString(16).uppercase().padStart(8, '0')

    /**
     * Skrócony zapis adresu względem adresu odniesienia: pomija początkowe oktety identyczne
     * z odniesieniem. Np. (192.168.1.62, 192.168.1.0) -> ".62".
     */
    fun shortForm(value: Long, reference: Long): String {
        val a = octets(value)
        val b = octets(reference)
        var same = 0
        while (same < 3 && a[same] == b[same]) same++
        if (same == 0) return format(value)
        return "." + a.drop(same).joinToString(".")
    }

    /** Najmniejszy prefiks (największy blok), w który zmieści się podana liczba adresów. */
    fun prefixForAddresses(addresses: Long): Int? {
        if (addresses < 1 || addresses > (1L shl 32)) return null
        var prefix = 32
        while (prefix > 0 && (1L shl (32 - prefix)) < addresses) prefix--
        return if ((1L shl (32 - prefix)) >= addresses) prefix else null
    }

    /**
     * Prefiks dla wymaganej liczby hostów.
     * Standardowo rezerwuje adres sieci i broadcast (min. /30).
     * Przy [allowSmall] 1 host -> /32, 2 hosty -> /31 (łącza punkt-punkt, RFC 3021).
     */
    fun prefixForHosts(hosts: Long, allowSmall: Boolean = false): Int? {
        if (hosts < 1) return null
        if (allowSmall) {
            if (hosts == 1L) return 32
            if (hosts == 2L) return 31
        }
        val prefix = prefixForAddresses(hosts + 2) ?: return null
        return minOf(prefix, 30)
    }

    fun usableHosts(prefix: Int): Long = when (prefix) {
        32 -> 1
        31 -> 2
        else -> (1L shl (32 - prefix)) - 2
    }

    fun blockSize(prefix: Int): Long = 1L shl (32 - prefix)
}
