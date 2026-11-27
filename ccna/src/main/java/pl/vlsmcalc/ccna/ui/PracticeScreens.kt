package pl.vlsmcalc.ccna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import pl.vlsmcalc.ccna.CcnaViewModel
import pl.vlsmcalc.ccna.QuizResult
import pl.vlsmcalc.ccna.Scope
import pl.vlsmcalc.ccna.study.Presented
import pl.vlsmcalc.ccna.study.Study
import pl.vlsmcalc.wear.ui.Accent
import pl.vlsmcalc.wear.ui.ActionChip
import pl.vlsmcalc.wear.ui.Bad
import pl.vlsmcalc.wear.ui.Good
import pl.vlsmcalc.wear.ui.InfoRow
import pl.vlsmcalc.wear.ui.Muted
import pl.vlsmcalc.wear.ui.ScreenList
import pl.vlsmcalc.wear.ui.header
import pl.vlsmcalc.wear.ui.note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Letters = listOf("A", "B", "C", "D", "E", "F")
private val CorrectBg = Color(0xFF1E4D2B)
private val WrongBg = Color(0xFF5C2323)
private val SelectedBg = Color(0xFF0D47A1)

// ---------- Menu ----------

@Composable
fun FlashMenuScreen(vm: CcnaViewModel, nav: NavController) {
    fun start(scope: Scope) {
        vm.startFlash(scope)
        nav.navigate(Routes.FLASH)
    }
    val all = vm.library.cards
    ScreenList {
        header("Fiszki")
        item {
            ActionChip(
                "Wszystkie – najsłabsze najpierw",
                secondary = "opanowane ${Study.mastered(all, vm.progress.boxes)}/${all.size}",
                icon = Icons.Default.PlayArrow, primary = true,
            ) { start(Scope.All) }
        }
        items(vm.library.domains) { d ->
            val cards = d.topics.flatMap { it.cards }
            DomainPickChip(d.id, d.title, "opanowane ${Study.mastered(cards, vm.progress.boxes)}/${cards.size}") { start(Scope.InDomain(d.id)) }
        }
        note("Fiszka uznana za „wiem” 3 razy z rzędu jest opanowana. „Nie wiem” cofa ją na początek.")
    }
}

@Composable
fun QuizMenuScreen(vm: CcnaViewModel, nav: NavController) {
    fun start(scope: Scope) {
        vm.startPractice(scope)
        nav.navigate(Routes.QUIZ)
    }
    val wrong = vm.wrongQuestions().size
    ScreenList {
        header("Quiz")
        item {
            ActionChip("Mieszany – 10 pytań", secondary = "najpierw nowe i błędne", icon = Icons.Default.PlayArrow, primary = true) { start(Scope.All) }
        }
        if (wrong > 0) {
            item {
                ActionChip("Powtórz błędy", secondary = "$wrong pytań", icon = Icons.Default.Refresh, color = Yellow) {
                    vm.startWrong()
                    nav.navigate(Routes.QUIZ)
                }
            }
        }
        items(vm.library.domains) { d ->
            val qs = d.topics.flatMap { it.questions }
            val acc = Study.accuracy(qs, vm.progress.stats)
            DomainPickChip(d.id, d.title, "${qs.size} pytań" + (acc?.let { " · $it%" } ?: "")) { start(Scope.InDomain(d.id)) }
        }
    }
}

@Composable
fun ExamMenuScreen(vm: CcnaViewModel, nav: NavController) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("pl-PL")) }
    ScreenList {
        header("Egzamin próbny")
        note("Pytania losowane według wag obszarów CCNA. Wynik i wyjaśnienia dopiero na końcu – jak na egzaminie.")
        listOf(20 to "szybki", 40 to "średni", 60 to "pełny trening").forEach { (n, label) ->
            item {
                ActionChip("$n pytań", secondary = label, icon = Icons.Default.PlayArrow, primary = n == 40) {
                    vm.startExam(n)
                    nav.navigate(Routes.QUIZ)
                }
            }
        }
        if (vm.progress.exams.isNotEmpty()) {
            header("Ostatnie wyniki")
            items(vm.progress.exams.take(5)) { e ->
                InfoRow(fmt.format(Date(e.at)), "${e.correct}/${e.total} · ${e.percent}%", valueColor = if (e.percent >= 85) Good else if (e.percent >= 70) Yellow else Bad)
            }
        }
    }
}

