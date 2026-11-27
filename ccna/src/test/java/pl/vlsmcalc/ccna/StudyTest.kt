package pl.vlsmcalc.ccna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.vlsmcalc.ccna.content.Card
import pl.vlsmcalc.ccna.content.ContentParser
import pl.vlsmcalc.ccna.content.Question
import pl.vlsmcalc.ccna.study.Progress
import pl.vlsmcalc.ccna.study.QStat
import pl.vlsmcalc.ccna.study.Study
import java.io.File
import kotlin.random.Random

class StudyTest {
    private val library by lazy {
        ContentParser.parse(File("src/main/assets/ccna").listFiles()!!.filter { it.extension == "txt" }.sortedBy { it.name }.map { it.readText() })
    }

    private fun q(id: String, correct: Set<Int> = setOf(0)) =
        Question(id, "t", 1, "Q$id", listOf("A", "B", "C", "D"), correct, "x")

    @Test fun presentedKeepsCorrectMapping() {
        val question = q("1", setOf(1, 3))
        repeat(20) { seed ->
            val p = Study.present(question, Random(seed))
            assertEquals(setOf("B", "D"), p.correctShown.map { p.options[it] }.toSet())
            assertTrue(p.isCorrect(p.correctShown))
            assertFalse(p.isCorrect(setOf(p.correctShown.first())))
        }
    }

    @Test fun leitnerBoxes() {
        var boxes = emptyMap<String, Int>()
        boxes = Study.answerCard(boxes, "c", true)
        boxes = Study.answerCard(boxes, "c", true)
        boxes = Study.answerCard(boxes, "c", true)
        assertEquals(3, boxes["c"])
        val cards = listOf(Card("c", "t", "f", "b"), Card("d", "t", "f", "b"))
        assertEquals(1, Study.mastered(cards, boxes))
        boxes = Study.answerCard(boxes, "c", false)
        assertEquals(0, boxes["c"])
        repeat(10) { boxes = Study.answerCard(boxes, "d", true) }
        assertEquals(Study.MAX_BOX, boxes["d"])
    }

    @Test fun flashDeckWeakestFirst() {
        val cards = (1..10).map { Card("c$it", "t", "f", "b") }
        val boxes = cards.associate { it.id to 3 } + ("c7" to 0) + ("c2" to 1)
        val deck = Study.flashDeck(cards, boxes, 2, Random(1))
        assertEquals(listOf("c7", "c2"), deck.map { it.id })
    }

    @Test fun practicePrefersUnseenAndWrong() {
        val qs = (1..6).map { q("$it") }
        val stats = mapOf(
            "1" to QStat(3, 3, true), "2" to QStat(1, 1, true), "3" to QStat(2, 0, false),
            "4" to QStat(1, 1, true), "5" to QStat(1, 1, true), "6" to QStat(1, 1, true),
        )
        val set = Study.practiceSet(qs, stats, 1, Random(3))
        assertEquals("3", set.single().id)
        assertEquals(listOf("3"), Study.wrongOnes(qs, stats).map { it.id })
    }

    @Test fun examAllocationFollowsWeights() {
        val weights = mapOf(1 to 20, 2 to 20, 3 to 25, 4 to 10, 5 to 15, 6 to 10)
        val a = Study.examAllocation(weights, 40)
        assertEquals(mapOf(1 to 8, 2 to 8, 3 to 10, 4 to 4, 5 to 6, 6 to 4), a)
        assertEquals(30, Study.examAllocation(weights, 30).values.sum())
        assertEquals(7, Study.examAllocation(weights, 30)[1]!! + 1) // 20% z 30 = 6
    }

    @Test fun examSetFromRealContent() {
        val exam = Study.examSet(library, 40, Random(7))
        assertEquals(40, exam.size)
        assertEquals(40, exam.map { it.id }.toSet().size)
        assertTrue(exam.none { it.domainId == 7 })
        val byDomain = exam.groupingBy { it.domainId }.eachCount()
        assertEquals(10, byDomain[3])
    }

    @Test fun accuracyAndReadiness() {
        val qs = listOf(q("a"), q("b"), q("c"))
        assertNull(Study.accuracy(qs, emptyMap()))
        val stats = Study.record(Study.record(emptyMap(), qs[0], true), qs[1], false)
        assertEquals(50, Study.accuracy(qs, stats))
        assertEquals(0, Study.readiness(library, Progress()))
        val all = Progress(
            read = library.topics.map { it.id }.toSet(),
            boxes = library.cards.associate { it.id to 5 },
            stats = library.questions.associate { it.id to QStat(1, 1, true) },
        )
        assertEquals(100, Study.readiness(library, all))
    }
}
