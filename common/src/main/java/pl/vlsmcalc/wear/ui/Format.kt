package pl.vlsmcalc.wear.ui

/** Polska odmiana: 1 host, 2–4 hosty, 5+ hostów (12–14 hostów). */
fun plural(n: Long, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        n == 1L -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

fun hostsLabel(n: Long) = "${fmt(n)} ${plural(n, "host", "hosty", "hostów")}"

/** Grupowanie cyfr spacjami: 16777214 -> "16 777 214". */
fun fmt(n: Long): String {
    val s = n.toString()
    if (s.length <= 4) return s
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(' ')
        sb.append(c)
    }
    return sb.toString()
}
