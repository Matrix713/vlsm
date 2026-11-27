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

/** **pogrubienie** → pogrubiony akcent, `kod` → czcionka o stałej szerokości. */
fun rich(text: String, accent: Color = Accent): AnnotatedString = buildAnnotatedString {
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

@Composable
fun HomeScreen(vm: CcnaViewModel, nav: NavController) {
    val p = vm.progress
    val due = vm.library.cards.size - Study.mastered(vm.library.cards, p.boxes)
    val wrong = vm.wrongQuestions().size
    val search = rememberTextInput("Szukaj w teorii") {
        vm.searchQuery = it
        nav.navigate(Routes.SEARCH)
    }
    ScreenList {
        header("CCNA 200-301")
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text("Gotowość: ${vm.readiness()}%", fontSize = 13.sp, color = Good, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                ProgressLine(vm.readiness() / 100f, Good)
            }
        }
        item { ActionChip("Teoria", secondary = "${vm.library.topics.size} tematów · ${p.read.size} przeczytanych", icon = Icons.Default.Search, primary = true) { nav.navigate(Routes.DOMAINS) } }
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
        item { ActionChip("Szukaj", secondary = "np. IPsec, DTP, NAT", icon = Icons.Default.Search) { search() } }
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

/** Elementy listy dla linii sekcji: tabele i kod są grupowane w bloki. */
fun ScalingLazyListScope.sectionLines(lines: List<Line>) {
    var i = 0
    while (i < lines.size) {
        when (val l = lines[i]) {
            is Line.Row -> {
                val rows = mutableListOf<Line.Row>()
                while (i < lines.size && lines[i] is Line.Row) { rows += lines[i] as Line.Row; i++ }
                rows.chunked(6).forEach { chunk -> item { TableBox(chunk) } }
            }
            is Line.Code -> {
                val code = mutableListOf<String>()
                while (i < lines.size && lines[i] is Line.Code) { code += lines[i].text; i++ }
                code.chunked(8).forEach { chunk -> item { CodeBox(chunk.joinToString("\n")) } }
            }
            is Line.Bullet -> {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("•", color = Accent, fontSize = 13.sp)
                        Text(rich(l.text), fontSize = 13.sp, color = Color.White)
                    }
                }
                i++
            }
            is Line.Paragraph -> {
                item { Text(rich(l.text), fontSize = 13.sp, color = Color.White, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) }
                i++
            }
        }
    }
}

@Composable
fun TableBox(rows: List<Line.Row>) {
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
                Text(rich(r.key), fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(keyWeight))
                Text(rich(r.value), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f - keyWeight))
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
fun TopicScreen(vm: CcnaViewModel, nav: NavController, id: String) {
    val t = vm.library.topic(id) ?: return
    val read = vm.isRead(id)
    val next = vm.nextTopic(id)
    ScreenList {
        header(t.title)
        t.sections.forEach { s ->
            if (s.title.isNotEmpty()) {
                item {
                    Text(
                        s.title, color = domainColor(t.domainId), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                    )
                }
            }
            sectionLines(s.lines)
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
                    nav.navigate(Routes.topic(next.id)) { popUpTo(Routes.topic(id)) { inclusive = true } }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: CcnaViewModel, nav: NavController) {
    val hits = vm.search(vm.searchQuery)
    val again = rememberTextInput("Szukaj w teorii") { vm.searchQuery = it }
    ScreenList {
        header("„${vm.searchQuery}”")
        item { ActionChip("Szukaj", secondary = "nowe zapytanie", icon = Icons.Default.Search) { again() } }
        if (hits.isEmpty()) note("Brak wyników")
        items(hits) { h ->
            Chip(
                label = { Text(h.topic.title, maxLines = 1, fontSize = 13.sp) },
                secondaryLabel = { Text(h.snippet, maxLines = 2, fontSize = 11.sp) },
                icon = { ColorDot(domainColor(h.topic.domainId), 10) },
                onClick = { nav.navigate(Routes.topic(h.topic.id)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
