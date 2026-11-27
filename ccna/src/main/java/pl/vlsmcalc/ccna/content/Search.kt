package pl.vlsmcalc.ccna.content

import java.text.Normalizer

/** Ujednolicanie tekstu do wyszukiwania: małe litery, bez polskich znaków. Zachowuje długość tekstu. */
object TextFold {
    private val polish = mapOf(
        'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n', 'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
    )

    fun fold(c: Char): Char {
        if (c.code < 128) return c.lowercaseChar()
        val lower = c.lowercaseChar()
        polish[lower]?.let { return it }
        val decomposed = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
        return decomposed.firstOrNull() ?: lower
    }

    fun fold(s: String): String {
        val out = CharArray(s.length)
        for (i in s.indices) out[i] = fold(s[i])
        return String(out)
    }

    /** Słowa zapytania (min. 2 znaki, z wyjątkiem liczb), już ujednolicone. */
    fun tokens(query: String): List<String> =
        fold(query).split(Regex("[^a-z0-9./:+-]+"))
            .map { it.trim('.', ':', '-') }
            .filter { it.length >= 2 || (it.isNotEmpty() && it.all(Char::isDigit)) }
            .distinct()
}

data class TopicHit(val topic: Topic, val sectionIndex: Int, val snippet: String, val score: Int)

data class SearchResults(
    val query: String,
    val tokens: List<String>,
    val topics: List<TopicHit>,
    val cards: List<Card>,
    val questions: List<Question>,
) {
    val total: Int get() = topics.size + cards.size + questions.size
    val isEmpty: Boolean get() = total == 0
}

/**
 * Indeks wyszukiwania budowany raz dla całej treści.
 * Dopasowanie: wszystkie słowa zapytania muszą wystąpić (w dowolnej kolejności), bez względu na polskie znaki.
 */
class SearchIndex(private val library: Library) {
    private class SectionDoc(val title: String, val body: String, val lines: List<String>)
    private class TopicDoc(val topic: Topic, val title: String, val sections: List<SectionDoc>, val all: String)

    private val topicDocs = library.topics.map { t ->
        val sections = t.sections.map { s ->
            val lines = s.lines.map { it.text.plain() }
            SectionDoc(TextFold.fold(s.title), TextFold.fold(lines.joinToString("\n")), lines)
        }
        val all = TextFold.fold(t.title) + "\n" + sections.joinToString("\n") { it.title + "\n" + it.body } +
            "\n" + TextFold.fold(t.cards.joinToString("\n") { it.front + " " + it.back })
        TopicDoc(t, TextFold.fold(t.title), sections, all)
    }
    private val cardDocs = library.cards.map { it to TextFold.fold(it.front.plain() + "\n" + it.back.plain()) }
    private val questionDocs = library.questions.map { q ->
        q to TextFold.fold((listOf(q.text) + q.options + q.explanation).joinToString("\n").plain())
    }

    private fun String.hasAll(tokens: List<String>) = tokens.all { contains(it) }

    fun search(query: String, limit: Int = 25): SearchResults {
        val tokens = TextFold.tokens(query)
        if (tokens.isEmpty()) return SearchResults(query, tokens, emptyList(), emptyList(), emptyList())

        val topics = topicDocs.mapNotNull { d ->
            val sectionIdx = d.sections.indexOfFirst { it.title.hasAll(tokens) }
                .takeIf { it >= 0 } ?: d.sections.indexOfFirst { (it.title + " " + it.body).hasAll(tokens) }
            val score = when {
                d.title.hasAll(tokens) -> 100
                sectionIdx >= 0 && d.sections[sectionIdx].title.hasAll(tokens) -> 60
                sectionIdx >= 0 -> 30
                d.all.hasAll(tokens) -> 10
                else -> return@mapNotNull null
            }
            val section = sectionIdx.coerceAtLeast(0)
            TopicHit(d.topic, if (score == 100) -1 else section, snippet(d.sections.getOrNull(section), tokens), score)
        }.sortedByDescending { it.score }.take(limit)

        val cards = cardDocs.filter { it.second.hasAll(tokens) }
            .sortedByDescending { (card, _) -> if (TextFold.fold(card.front).hasAll(tokens)) 1 else 0 }
            .map { it.first }.take(limit)
        val questions = questionDocs.filter { it.second.hasAll(tokens) }
            .sortedByDescending { (q, _) -> if (TextFold.fold(q.text).hasAll(tokens)) 1 else 0 }
            .map { it.first }.take(limit)
        return SearchResults(query, tokens, topics, cards, questions)
    }

    private fun snippet(section: SectionDoc?, tokens: List<String>): String {
        section ?: return ""
        val line = section.lines.firstOrNull { l -> tokens.any { TextFold.fold(l).contains(it) } }
            ?: section.lines.firstOrNull() ?: return ""
        if (line.length <= 90) return line
        val pos = tokens.map { TextFold.fold(line).indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (pos - 30).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + line.substring(start, minOf(line.length, start + 90)) + "…"
    }

    companion object {
        /** Podpowiedzi na ekranie wyszukiwania (dotknięcie zamiast pisania). */
        val SUGGESTIONS = listOf(
            "IPsec", "IKE", "VPN", "OSPF", "NAT", "ACL", "VLAN", "trunk", "STP", "EtherChannel",
            "HSRP", "DHCP", "IPv6", "WPA3", "RADIUS", "QoS", "SNMP", "Syslog", "REST", "JSON",
        )
    }
}