@Composable
private fun DomainPickChip(id: Int, title: String, secondary: String, onClick: () -> Unit) {
    Chip(
        label = { Text(title, maxLines = 2, fontSize = 13.sp) },
        secondaryLabel = { Text(secondary, maxLines = 1, fontSize = 11.sp) },
        icon = { pl.vlsmcalc.wear.ui.ColorDot(domainColor(id), 12) },
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Fiszki ----------

@Composable
fun FlashScreen(vm: CcnaViewModel, nav: NavController) {
    val s = vm.flash ?: return
    val card = s.current
    key(s.index) {
        ScreenList {
            if (card == null) {
                header("Koniec sesji")
                item { InfoRow("Wiem / nie wiem", "${s.known} / ${s.unknown}", valueColor = Good) }
                item { ActionChip("Kolejne fiszki", icon = Icons.Default.Refresh, primary = true) { vm.startFlash(Scope.All) } }
                item { ActionChip("Koniec") { nav.popBackStack() } }
                return@ScreenList
            }
            header("Fiszka ${s.index + 1}/${s.cards.size}")
            item {
                val topic = vm.library.topic(card.topicId)
                Text(topic?.title.orEmpty(), fontSize = 10.sp, color = Muted, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            item {
                Card(onClick = { vm.flipCard() }, modifier = Modifier.fillMaxWidth()) {
                    val text = if (s.flipped) card.back else card.front
                    Text(
                        rich(text),
                        fontSize = when {
                            text.length < 50 -> 16.sp
                            text.length < 110 -> 14.sp
                            else -> 12.sp
                        },
                        color = if (s.flipped) Good else Color.White,
                        fontWeight = if (s.flipped) FontWeight.Normal else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            if (!s.flipped) {
                note("Dotknij karty, aby zobaczyć odpowiedź")
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = { vm.answerCard(false) }, colors = ButtonDefaults.buttonColors(backgroundColor = WrongBg)) {
                            Icon(Icons.Default.Close, "Nie wiem", tint = Bad)
                        }
                        Button(onClick = { vm.answerCard(true) }, colors = ButtonDefaults.buttonColors(backgroundColor = CorrectBg)) {
                            Icon(Icons.Default.Check, "Wiem", tint = Good)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("nie wiem", fontSize = 10.sp, color = Muted)
                        Text("wiem", fontSize = 10.sp, color = Muted)
                    }
                }
            }
        }
    }
}

// ---------- Quiz / egzamin ----------

@Composable
fun QuizScreen(vm: CcnaViewModel, nav: NavController) {
    val s = vm.quiz
    if (s == null) {
        // Sesja zakończona – przejdź do wyniku
        LaunchedEffect(Unit) {
            if (vm.result != null) nav.navigate(Routes.RESULT) { popUpTo(Routes.QUIZ) { inclusive = true } } else nav.popBackStack()
        }
        return
    }
    val item = s.current
    val q = item.question
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(confirmEnd) { if (confirmEnd) { delay(3000); confirmEnd = false } }

    fun goNext() {
        if (vm.next()) nav.navigate(Routes.RESULT) { popUpTo(Routes.QUIZ) { inclusive = true } }
    }

    key(s.index) {
        ScreenList {
            header("${if (s.exam) "Egzamin" else "Pytanie"} ${s.index + 1}/${s.items.size}")
            item {
                Text(
                    vm.library.domain(q.domainId)?.title.orEmpty(), fontSize = 10.sp, color = domainColor(q.domainId),
                    maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(rich(q.text), fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
            }
            if (q.isMulti) note("Zaznacz ${q.correct.size} odpowiedzi", Yellow)
            itemsIndexed(item.options) { i, option ->
                OptionChip(i, option, item, s.selected, s.answered) { vm.toggleOption(i) }
            }
            if (s.answered) {
                val ok = item.isCorrect(s.selected)
                item {
                    Text(
                        if (ok) "✓ Dobrze!" else "✗ Poprawnie: " + item.correctShown.sorted().joinToString { Letters[it] },
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (ok) Good else Bad,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                note(q.explanation, Color.White)
                item { ActionChip(if (s.isLast) "Wynik" else "Dalej", icon = Icons.Default.PlayArrow, primary = true) { goNext() } }
            } else if (s.exam || q.isMulti) {
                item {
                    ActionChip(
                        when {
                            !s.exam -> "Sprawdź"
                            s.isLast -> "Zakończ i oceń"
                            else -> "Dalej"
                        },
                        icon = Icons.Default.PlayArrow,
                        primary = true,
                        enabled = s.selected.isNotEmpty(),
                    ) { if (s.exam) goNext() else vm.submit() }
                }
            }
            if (s.exam && !s.isLast) {
                item {
                    ActionChip(if (confirmEnd) "Na pewno? Dotknij" else "Zakończ wcześniej", color = Muted) {
                        if (confirmEnd) {
                            vm.finishEarly()
                            nav.navigate(Routes.RESULT) { popUpTo(Routes.QUIZ) { inclusive = true } }
                        } else confirmEnd = true
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionChip(i: Int, option: String, item: Presented, selected: Set<Int>, answered: Boolean, onClick: () -> Unit) {
    val isSelected = i in selected
    val isCorrect = i in item.correctShown
    val bg = when {
        answered && isCorrect -> CorrectBg
        answered && isSelected -> WrongBg
        isSelected -> SelectedBg
        else -> Color(0xFF1E2A31)
    }
    Chip(
        label = { Text(rich(option), fontSize = 12.sp, maxLines = 6, color = Color.White) },
        icon = {
            Box(
                Modifier.size(22.dp).background(if (isSelected) Accent else Color(0xFF37474F), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(Letters[i], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.Black else Color.White) }
        },
        onClick = onClick,
        enabled = !answered,
        colors = ChipDefaults.chipColors(backgroundColor = bg, disabledBackgroundColor = bg, contentColor = Color.White, disabledContentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Wynik ----------

@Composable
fun ResultScreen(vm: CcnaViewModel, nav: NavController) {
    val r: QuizResult = vm.result ?: return
    val color = when {
        r.percent >= 85 -> Good
        r.percent >= 70 -> Yellow
        else -> Bad
    }
    ScreenList {
        header(r.title)
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("${r.percent}%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("${r.correct}/${r.total} poprawnych", fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("czas ${r.seconds / 60} min ${r.seconds % 60} s", fontSize = 11.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ProgressLine(r.percent / 100f, color)
            }
        }
        if (r.exam) {
            note(
                if (r.percent >= 85) "Świetnie – utrzymuj ten poziom w kolejnych próbach."
                else "Cisco nie publikuje progu zdawalności – celuj stabilnie w 85%+ w egzaminach próbnych.",
                color,
            )
        }
        if (r.perDomain.size > 1) {
            header("Obszary")
            r.perDomain.forEach { (d, pair) ->
                item {
                    val (c, t) = pair
                    InfoRow(vm.library.domain(d)?.title.orEmpty(), "$c/$t · ${c * 100 / t}%", valueColor = if (c * 100 / t >= 85) Good else if (c * 100 / t >= 70) Yellow else Bad)
                }
            }
        }
        if (r.wrong.isNotEmpty()) {
            item { ActionChip("Przejrzyj błędy (${r.wrong.size})", icon = Icons.Default.Refresh, primary = true) { nav.navigate(Routes.REVIEW) } }
        }
        item { ActionChip("Menu główne") { nav.popBackStack(Routes.HOME, inclusive = false) } }
    }
}

@Composable
fun ReviewScreen(vm: CcnaViewModel) {
    val r = vm.result ?: return
    ScreenList {
        header("Błędy")
        r.wrong.forEachIndexed { n, (item, chosen) ->
            item {
                Text(
                    "${n + 1}. ", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                )
            }
            item { Text(rich(item.question.text), fontSize = 13.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
            item.options.forEachIndexed { i, opt ->
                val correct = i in item.correctShown
                val picked = i in chosen
                if (correct || picked) {
                    item {
                        Text(
                            (if (correct) "✓ " else "✗ ") + "${Letters[i]}. " + opt.replace("**", ""),
                            fontSize = 12.sp, color = if (correct) Good else Bad,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        )
                    }
                }
            }
            note(item.question.explanation, Muted)
        }
    }
}

// ---------- Postęp ----------

@Composable
fun ProgressScreen(vm: CcnaViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(confirmReset) { if (confirmReset) { delay(3000); confirmReset = false } }
    val p = vm.progress
    ScreenList {
        header("Postęp")
        item {
            InfoRow("Gotowość (szacunek)", "${vm.readiness()}%", valueColor = Good)
        }
        vm.library.domains.forEach { d ->
            val topics = d.topics
            val cards = topics.flatMap { it.cards }
            val qs = topics.flatMap { it.questions }
            val read = topics.count { it.id in p.read }
            val mastered = Study.mastered(cards, p.boxes)
            val acc = Study.accuracy(qs, p.stats)
            item {
                InfoRow(
                    d.title,
                    "teoria $read/${topics.size} · fiszki $mastered/${cards.size}\nquiz ${acc?.let { "$it%" } ?: "—"}",
                    valueColor = Color.White,
                )
            }
        }
        item {
            ActionChip(if (confirmReset) "Na pewno? Dotknij" else "Wyzeruj postęp", icon = Icons.Default.Delete, color = Bad) {
                if (confirmReset) { vm.resetProgress(); confirmReset = false } else confirmReset = true
            }
        }
    }
}

@Composable
fun AboutScreen() {
    ScreenList {
        header("Jak się uczyć")
        note("1. Przeczytaj temat w Teorii i oznacz go jako przeczytany.", Color.White)
        note("2. Przerób fiszki z tematu – aż wszystkie będą opanowane.", Color.White)
        note("3. Zrób quiz z tematu i czytaj wyjaśnienia przy błędach.", Color.White)
        note("4. Co kilka dni: „Powtórz błędy” i egzamin próbny 40–60 pytań.", Color.White)
        note("Obszary mają wagi jak na egzaminie CCNA 200-301 v1.1: łączność IP 25%, podstawy i dostęp po 20%, bezpieczeństwo 15%, usługi i automatyzacja po 10%.")
        note("Egzamin to też zadania praktyczne (konfiguracja w symulatorze) – ćwicz CLI w Packet Tracer lub labie.", Yellow)
        note("Na prawdziwym egzaminie zegarek trzeba zostawić w szafce – ta aplikacja służy do nauki.", Muted)
    }
}
