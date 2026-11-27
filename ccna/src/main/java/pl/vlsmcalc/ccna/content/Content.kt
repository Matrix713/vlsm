package pl.vlsmcalc.ccna.content

/** Obszar egzaminu CCNA 200-301 (z wagą procentową) albo dział pomocniczy (waga 0). */
data class Domain(val id: Int, val title: String, val weight: Int, val topics: List<Topic>) {
    val isExamDomain: Boolean get() = weight > 0
}

data class Topic(
    val id: String,
    val title: String,
    val domainId: Int,
    val sections: List<Section>,
    val cards: List<Card>,
    val questions: List<Question>,
)

data class Section(val title: String, val lines: List<Line>)

sealed interface Line {
    val text: String

    data class Paragraph(override val text: String) : Line
    data class Bullet(override val text: String) : Line
    data class Row(val key: String, val value: String) : Line {
        override val text: String get() = "$key $value"
    }
    data class Code(override val text: String) : Line
}

data class Card(val id: String, val topicId: String, val front: String, val back: String)

data class Question(
    val id: String,
    val topicId: String,
    val domainId: Int,
    val text: String,
    val options: List<String>,
    val correct: Set<Int>,
    val explanation: String,
) {
    val isMulti: Boolean get() = correct.size > 1
}

class Library(val domains: List<Domain>) {
    val topics: List<Topic> = domains.flatMap { it.topics }
    val cards: List<Card> = topics.flatMap { it.cards }
    val questions: List<Question> = topics.flatMap { it.questions }
    val examDomains: List<Domain> get() = domains.filter { it.isExamDomain }

    private val topicById = topics.associateBy { it.id }
    private val questionById = questions.associateBy { it.id }

    fun topic(id: String): Topic? = topicById[id]
    fun question(id: String): Question? = questionById[id]
    fun domain(id: Int): Domain? = domains.firstOrNull { it.id == id }
    fun domainOfTopic(topicId: String): Domain? = topic(topicId)?.let { domain(it.domainId) }
}

/**
 * Parser prostego formatu treści:
 * ```
 * @domain 1 | Podstawy sieci | 20
 * @topic osi | Model OSI
 * ## Nagłówek sekcji
 * zwykły akapit
 * - punkt listy (może zawierać **pogrubienie**)
 * | klucz | wartość
 * > linia kodu / polecenia
 * @card przód | tył
 * @quiz Treść pytania?
 * + poprawna odpowiedź
 * - błędna odpowiedź
 * = wyjaśnienie (kończy pytanie)
 * ```
 * Linie zaczynające się od `#!` to komentarze.
 */
object ContentParser {
    class ParseException(message: String) : Exception(message)

