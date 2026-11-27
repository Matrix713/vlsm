package pl.vlsmcalc.ccna

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import pl.vlsmcalc.ccna.content.Card
import pl.vlsmcalc.ccna.content.ContentParser
import pl.vlsmcalc.ccna.content.Library
import pl.vlsmcalc.ccna.content.Question
import pl.vlsmcalc.ccna.content.Topic
import pl.vlsmcalc.ccna.content.SearchIndex
import pl.vlsmcalc.ccna.content.SearchResults
import pl.vlsmcalc.ccna.study.ExamRecord
import pl.vlsmcalc.ccna.study.Presented
import pl.vlsmcalc.ccna.study.Progress
import pl.vlsmcalc.ccna.study.Study
import pl.vlsmcalc.ccna.study.StudyStore
import kotlin.random.Random

/** Zakres materiału dla fiszek i quizów. */
sealed interface Scope {
    data object All : Scope
    data class InDomain(val id: Int) : Scope
    data class InTopic(val id: String) : Scope
}

data class FlashSession(
    val title: String,
    val cards: List<Card>,
    val index: Int = 0,
    val flipped: Boolean = false,
    val known: Int = 0,
    val unknown: Int = 0,
) {
    val done: Boolean get() = index >= cards.size
    val current: Card? get() = cards.getOrNull(index)
}

data class QuizSession(
    val title: String,
    val items: List<Presented>,
    val exam: Boolean,
    val index: Int = 0,
    val selected: Set<Int> = emptySet(),
    val answered: Boolean = false,
    /** Odpowiedzi użytkownika (indeksy wyświetlanych opcji) dla każdego pytania. */
    val answers: List<Set<Int>?> = List(items.size) { null },
    val startedAt: Long = System.currentTimeMillis(),
) {
    val current: Presented get() = items[index]
    val isLast: Boolean get() = index == items.lastIndex
}

