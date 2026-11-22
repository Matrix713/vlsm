package pl.vlsmcalc.wear.ui

/**
 * Logika szybkiego wpisywania na klawiaturze zegarka.
 * - Kropka wstawia się sama, gdy oktet ma 3 cyfry, jest "0" albo kolejna cyfra przekroczyłaby 255.
 * - W trybie IP/CIDR po pełnym 4. oktecie kolejna cyfra trafia do prefiksu (sam wstawia "/").
 * - Prefiks jest ograniczony do 0..32, liczby do 10 cyfr bez zer wiodących.
 */
object InputEditor {
    fun append(text: String, ch: Char, mode: InputMode): String = when (mode) {
        InputMode.NUMBER -> appendNumber(text, ch)
        InputMode.IP, InputMode.IP_CIDR -> appendAddress(text, ch, mode == InputMode.IP_CIDR)
    }

    fun backspace(text: String): String = text.dropLast(1)

    private fun appendNumber(text: String, ch: Char): String {
        if (!ch.isDigit()) return text
        if (text == "0") return ch.toString()
        if (text.length >= 10) return text
        return text + ch
    }

    private fun appendAddress(text: String, ch: Char, allowPrefix: Boolean): String {
        val slash = text.indexOf('/')
        if (slash >= 0) return appendPrefix(text, slash, ch)
        val dots = text.count { it == '.' }
        val octet = text.substringAfterLast('.')
        return when (ch) {
            '.' -> if (octet.isNotEmpty() && dots < 3) "$text." else text
            '/' -> if (allowPrefix && dots == 3 && octet.isNotEmpty()) "$text/" else text
            else -> {
                if (!ch.isDigit()) return text
                val full = octet.length == 3 || octet == "0" || (octet + ch).toInt() > 255
                when {
                    !full -> text + ch
                    dots < 3 -> "$text.$ch"
                    allowPrefix -> appendPrefix("$text/", text.length, ch)
                    else -> text
                }
            }
        }
    }

    private fun appendPrefix(text: String, slash: Int, ch: Char): String {
        val prefix = text.substring(slash + 1)
        if (prefix.contains('.')) {
            // Maska w notacji kropkowej po ukośniku, np. /255.255.255.0
            val dots = prefix.count { it == '.' }
            val octet = prefix.substringAfterLast('.')
            return when {
                ch == '.' -> if (octet.isNotEmpty() && dots < 3) "$text." else text
                !ch.isDigit() -> text
                octet.length == 3 || octet == "0" || (octet + ch).toInt() > 255 ->
                    if (dots < 3) "$text.$ch" else text
                else -> text + ch
            }
        }
        return when {
            ch == '.' -> if (prefix.isNotEmpty() && prefix.toInt() in 128..255) "$text." else text
            !ch.isDigit() -> text
            prefix == "0" -> text
            (prefix + ch).toInt() <= 32 -> text + ch
            // Pozwala zacząć wpisywać maskę kropkową, np. 255
            (prefix + ch).toInt() <= 255 && prefix.length < 3 -> text + ch
            else -> text
        }
    }
}