    fun parse(sources: List<String>): Library {
        val domains = mutableListOf<Domain>()

        var domainId = -1
        var domainTitle = ""
        var domainWeight = 0
        val topics = mutableListOf<Topic>()

        var topicId: String? = null
        var topicTitle = ""
        val sections = mutableListOf<Section>()
        var sectionTitle: String? = null
        var lines = mutableListOf<Line>()
        val cards = mutableListOf<Card>()
        val questions = mutableListOf<Question>()

        var quizText: String? = null
        val quizOptions = mutableListOf<String>()
        val quizCorrect = mutableSetOf<Int>()

        fun fail(lineNo: Int, msg: String): Nothing = throw ParseException("Linia $lineNo: $msg")

        fun flushSection() {
            if (sectionTitle != null || lines.isNotEmpty()) {
                sections += Section(sectionTitle ?: "", lines)
            }
            sectionTitle = null
            lines = mutableListOf()
        }

        fun flushQuiz(lineNo: Int, explanation: String) {
            val text = quizText ?: return
            val tid = topicId ?: fail(lineNo, "pytanie poza tematem")
            if (quizOptions.size < 2) fail(lineNo, "pytanie ma mniej niż 2 odpowiedzi: $text")
            if (quizCorrect.isEmpty()) fail(lineNo, "pytanie bez poprawnej odpowiedzi: $text")
            questions += Question(
                id = "$tid:q:${stableHash(text)}",
                topicId = tid,
                domainId = domainId,
                text = text,
                options = quizOptions.toList(),
                correct = quizCorrect.toSet(),
                explanation = explanation,
            )
            quizText = null
            quizOptions.clear()
            quizCorrect.clear()
        }

        fun flushTopic(lineNo: Int) {
            if (quizText != null) fail(lineNo, "pytanie bez wyjaśnienia (=): $quizText")
            val tid = topicId ?: return
            flushSection()
            topics += Topic(tid, topicTitle, domainId, sections.toList(), cards.toList(), questions.toList())
            sections.clear()
            cards.clear()
            questions.clear()
            topicId = null
        }

        fun flushDomain(lineNo: Int) {
            flushTopic(lineNo)
            if (domainId >= 0) domains += Domain(domainId, domainTitle, domainWeight, topics.toList())
            topics.clear()
            domainId = -1
        }

        var globalLine = 0
        for (source in sources) {
            for (raw in source.lines()) {
                globalLine++
                val line = raw.trimEnd()
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#!")) continue

                if (quizText != null && t.startsWith("@")) fail(globalLine, "pytanie bez wyjaśnienia (=): $quizText")
                if (quizText != null) {
                    when {
                        t.startsWith("+ ") -> { quizCorrect += quizOptions.size; quizOptions += t.substring(2).trim() }
                        t.startsWith("- ") -> quizOptions += t.substring(2).trim()
                        t.startsWith("= ") -> flushQuiz(globalLine, t.substring(2).trim())
                        else -> quizText = quizText + " " + t
                    }
                    continue
                }

                when {
                    t.startsWith("@domain ") -> {
                        flushDomain(globalLine)
                        val parts = t.removePrefix("@domain ").split("|").map { it.trim() }
                        if (parts.size != 3) fail(globalLine, "@domain id | tytuł | waga")
                        domainId = parts[0].toIntOrNull() ?: fail(globalLine, "zły numer domeny")
                        domainTitle = parts[1]
                        domainWeight = parts[2].toIntOrNull() ?: fail(globalLine, "zła waga")
                    }
                    t.startsWith("@topic ") -> {
                        if (domainId < 0) fail(globalLine, "temat przed @domain")
                        flushTopic(globalLine)
                        val parts = t.removePrefix("@topic ").split("|", limit = 2).map { it.trim() }
                        if (parts.size != 2) fail(globalLine, "@topic id | tytuł")
                        topicId = parts[0]
                        topicTitle = parts[1]
                    }
                    t.startsWith("@card ") -> {
                        val tid = topicId ?: fail(globalLine, "fiszka poza tematem")
                        val parts = t.removePrefix("@card ").split("|", limit = 2).map { it.trim() }
                        if (parts.size != 2 || parts.any { it.isEmpty() }) fail(globalLine, "@card przód | tył")
                        cards += Card("$tid:c:${stableHash(parts[0])}", tid, parts[0], parts[1])
                    }
                    t.startsWith("@quiz ") -> {
                        if (topicId == null) fail(globalLine, "pytanie poza tematem")
                        quizText = t.removePrefix("@quiz ").trim()
                    }
                    t.startsWith("@") -> fail(globalLine, "nieznana dyrektywa: $t")
                    topicId == null -> fail(globalLine, "treść poza tematem: $t")
                    t.startsWith("## ") -> {
                        flushSection()
                        sectionTitle = t.removePrefix("## ").trim()
                    }
                    t.startsWith("- ") -> lines += Line.Bullet(t.substring(2).trim())
                    t.startsWith("> ") -> lines += Line.Code(line.substringAfter("> "))
                    t.startsWith("| ") -> {
                        val parts = t.removePrefix("| ").split("|", limit = 2).map { it.trim() }
                        lines += Line.Row(parts[0], parts.getOrElse(1) { "" })
                    }
                    else -> lines += Line.Paragraph(t)
                }
            }
        }
        flushDomain(globalLine)
        return Library(domains.sortedBy { it.id })
    }

    /** Stabilny hash (niezależny od JVM) do identyfikatorów zapisywanych w postępach. */
    fun stableHash(text: String): String {
        var h = 0xcbf29ce484222325uL
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h = h xor b.toUByte().toULong()
            h *= 0x100000001b3uL
        }
        return (h and 0xFFFFFFFFFFuL).toString(36)
    }
}

/** Usuwa znaczniki **pogrubienia** (np. do wyszukiwania). */
fun String.plain(): String = replace("**", "")
