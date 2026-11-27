package pl.vlsmcalc.ccna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vlsmcalc.ccna.content.ContentParser
import pl.vlsmcalc.ccna.content.SearchIndex
import pl.vlsmcalc.ccna.content.TextFold
import java.io.File

class SearchTest {
    private val library by lazy {
        ContentParser.parse(File("src/main/assets/ccna").listFiles()!!.filter { it.extension == "txt" }.sortedBy { it.name }.map { it.readText() })
    }
    private val index by lazy { SearchIndex(library) }

    @Test fun foldKeepsLengthAndRemovesDiacritics() {
        val s = "Hasło ŁĄCZE Źródło – ÓÓ"
        assertEquals(s.length, TextFold.fold(s).length)
        assertEquals("haslo lacze zrodlo – oo", TextFold.fold(s))
        assertEquals(listOf("nat-t", "udp", "4500"), TextFold.tokens("NAT-T, UDP 4500"))
        assertEquals(listOf("ip"), TextFold.tokens("ip a"))
    }

    @Test fun findsWithoutPolishCharacters() {
        val r = index.search("haslo")
        assertTrue(r.topics.any { it.topic.id == "secprogram" })
        assertEquals(r.topics.map { it.topic.id }, index.search("hasło").topics.map { it.topic.id })
    }

    @Test fun titleMatchesRankFirst() {
        val r = index.search("ike")
        assertEquals("ike", r.topics.first().topic.id)
        assertEquals(-1, r.topics.first().sectionIndex)
    }

    @Test fun allWordsMustMatchAndSectionIsFound() {
        val r = index.search("tryb tunelowy")
        val ipsec = r.topics.first { it.topic.id == "ipsec" }
        assertEquals("Tryb tunelowy (tunnel mode)", ipsec.topic.sections[ipsec.sectionIndex].title)
        assertTrue(index.search("ospf zzzqqq").isEmpty)
    }

    @Test fun searchesCardsAndQuestions() {
        val r = index.search("NAT-T")
        assertTrue(r.topics.any { it.topic.id == "ike" })
        assertTrue(r.cards.isNotEmpty())
        assertTrue(r.questions.isNotEmpty())
        assertTrue(index.search("").isEmpty)
        assertTrue(index.search("a").isEmpty)
    }

    @Test fun suggestionsAllHaveResults() {
        SearchIndex.SUGGESTIONS.forEach { assertTrue("brak wyników: $it", !index.search(it).isEmpty) }
    }
}
