package pl.vlsmcalc.ccna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pl.vlsmcalc.ccna.content.ContentParser
import pl.vlsmcalc.ccna.content.Library
import pl.vlsmcalc.ccna.content.Line
import java.io.File

class ContentTest {
    private val library: Library by lazy {
        val dir = File("src/main/assets/ccna")
        val files = dir.listFiles { f -> f.extension == "txt" }!!.sortedBy { it.name }
        assertTrue("brak plików treści", files.isNotEmpty())
        ContentParser.parse(files.map { it.readText() })
    }

    @Test fun examDomainsMatchBlueprint() {
        val weights = library.examDomains.associate { it.id to it.weight }
        assertEquals(mapOf(1 to 20, 2 to 20, 3 to 25, 4 to 10, 5 to 15, 6 to 10), weights)
        assertEquals(100, weights.values.sum())
    }

    @Test fun everyTopicHasContentCardsAndQuestions() {
        for (t in library.topics) {
            assertTrue("${t.id}: brak sekcji", t.sections.isNotEmpty())
            assertTrue("${t.id}: brak fiszek", t.cards.isNotEmpty())
            assertTrue("${t.id}: brak pytań", t.questions.isNotEmpty())
        }
    }

    @Test fun idsUnique() {
        val topicDupes = library.topics.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("Duplikaty tematów: $topicDupes", topicDupes.isEmpty())
        val qDupes = library.questions.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("Duplikaty pytań: $qDupes", qDupes.isEmpty())
        val cDupes = library.cards.groupBy { it.id }.filter { it.value.size > 1 }.keys
        assertTrue("Duplikaty fiszek: $cDupes", cDupes.isEmpty())
    }

    @Test fun questionsWellFormed() {
        for (q in library.questions) {
            assertTrue(q.text, q.options.size in 2..6)
            assertTrue(q.text, q.correct.isNotEmpty() && q.correct.all { it in q.options.indices })
            assertTrue("Brak wyjaśnienia: ${q.text}", q.explanation.isNotBlank())
            assertEquals("Powtórzone odpowiedzi: ${q.text}", q.options.size, q.options.toSet().size)
            val saysTwo = q.text.contains("wybierz dwie")
            if (saysTwo != (q.correct.size == 2)) fail("Niezgodna liczba poprawnych odpowiedzi: ${q.text}")
            if (q.correct.size > 2) fail("Za dużo poprawnych odpowiedzi: ${q.text}")
        }
    }

    @Test fun volume() {
        assertTrue("pytania: ${library.questions.size}", library.questions.size >= 200)
        assertTrue("fiszki: ${library.cards.size}", library.cards.size >= 200)
        assertTrue("tematy: ${library.topics.size}", library.topics.size >= 45)
        val vpn = listOf("vpnbasics", "ipsec", "ike", "ipseccfg", "gre", "ravpn", "providervpn", "crypto")
        vpn.forEach { assertTrue("brak tematu $it", library.topic(it) != null) }
        println("Tematy: ${library.topics.size}, fiszki: ${library.cards.size}, pytania: ${library.questions.size}")
        library.domains.forEach { d ->
            println("  ${d.id} ${d.title}: ${d.topics.size} tematów, ${d.topics.sumOf { it.questions.size }} pytań")
        }
    }

    @Test fun boldMarkersBalanced() {
        for (t in library.topics) for (s in t.sections) for (l in s.lines) {
            val text = when (l) {
                is Line.Row -> l.key + l.value
                else -> l.text
            }
            if (text.split("**").size % 2 == 0) fail("Niezamknięte ** w ${t.id}: $text")
        }
    }
}

class ParserTest {
    @Test fun parsesAllElements() {
        val lib = ContentParser.parse(
            listOf(
                """
                @domain 1 | Test | 100
                @topic t1 | Temat
                ## Sekcja
                akapit
                - punkt **ważny**
                | klucz | wartość
                > show run
                @card Przód | Tył
                @quiz Pytanie
                wieloliniowe?
                - zła
                + dobra
                = Bo tak.
                """.trimIndent(),
            ),
        )
        val t = lib.topic("t1")!!
        assertEquals(4, t.sections[0].lines.size)
        assertTrue(t.sections[0].lines[3] is Line.Code)
        assertEquals("Pytanie wieloliniowe?", t.questions[0].text)
        assertEquals(setOf(1), t.questions[0].correct)
        assertEquals("Tył", t.cards[0].back)
    }

    @Test(expected = ContentParser.ParseException::class)
    fun unterminatedQuizFails() {
        ContentParser.parse(listOf("@domain 1 | T | 1\n@topic a | A\n@quiz Q?\n+ a\n- b\n@topic b | B"))
    }

    @Test fun stableHashIsStable() {
        assertEquals(ContentParser.stableHash("abc"), ContentParser.stableHash("abc"))
        assertTrue(ContentParser.stableHash("abc") != ContentParser.stableHash("abd"))
    }
}
