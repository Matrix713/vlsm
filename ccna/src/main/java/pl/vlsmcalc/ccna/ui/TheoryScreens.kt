package pl.vlsmcalc.ccna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import pl.vlsmcalc.ccna.content.SearchIndex
import pl.vlsmcalc.ccna.content.TextFold
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import pl.vlsmcalc.ccna.CcnaViewModel
import pl.vlsmcalc.ccna.Scope
import pl.vlsmcalc.ccna.content.Domain
import pl.vlsmcalc.ccna.content.Line
import pl.vlsmcalc.ccna.study.Study
import pl.vlsmcalc.wear.ui.Accent
import pl.vlsmcalc.wear.ui.ActionChip
import pl.vlsmcalc.wear.ui.ColorDot
import pl.vlsmcalc.wear.ui.Good
import pl.vlsmcalc.wear.ui.Muted
import pl.vlsmcalc.wear.ui.ScreenList
import pl.vlsmcalc.wear.ui.SubnetPalette
import pl.vlsmcalc.wear.ui.SurfaceDark
import pl.vlsmcalc.wear.ui.header
import pl.vlsmcalc.wear.ui.note
import pl.vlsmcalc.wear.ui.rememberTextInput

val Yellow = Color(0xFFFFE082)
private val CodeBg = Color(0xFF0E171C)

fun domainColor(id: Int): Color = SubnetPalette[(id - 1).mod(SubnetPalette.size)]

private val HighlightStyle = SpanStyle(background = Color(0xFFFFE082), color = Color.Black)

/**
 * **pogrubienie** → pogrubiony akcent, `kod` → czcionka o stałej szerokości.
 * [highlight] – ujednolicone słowa (TextFold), które zostaną podświetlone.
 */
fun rich(text: String, accent: Color = Accent, highlight: List<String> = emptyList()): AnnotatedString {
    val base = buildAnnotatedString {
        var bold = false
        var code = false
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> { bold = !bold; i += 2 }
                text[i] == '`' -> { code = !code; i++ }
                else -> {
                    val style = when {
                        code -> SpanStyle(fontFamily = FontFamily.Monospace, color = Yellow)
                        bold -> SpanStyle(fontWeight = FontWeight.Bold, color = accent)
                        else -> null
                    }
                    if (style != null) withStyle(style) { append(text[i]) } else append(text[i])
                    i++
                }
            }
        }
    }
    if (highlight.isEmpty()) return base
    val folded = TextFold.fold(base.text)
    return buildAnnotatedString {
        append(base)
        for (token in highlight) {
            var from = folded.indexOf(token)
            while (from >= 0) {
                addStyle(HighlightStyle, from, from + token.length)
                from = folded.indexOf(token, from + token.length)
            }
        }
    }
}

@Composable
fun HomeScreen(vm: CcnaViewModel, nav: NavController) {
    val p = vm.progress
    val due = vm.library.cards.size - Study.mastered(vm.library.cards, p.boxes)
    val wrong = vm.wrongQuestions().size
    ScreenList {
        header("CCNA 200-301")
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text("Gotowość: ${vm.readiness()}%", fontSize = 13.sp, color = Good, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                ProgressLine(vm.readiness() / 100f, Good)
            }
        }
        item {
            ActionChip("Szukaj", secondary = "teoria, fiszki i pytania", icon = Icons.Default.Search) {
                vm.searchQuery = ""
                nav.navigate(Routes.SEARCH)
            }
        }
        item { ActionChip("Teoria", secondary = "${vm.library.topics.size} tematów · ${p.read.size} przeczytanych", icon = Icons.AutoMirrored.Filled.ArrowForward, primary = true) { nav.navigate(Routes.DOMAINS) } }
        item { ActionChip("Fiszki", secondary = "do opanowania: $due", icon = Icons.Default.Refresh) { nav.navigate(Routes.FLASH_MENU) } }
        item { ActionChip("Quiz", secondary = "${vm.library.questions.size} pytań z wyjaśnieniami", icon = Icons.Default.PlayArrow) { nav.navigate(Routes.QUIZ_MENU) } }
        item { ActionChip("Egzamin próbny", secondary = "pytania wg wag CCNA", icon = Icons.Default.Check) { nav.navigate(Routes.EXAM_MENU) } }
        if (wrong > 0) {
            item {
                ActionChip("Powtórz błędy", secondary = "$wrong pytań", icon = Icons.Default.Refresh, color = Yellow) {
                    vm.startWrong()
                    nav.navigate(Routes.QUIZ)
                }
            }
        }
        item { ActionChip("Postęp", secondary = "obszary i wyniki egzaminów", icon = Icons.Default.Check) { nav.navigate(Routes.PROGRESS) } }
        item { ActionChip("Jak się uczyć", icon = Icons.AutoMirrored.Filled.ArrowForward) { nav.navigate(Routes.ABOUT) } }
    }
}