data class QuizResult(
    val title: String,
    val exam: Boolean,
    val total: Int,
    val correct: Int,
    val seconds: Long,
    /** domena → (poprawne, wszystkie) */
    val perDomain: Map<Int, Pair<Int, Int>>,
    val wrong: List<Pair<Presented, Set<Int>>>,
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

class CcnaViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StudyStore(app)
    private val random = Random(System.nanoTime())

    val library: Library = ContentParser.parse(
        app.assets.list("ccna").orEmpty().filter { it.endsWith(".txt") }.sorted().map { name ->
            app.assets.open("ccna/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        },
    )

    var progress by mutableStateOf(store.load())
        private set
    var flash by mutableStateOf<FlashSession?>(null)
        private set
    var quiz by mutableStateOf<QuizSession?>(null)
        private set
    var result by mutableStateOf<QuizResult?>(null)
        private set
    var searchQuery by mutableStateOf("")

    private fun update(block: Progress.() -> Progress) {
        progress = progress.block()
        store.save(progress)
    }

    // ---------- Zakresy ----------

    fun scopeTitle(scope: Scope): String = when (scope) {
        Scope.All -> "Wszystko"
        is Scope.InDomain -> library.domain(scope.id)?.title ?: ""
        is Scope.InTopic -> library.topic(scope.id)?.title ?: ""
    }

    fun cardsIn(scope: Scope): List<Card> = when (scope) {
        Scope.All -> library.cards
        is Scope.InDomain -> library.domain(scope.id)?.topics?.flatMap { it.cards }.orEmpty()
        is Scope.InTopic -> library.topic(scope.id)?.cards.orEmpty()
    }

    fun questionsIn(scope: Scope): List<Question> = when (scope) {
        Scope.All -> library.questions
        is Scope.InDomain -> library.domain(scope.id)?.topics?.flatMap { it.questions }.orEmpty()
        is Scope.InTopic -> library.topic(scope.id)?.questions.orEmpty()
    }

    // ---------- Teoria ----------

    fun isRead(topicId: String) = topicId in progress.read

    fun setRead(topicId: String, read: Boolean) = update {
        copy(read = if (read) this.read + topicId else this.read - topicId)
    }

    fun nextTopic(topicId: String): Topic? {
        val all = library.topics
        val i = all.indexOfFirst { it.id == topicId }
        return all.getOrNull(i + 1)
    }

    // ---------- Fiszki ----------

    fun startFlash(scope: Scope, size: Int = 20) {
        val cards = Study.flashDeck(cardsIn(scope), progress.boxes, size, random)
        flash = FlashSession(scopeTitle(scope), cards)
    }

    fun flipCard() {
        flash = flash?.let { it.copy(flipped = !it.flipped) }
    }

    fun answerCard(known: Boolean) {
        val s = flash ?: return
        val card = s.current ?: return
        update { copy(boxes = Study.answerCard(boxes, card.id, known)) }
        // Nieznana fiszka wraca na koniec bieżącej sesji
        val cards = if (known || s.cards.size >= 60) s.cards else s.cards + card
        flash = s.copy(
            cards = cards,
            index = s.index + 1,
            flipped = false,
            known = s.known + if (known) 1 else 0,
            unknown = s.unknown + if (known) 0 else 1,
        )
    }

    // ---------- Quiz i egzamin ----------

    private fun startQuiz(title: String, questions: List<Question>, exam: Boolean) {
        if (questions.isEmpty()) return
        result = null
        quiz = QuizSession(title, questions.map { Study.present(it, random) }, exam)
    }

    fun startPractice(scope: Scope, size: Int = 10) =
        startQuiz(scopeTitle(scope), Study.practiceSet(questionsIn(scope), progress.stats, size, random), exam = false)

    fun wrongQuestions(): List<Question> = Study.wrongOnes(library.questions, progress.stats)

    fun startWrong() = startQuiz("Powtórka błędów", wrongQuestions().shuffled(random).take(20), exam = false)

    fun startExam(size: Int) = startQuiz("Egzamin próbny", Study.examSet(library, size, random), exam = true)

    fun toggleOption(shown: Int) {
        val s = quiz ?: return
        if (s.answered) return
        val multi = s.current.question.isMulti
        val selected = when {
            !multi -> setOf(shown)
            shown in s.selected -> s.selected - shown
            else -> s.selected + shown
        }
        quiz = s.copy(selected = selected)
        // W trybie ćwiczeń pytanie jednokrotnego wyboru sprawdzamy od razu
        if (!multi && !s.exam) submit()
    }

    /** Ćwiczenia: sprawdza odpowiedź i pokazuje wyjaśnienie. */
    fun submit() {
        val s = quiz ?: return
        if (s.selected.isEmpty() || s.answered) return
        val correct = s.current.isCorrect(s.selected)
        update { copy(stats = Study.record(stats, s.current.question, correct)) }
        quiz = s.copy(answered = true, answers = s.answers.toMutableList().also { it[s.index] = s.selected })
    }

    /** Przejście dalej. Zwraca true, gdy sesja się zakończyła (jest wynik). */
    fun next(): Boolean {
        val s = quiz ?: return true
        var session = s
        if (s.exam) {
            if (s.selected.isEmpty()) return false
            val correct = s.current.isCorrect(s.selected)
            update { copy(stats = Study.record(stats, s.current.question, correct)) }
            session = s.copy(answers = s.answers.toMutableList().also { it[s.index] = s.selected })
        } else if (!s.answered) {
            return false
        }
        if (session.isLast) {
            finish(session)
            return true
        }
        quiz = session.copy(index = session.index + 1, selected = emptySet(), answered = false)
        return false
    }

    private fun finish(s: QuizSession) {
        val answered = s.items.indices.filter { s.answers[it] != null }
        val perDomain = mutableMapOf<Int, Pair<Int, Int>>()
        val wrong = mutableListOf<Pair<Presented, Set<Int>>>()
        var correct = 0
        for (i in answered) {
            val item = s.items[i]
            val ok = item.isCorrect(s.answers[i]!!)
            if (ok) correct++ else wrong += item to s.answers[i]!!
            val d = item.question.domainId
            val (c, t) = perDomain[d] ?: (0 to 0)
            perDomain[d] = (c + if (ok) 1 else 0) to (t + 1)
        }
        val r = QuizResult(
            title = s.title,
            exam = s.exam,
            total = answered.size,
            correct = correct,
            seconds = (System.currentTimeMillis() - s.startedAt) / 1000,
            perDomain = perDomain.toSortedMap(),
            wrong = wrong,
        )
        if (s.exam) update { copy(exams = (listOf(ExamRecord(System.currentTimeMillis(), r.total, r.correct)) + exams).take(20)) }
        result = r
        quiz = null
    }

    /** Kończy egzamin przed czasem – liczą się udzielone odpowiedzi. */
    fun finishEarly() {
        quiz?.let { finish(it) }
    }

    // ---------- Postęp ----------

    fun readiness(): Int = Study.readiness(library, progress)

    fun resetProgress() = update { Progress(recentSearches = recentSearches) }

    // ---------- Wyszukiwanie ----------

    private val searchIndex by lazy { SearchIndex(library) }

    /** Wyniki dla bieżącego zapytania (teoria, fiszki, pytania). */
    fun results(query: String = searchQuery): SearchResults = searchIndex.search(query)

    /** Ustawia zapytanie i zapamiętuje je w historii. */
    fun runSearch(query: String) {
        val q = query.trim()
        searchQuery = q
        if (q.isEmpty()) return
        update { copy(recentSearches = (listOf(q) + recentSearches.filter { !it.equals(q, ignoreCase = true) }).take(8)) }
    }

    fun clearRecentSearches() = update { copy(recentSearches = emptyList()) }

    fun card(id: String): Card? = library.cards.firstOrNull { it.id == id }
}
