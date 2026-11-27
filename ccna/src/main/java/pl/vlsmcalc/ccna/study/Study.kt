package pl.vlsmcalc.ccna.study

import pl.vlsmcalc.ccna.content.Card
import pl.vlsmcalc.ccna.content.Library
import pl.vlsmcalc.ccna.content.Question
import kotlin.math.floor
import kotlin.random.Random

data class QStat(val attempts: Int = 0, val correct: Int = 0, val lastCorrect: Boolean = false)

data class ExamRecord(val at: Long, val total: Int, val correct: Int) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

data class Progress(
    val read: Set<String> = emptySet(),
    /** Pudełko Leitnera dla fiszki: 0 = nowa/nie wiem … 5 = utrwalona. */
    val boxes: Map<String, Int> = emptyMap(),
    val stats: Map<String, QStat> = emptyMap(),
    val exams: List<ExamRecord> = emptyList(),
)

/** Pytanie z wymieszaną kolejnością odpowiedzi. [order] – indeksy oryginalnych odpowiedzi w kolejności wyświetlania. */
data class Presented(val question: Question, val order: List<Int>) {
    val options: List<String> get() = order.map { question.options[it] }

    /** Indeksy (w kolejności wyświetlania) poprawnych odpowiedzi. */
    val correctShown: Set<Int> get() = order.indices.filter { order[it] in question.correct }.toSet()

    fun isCorrect(selectedShown: Set<Int>): Boolean = selectedShown == correctShown
}

object Study {
    const val MASTERED_BOX = 3
    const val MAX_BOX = 5

    fun present(q: Question, random: Random): Presented {
        // Odpowiedzi typu „wszystkie powyższe” zostawiamy w oryginalnej kolejności
        val keepOrder = q.options.any { it.contains("powyższe", ignoreCase = true) }
        val order = if (keepOrder) q.options.indices.toList() else q.options.indices.shuffled(random)
        return Presented(q, order)
    }

    /** Talia fiszek: najpierw najsłabiej znane (niższe pudełko), w obrębie pudełka losowo. */
    fun flashDeck(cards: List<Card>, boxes: Map<String, Int>, size: Int, random: Random): List<Card> =
        cards.shuffled(random)
            .sortedBy { boxes[it.id] ?: 0 }
            .take(size)

    fun answerCard(boxes: Map<String, Int>, cardId: String, known: Boolean): Map<String, Int> {
        val current = boxes[cardId] ?: 0
        val next = if (known) minOf(current + 1, MAX_BOX) else 0
        return boxes + (cardId to next)
    }

    fun mastered(cards: List<Card>, boxes: Map<String, Int>): Int = cards.count { (boxes[it.id] ?: 0) >= MASTERED_BOX }

    /**
     * Zestaw ćwiczeniowy: pytania nigdy nierozwiązane i ostatnio błędne mają pierwszeństwo,
     * potem losowo pozostałe.
     */
    fun practiceSet(questions: List<Question>, stats: Map<String, QStat>, size: Int, random: Random): List<Question> {
        fun priority(q: Question): Int {
            val s = stats[q.id] ?: return 0
            return if (!s.lastCorrect) 1 else 2
        }
        return questions.shuffled(random).sortedBy { priority(it) }.take(size).shuffled(random)
    }

    fun wrongOnes(questions: List<Question>, stats: Map<String, QStat>): List<Question> =
        questions.filter { stats[it.id]?.let { s -> s.attempts > 0 && !s.lastCorrect } == true }

    /** Liczba pytań na obszar wg wag egzaminu (metoda największych reszt). */
    fun examAllocation(weights: Map<Int, Int>, total: Int): Map<Int, Int> {
        val sum = weights.values.sum().toDouble()
        val exact = weights.mapValues { it.value / sum * total }
        val base = exact.mapValues { floor(it.value).toInt() }.toMutableMap()
        var left = total - base.values.sum()
        exact.entries.sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value - floor(it.value) }.thenBy { it.key })
            .forEach { if (left > 0) { base[it.key] = base.getValue(it.key) + 1; left-- } }
        return base
    }

    fun examSet(library: Library, total: Int, random: Random): List<Question> {
        val domains = library.examDomains
        val allocation = examAllocation(domains.associate { it.id to it.weight }, total)
        val chosen = mutableListOf<Question>()
        for (d in domains) {
            val pool = d.topics.flatMap { it.questions }.shuffled(random)
            chosen += pool.take(allocation[d.id] ?: 0)
        }
        // Uzupełnienie, gdyby któryś obszar miał za mało pytań
        if (chosen.size < total) {
            val rest = domains.flatMap { d -> d.topics.flatMap { it.questions } }.filter { it !in chosen }.shuffled(random)
            chosen += rest.take(total - chosen.size)
        }
        return chosen.shuffled(random)
    }

    fun record(stats: Map<String, QStat>, q: Question, correct: Boolean): Map<String, QStat> {
        val s = stats[q.id] ?: QStat()
        return stats + (q.id to QStat(s.attempts + 1, s.correct + if (correct) 1 else 0, correct))
    }

    /** Skuteczność (0–100) w podanych pytaniach, liczona z ostatnich wyników; null gdy brak prób. */
    fun accuracy(questions: List<Question>, stats: Map<String, QStat>): Int? {
        val tried = questions.mapNotNull { stats[it.id] }.filter { it.attempts > 0 }
        if (tried.isEmpty()) return null
        return tried.count { it.lastCorrect } * 100 / tried.size
    }

    /**
     * Ogólny wskaźnik gotowości (0–100): średnia ważona wagami obszarów z
     * (przeczytane tematy 20%, opanowane fiszki 30%, poprawne odpowiedzi na wszystkie pytania 50%).
     */
    fun readiness(library: Library, p: Progress): Int {
        var acc = 0.0
        var weightSum = 0
        for (d in library.examDomains) {
            val topics = d.topics
            val cards = topics.flatMap { it.cards }
            val questions = topics.flatMap { it.questions }
            val read = topics.count { it.id in p.read }.toDouble() / topics.size
            val mastered = if (cards.isEmpty()) 0.0 else mastered(cards, p.boxes).toDouble() / cards.size
            val answered = if (questions.isEmpty()) 0.0 else questions.count { p.stats[it.id]?.lastCorrect == true }.toDouble() / questions.size
            acc += d.weight * (0.2 * read + 0.3 * mastered + 0.5 * answered)
            weightSum += d.weight
        }
        return if (weightSum == 0) 0 else (acc / weightSum * 100).toInt()
    }
}