@Composable
fun ProgressLine(fraction: Float, color: Color) {
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF37474F)),
    ) {
        drawRect(color, size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}


@Composable
fun DomainChip(vm: CcnaViewModel, d: Domain, onClick: () -> Unit) {
    val read = d.topics.count { vm.isRead(it.id) }
    Chip(
        label = { Text(d.title, maxLines = 2, fontSize = 13.sp) },
        secondaryLabel = {
            Text(
                (if (d.weight > 0) "${d.weight}% egzaminu · " else "") + "$read/${d.topics.size} tematów",
                maxLines = 1, fontSize = 11.sp,
            )
        },
        icon = { ColorDot(domainColor(d.id), 12) },
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun DomainsScreen(vm: CcnaViewModel, nav: NavController) {
    ScreenList {
        header("Teoria")
        items(vm.library.domains) { d -> DomainChip(vm, d) { nav.navigate(Routes.domain(d.id)) } }
    }
}

@Composable
fun DomainScreen(vm: CcnaViewModel, nav: NavController, id: Int) {
    val d = vm.library.domain(id) ?: return
    ScreenList {
        header(d.title)
        items(d.topics) { t ->
            val acc = Study.accuracy(t.questions, vm.progress.stats)
            Chip(
                label = { Text(t.title, maxLines = 2, fontSize = 13.sp) },
                secondaryLabel = {
                    Text(
                        (if (vm.isRead(t.id)) "✓ przeczytane" else "nieprzeczytane") + (acc?.let { " · quiz $it%" } ?: ""),
                        maxLines = 1, fontSize = 11.sp,
                        color = if (vm.isRead(t.id)) Good else Muted,
                    )
                },
                onClick = { nav.navigate(Routes.topic(t.id)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ActionChip("Quiz z obszaru", secondary = "10 pytań", icon = Icons.Default.PlayArrow) {
                vm.startPractice(Scope.InDomain(id))
                nav.navigate(Routes.QUIZ)
            }
        }
        item {
            ActionChip("Fiszki z obszaru", icon = Icons.Default.Refresh) {
                vm.startFlash(Scope.InDomain(id))
                nav.navigate(Routes.FLASH)
            }
        }
    }
}

/** Blok wyświetlany jako jeden element listy. */
sealed interface LineBlock {
    data class Table(val rows: List<Line.Row>) : LineBlock
    data class Code(val text: String) : LineBlock
    data class Single(val line: Line) : LineBlock
}

/** Grupuje linie sekcji: tabele po 6 wierszy, kod po 8 linii, reszta osobno. */
fun groupLines(lines: List<Line>): List<LineBlock> {
    val out = mutableListOf<LineBlock>()
    var i = 0
    while (i < lines.size) {
        when (lines[i]) {
            is Line.Row -> {
                val rows = mutableListOf<Line.Row>()
                while (i < lines.size && lines[i] is Line.Row) { rows += lines[i] as Line.Row; i++ }
                rows.chunked(6).forEach { out += LineBlock.Table(it) }
            }
            is Line.Code -> {
                val code = mutableListOf<String>()
                while (i < lines.size && lines[i] is Line.Code) { code += lines[i].text; i++ }
                code.chunked(8).forEach { out += LineBlock.Code(it.joinToString("\n")) }
            }
            else -> { out += LineBlock.Single(lines[i]); i++ }
        }
    }
    return out
}

/** Elementy listy dla linii sekcji. */
fun ScalingLazyListScope.sectionLines(lines: List<Line>, highlight: List<String> = emptyList()) {
    groupLines(lines).forEach { block ->
        when (block) {
            is LineBlock.Table -> item { TableBox(block.rows, highlight) }
            is LineBlock.Code -> item { CodeBox(block.text) }
            is LineBlock.Single -> when (val l = block.line) {
                is Line.Bullet -> item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("•", color = Accent, fontSize = 13.sp)
                        Text(rich(l.text, highlight = highlight), fontSize = 13.sp, color = Color.White)
                    }
                }
                else -> item {
                    Text(rich(l.text, highlight = highlight), fontSize = 13.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
fun TableBox(rows: List<Line.Row>, highlight: List<String> = emptyList()) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Proporcje kolumn dobrane do najdłuższego klucza i wartości w tabeli
        val keyLen = rows.maxOf { it.key.replace("**", "").length }.toFloat()
        val valueLen = rows.maxOf { it.value.replace("**", "").length }.toFloat()
        val keyWeight = (keyLen / (keyLen + valueLen)).coerceIn(0.3f, 0.62f)
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(rich(r.key, highlight = highlight), fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(keyWeight))
                Text(rich(r.value, highlight = highlight), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f - keyWeight))
            }
        }
    }
}

@Composable
fun CodeBox(text: String) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = Yellow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun TopicScreen(vm: CcnaViewModel, nav: NavController, id: String, jumpTo: Int = -1, highlightQuery: String = "") {
    val t = vm.library.topic(id) ?: return
    val read = vm.isRead(id)
    val next = vm.nextTopic(id)
    val highlight = remember(highlightQuery) { TextFold.tokens(highlightQuery) }
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    LaunchedEffect(jumpTo) {
        if (jumpTo > 0) {
            // Pozycja nagłówka sekcji: 1 (nagłówek tematu) + elementy poprzednich sekcji
            val index = 1 + t.sections.take(jumpTo).sumOf { (if (it.title.isNotEmpty()) 1 else 0) + groupLines(it.lines).size }
            state.scrollToItem(index)
        }
    }
    ScreenList(state) {
        header(t.title)
        t.sections.forEach { s ->
            if (s.title.isNotEmpty()) {
                item {
                    Text(
                        rich(s.title, highlight = highlight), color = domainColor(t.domainId), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                    )
                }
            }
            sectionLines(s.lines, highlight)
        }
        item {
            ActionChip(
                if (read) "Przeczytane ✓" else "Oznacz jako przeczytane",
                icon = Icons.Default.Check,
                primary = !read,
            ) { vm.setRead(id, !read) }
        }
        item {
            ActionChip("Fiszki z tematu", secondary = "${t.cards.size} fiszek", icon = Icons.Default.Refresh) {
                vm.startFlash(Scope.InTopic(id))
                nav.navigate(Routes.FLASH)
            }
        }
        item {
            ActionChip("Quiz z tematu", secondary = "${t.questions.size} pytań", icon = Icons.Default.PlayArrow) {
                vm.startPractice(Scope.InTopic(id), size = t.questions.size)
                nav.navigate(Routes.QUIZ)
            }
        }
        if (next != null) {
            item {
                ActionChip("Dalej: ${next.title}", icon = Icons.AutoMirrored.Filled.ArrowForward) {
                    if (!read) vm.setRead(id, true)
                    nav.navigate(Routes.topic(next.id)) { popUpTo(Routes.TOPIC) { inclusive = true } }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: CcnaViewModel, nav: NavController) {
    val query = vm.searchQuery
    val results = remember(query) { vm.results(query) }
    val ask = rememberTextInput("Szukaj (teoria, fiszki, pytania)") { vm.runSearch(it) }
    ScreenList {
        header(if (query.isBlank()) "Szukaj" else "„$query”")
        item {
            ActionChip(
                if (query.isBlank()) "Wpisz / powiedz" else "Szukaj ponownie",
                secondary = "klawiatura lub głos",
                icon = Icons.Default.Search,
                primary = query.isBlank(),
            ) { ask() }
        }
        if (query.isBlank()) {
            val recent = vm.progress.recentSearches
            if (recent.isNotEmpty()) {
                header("Ostatnie")
                items(recent) { q -> SuggestionChip(q) { vm.runSearch(q) } }
                item { ActionChip("Wyczyść historię", color = Muted) { vm.clearRecentSearches() } }
            }
            header("Popularne")
            items(SearchIndex.SUGGESTIONS) { q -> SuggestionChip(q) { vm.runSearch(q) } }
            return@ScreenList
        }
        if (results.isEmpty) {
            note("Brak wyników. Spróbuj krótszego słowa, np. „ospf” zamiast „ospfv2 dr”.")
            return@ScreenList
        }
        note("Wyniki: ${results.total}")
        if (results.topics.isNotEmpty()) {
            header("Teoria (${results.topics.size})")
            items(results.topics) { h ->
                Chip(
                    label = { Text(h.topic.title, maxLines = 2, fontSize = 13.sp) },
                    secondaryLabel = { Text(rich(h.snippet, highlight = results.tokens), maxLines = 2, fontSize = 11.sp) },
                    icon = { ColorDot(domainColor(h.topic.domainId), 10) },
                    onClick = { nav.navigate(Routes.topic(h.topic.id, h.sectionIndex, query)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (results.cards.isNotEmpty()) {
            header("Fiszki (${results.cards.size})")
            items(results.cards) { c ->
                Chip(
                    label = { Text(rich(c.front, highlight = results.tokens), maxLines = 2, fontSize = 12.sp) },
                    secondaryLabel = { Text(rich(c.back, highlight = results.tokens), maxLines = 2, fontSize = 11.sp, color = Good) },
                    onClick = { nav.navigate(Routes.card(c.id)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (results.questions.isNotEmpty()) {
            header("Pytania (${results.questions.size})")
            items(results.questions) { q ->
                Chip(
                    label = { Text(rich(q.text, highlight = results.tokens), maxLines = 3, fontSize = 12.sp) },
                    secondaryLabel = { Text(vm.library.domain(q.domainId)?.title.orEmpty(), maxLines = 1, fontSize = 10.sp) },
                    onClick = { nav.navigate(Routes.question(q.id)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Chip(
        label = { Text(text, maxLines = 1, fontSize = 13.sp) },
        onClick = onClick,
        colors = ChipDefaults.childChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CardViewScreen(vm: CcnaViewModel, nav: NavController, id: String) {
    val c = vm.card(id) ?: return
    val topic = vm.library.topic(c.topicId)
    val highlight = remember(vm.searchQuery) { TextFold.tokens(vm.searchQuery) }
    ScreenList {
        header("Fiszka")
        item { Text(rich(c.front, highlight = highlight), fontSize = 15.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
        item {
            Text(
                rich(c.back, accent = Good, highlight = highlight), fontSize = 14.sp, color = Good,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceDark).padding(10.dp),
            )
        }
        if (topic != null) {
            item { ActionChip("Temat: ${topic.title}", icon = Icons.AutoMirrored.Filled.ArrowForward) { nav.navigate(Routes.topic(topic.id)) } }
        }
    }
}

@Composable
fun QuestionViewScreen(vm: CcnaViewModel, nav: NavController, id: String) {
    val q = vm.library.question(id) ?: return
    val topic = vm.library.topic(q.topicId)
    val highlight = remember(vm.searchQuery) { TextFold.tokens(vm.searchQuery) }
    val letters = listOf("A", "B", "C", "D", "E", "F")
    ScreenList {
        header("Pytanie")
        item { Text(rich(q.text, highlight = highlight), fontSize = 14.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
        q.options.forEachIndexed { i, opt ->
            val ok = i in q.correct
            item {
                Text(
                    (if (ok) "✓ " else "   ") + "${letters[i]}. " + opt.replace("**", ""),
                    fontSize = 12.sp, color = if (ok) Good else Muted,
                    fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
        }
        note(q.explanation, Color.White)
        if (topic != null) {
            item { ActionChip("Temat: ${topic.title}", icon = Icons.AutoMirrored.Filled.ArrowForward) { nav.navigate(Routes.topic(topic.id)) } }
        }
        item {
            ActionChip("Ćwicz ten temat", icon = Icons.Default.PlayArrow) {
                vm.startPractice(Scope.InTopic(q.topicId), size = topic?.questions?.size ?: 5)
                nav.navigate(Routes.QUIZ)
            }
        }
    }
}
