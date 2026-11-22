package pl.vlsmcalc.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Flsm
import pl.vlsmcalc.wear.core.FlsmMode
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.core.Summarizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.state
    ScreenList {
        header("VLSM")
        item {
            ActionChip(
                "Kalkulator VLSM",
                secondary = "${s.vlsmBase} · ${s.subnets.size} ${plural(s.subnets.size.toLong(), "podsieć", "podsieci", "podsieci")}",
                icon = Icons.AutoMirrored.Filled.List,
                primary = true,
            ) { nav.navigate(Routes.VLSM) }
        }
        item { ActionChip("Kalkulator IP", secondary = s.ipCalc, icon = Icons.Default.Search) { nav.navigate(Routes.IPCALC) } }
        item { ActionChip("Podział równy (FLSM)", secondary = s.flsmBase, icon = Icons.Default.Menu) { nav.navigate(Routes.FLSM) } }
        item { ActionChip("Sumaryzacja tras", secondary = "supernetting", icon = Icons.Default.Share) { nav.navigate(Routes.SUMMARY) } }
        item { ActionChip("Ta sama sieć?", secondary = "sprawdź 2 adresy", icon = Icons.Default.CheckCircle) { nav.navigate(Routes.CHECK) } }
        item { ActionChip("Ile hostów → prefiks", secondary = hostsLabel(s.hostsTool), icon = Icons.Default.Person) { nav.navigate(Routes.HOSTS) } }
        item { ActionChip("Tabela masek", secondary = "/0 – /32", icon = Icons.Default.Build) { nav.navigate(Routes.MASKS) } }
        item {
            ActionChip("Zapisane projekty", secondary = "${s.saved.size}", icon = Icons.Default.Star) { nav.navigate(Routes.SAVED) }
        }
        item { ActionChip("Pomoc", icon = Icons.Default.Info) { nav.navigate(Routes.HELP) } }
    }
}

@Composable
fun DetailScreen(cidr: Cidr, title: String, requested: Long?) {
    ScreenList {
        header(title)
        item {
            Text(cidr.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        cidrDetails(cidr, requested, showAddress = true)
    }
}

@Composable
fun IpCalcScreen(vm: AppViewModel, nav: NavController) {
    val c = Cidr.parse(vm.state.ipCalc)
    ScreenList {
        header("Kalkulator IP")
        item {
            ValueChip("Adres / prefiks", vm.state.ipCalc, primary = true) {
                vm.openInput(networkInput("Adres IP", vm.state.ipCalc) { vm.setIpCalc(it.toString()) })
                nav.navigate(Routes.KEYPAD)
            }
        }
        if (c != null) {
            item { PrefixRow(c.prefix) { vm.adjustIpCalcPrefix(it) } }
            cidrDetails(c, showAddress = true)
        }
    }
}

@Composable
fun FlsmScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.state
    val outcome by remember { derivedStateOf { vm.computeFlsm() } }
    ScreenList {
        header("Podział FLSM")
        item {
            ValueChip("Sieć do podziału", s.flsmBase) {
                vm.openInput(networkInput("Sieć", s.flsmBase) { vm.setFlsmBase(it.normalized().toString()) })
                nav.navigate(Routes.KEYPAD)
            }
        }
        item {
            ActionChip("Tryb: ${s.flsmMode.label}", secondary = "dotknij, aby zmienić", icon = Icons.Default.Refresh) {
                vm.cycleFlsmMode()
            }
        }
        item {
            ValueChip(s.flsmMode.valueLabel, if (s.flsmMode == FlsmMode.BY_PREFIX) "/${s.flsmValue}" else fmt(s.flsmValue)) {
                vm.openInput(
                    InputRequest(
                        title = s.flsmMode.valueLabel,
                        mode = InputMode.NUMBER,
                        initial = s.flsmValue.toString(),
                        apply = { t ->
                            val n = t.toLongOrNull()
                            if (n == null) "Podaj liczbę" else {
                                val base = Cidr.parse(s.flsmBase)
                                val res = base?.let { Flsm.plan(it, s.flsmMode, n) }
                                if (res is Flsm.Outcome.Error) res.message else { vm.setFlsmValue(n); null }
                            }
                        },
                    ),
                )
                nav.navigate(Routes.KEYPAD)
            }
        }
        when (val o = outcome) {
            is Flsm.Outcome.Error -> note(o.message, Bad)
            is Flsm.Outcome.Ok -> {
                val plan = o.plan
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${fmt(plan.count)} × /${plan.newPrefix}",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent,
                        )
                        Text("${hostsLabel(Ip.usableHosts(plan.newPrefix))} w każdej", fontSize = 12.sp)
                        Text("maska ${Ip.format(Ip.mask(plan.newPrefix))}", fontSize = 12.sp, color = Muted)
                        Text("pożyczone bity: ${plan.newPrefix - plan.base.prefix}", fontSize = 12.sp, color = Muted)
                    }
                }
                val shown = minOf(plan.count, MAX_LIST).toInt()
                items(shown) { i ->
                    val sub = plan.subnet(i.toLong())
                    Chip(
                        label = { Text("#${i + 1}  ${sub.networkString()}", maxLines = 1, fontSize = 13.sp) },
                        secondaryLabel = {
                            Text(
                                if (sub.prefix >= 31) "${Ip.format(sub.firstHost)} – ${Ip.shortForm(sub.lastHost, sub.network)}"
                                else "${Ip.shortForm(sub.firstHost, sub.network)} – ${Ip.shortForm(sub.lastHost, sub.network)} · bc ${Ip.shortForm(sub.broadcast, sub.network)}",
                                maxLines = 1, fontSize = 11.sp,
                            )
                        },
                        onClick = { nav.navigate(Routes.detail(sub, "Podsieć #${i + 1}")) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (plan.count > MAX_LIST) note("Pokazano ${fmt(MAX_LIST)} z ${fmt(plan.count)}")
            }
        }
    }
}

private const val MAX_LIST = 1024L

@Composable
fun SummaryScreen(vm: AppViewModel, nav: NavController) {
    val nets = vm.state.summaryNets
    val result by remember { derivedStateOf { vm.computeSummary() } }
    fun edit(index: Int, initial: String) {
        vm.openInput(
            InputRequest(
                title = if (index < 0) "Nowa sieć" else "Sieć (puste = usuń)",
                mode = InputMode.IP_CIDR,
                initial = initial,
                allowEmpty = index >= 0,
                preview = { t -> Cidr.parse(t)?.let { "→ ${it.normalized()}" } },
                apply = { t ->
                    if (t.isEmpty()) { vm.setSummaryNet(index, null); null } else {
                        val c = Cidr.parse(t)
                        if (c == null) "Błędna sieć" else { vm.setSummaryNet(index, c.normalized().toString()); null }
                    }
                },
            ),
        )
        nav.navigate(Routes.KEYPAD)
    }
    ScreenList {
        header("Sumaryzacja")
        itemsIndexed(nets) { i, n ->
            Chip(
                label = { Text(n, maxLines = 1) },
                icon = { Icon(Icons.Default.Edit, null, tint = Muted) },
                onClick = { edit(i, n) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                label = { Text("Dodaj sieć") },
                icon = { Icon(Icons.Default.Add, null, tint = Accent) },
                onClick = { edit(-1, "") },
                colors = ChipDefaults.childChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val r = result
        if (r != null) {
            item {
                Card(onClick = { nav.navigate(Routes.detail(r.summary, "Trasa sumaryczna")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Trasa sumaryczna", fontSize = 11.sp, color = Muted)
                    Text(r.summary.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Accent)
                    Text(Ip.format(r.summary.mask), fontSize = 12.sp, color = Muted)
                    if (r.extraAddresses == 0L) {
                        Text("Dokładne pokrycie ✓", fontSize = 12.sp, color = Good)
                    } else {
                        Text("+${fmt(r.extraAddresses)} adr. spoza listy", fontSize = 12.sp, color = Bad)
                    }
                }
            }
            if (r.aggregated.size > 1 || r.extraAddresses > 0) {
                header("Agregacja dokładna (${r.aggregated.size})")
                items(r.aggregated) { a ->
                    Chip(
                        label = { Text(a.toString(), maxLines = 1) },
                        onClick = { nav.navigate(Routes.detail(a, "Agregat")) },
                        colors = ChipDefaults.childChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            note("Dodaj sieci, aby wyznaczyć wspólną trasę.")
        }
        item { ActionChip("Przykład", secondary = "4 × /24", icon = Icons.Default.Refresh) { vm.loadSummaryExample() } }
        if (nets.isNotEmpty()) {
            item { ActionChip("Wyczyść", icon = Icons.Default.Delete, color = Bad) { vm.clearSummary() } }
        }
    }
}

@Composable
fun CheckScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.state
    val a = Cidr.parse(s.checkA)
    val b = Ip.parse(s.checkB)
    ScreenList {
        header("Ta sama sieć?")
        item {
            ValueChip("Adres A / prefiks", s.checkA) {
                vm.openInput(networkInput("Adres A", s.checkA) { vm.setCheckA(it.toString()) })
                nav.navigate(Routes.KEYPAD)
            }
        }
        item {
            ValueChip("Adres B", s.checkB) {
                vm.openInput(
                    InputRequest(
                        title = "Adres B",
                        mode = InputMode.IP,
                        initial = s.checkB,
                        apply = { t -> if (Ip.parse(t) == null) "Błędny adres" else { vm.setCheckB(t); null } },
                    ),
                )
                nav.navigate(Routes.KEYPAD)
            }
        }
        if (a != null && b != null) {
            item { PrefixRow(a.prefix) { vm.adjustCheckPrefix(it) } }
            val netA = a.normalized()
            val netB = Cidr(b, a.prefix).normalized()
            val same = netA == netB
            item {
                Card(onClick = { nav.navigate(Routes.detail(netA, "Sieć A")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (same) "✓ Ta sama sieć" else "✗ Różne sieci",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (same) Good else Bad,
                    )
                    Text("A: ${netA}", fontSize = 12.sp)
                    if (!same) Text("B: ${netB}", fontSize = 12.sp)
                    val warnings = buildList {
                        if (a.prefix < 31) {
                            if (a.address == netA.network) add("A to adres sieci")
                            if (a.address == netA.broadcast) add("A to broadcast")
                            if (same && b == netA.network) add("B to adres sieci")
                            if (same && b == netA.broadcast) add("B to broadcast")
                        }
                    }
                    warnings.forEach { Text("⚠ $it", fontSize = 11.sp, color = Color(0xFFFFE082)) }
                }
            }
            if (!same) {
                val common = Summarizer.summarize(listOf(netA, netB))!!.summary
                item {
                    InfoRow("Wspólna nadsieć", common.toString(), valueColor = Accent)
                }
                item {
                    InfoRow("Prefiks, przy którym są razem", "/${common.prefix} lub krótszy")
                }
            }
        }
    }
}

@Composable
fun MasksScreen(vm: AppViewModel, nav: NavController) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 25)
    ScreenList(state) {
        header("Maski /CIDR")
        items(33) { p ->
            Chip(
                label = { Text("/$p  ${Ip.format(Ip.mask(p))}", maxLines = 1, fontSize = 13.sp) },
                secondaryLabel = {
                    Text("${fmt(Ip.blockSize(p))} adr · ${fmt(Ip.usableHosts(p))} h · wc ${Ip.format(Ip.wildcard(p))}", maxLines = 1, fontSize = 10.sp)
                },
                onClick = {
                    vm.setIpCalcPrefix(p)
                    nav.navigate(Routes.IPCALC)
                },
                colors = if (p % 8 == 0) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HostsToolScreen(vm: AppViewModel, nav: NavController) {
    val n = vm.state.hostsTool
    val p = Ip.prefixForHosts(n)
    ValueStepper(
        value = n,
        onValueChange = { vm.setHostsTool(it) },
        min = 1,
        max = AppViewModel.MAX_HOSTS,
        title = "Potrzebne hosty",
        onTitleClick = null,
        lines = if (p != null) listOf(
            "/$p · ${Ip.format(Ip.mask(p))}" to Accent,
            "blok ${fmt(Ip.blockSize(p))} · zapas ${fmt(Ip.usableHosts(p) - n)}" to Muted,
        ) else listOf("Za dużo" to Bad),
        onValueClick = {
            vm.openInput(hostsInput("Liczba hostów", n, false) { vm.setHostsTool(it) })
            nav.navigate(Routes.KEYPAD)
        },
    )
}

private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("pl-PL"))

@Composable
fun SavedScreen(vm: AppViewModel, nav: NavController) {
    val saved = vm.state.saved
    ScreenList {
        header("Zapisane")
        if (saved.isEmpty()) note("Brak projektów. Zapisz je na ekranie VLSM.")
        itemsIndexed(saved) { i, p ->
            Chip(
                label = { Text(p.name, maxLines = 1) },
                secondaryLabel = {
                    Text("${p.subnets.size} ${plural(p.subnets.size.toLong(), "podsieć", "podsieci", "podsieci")} · ${dateFormat.format(Date(p.savedAt))}", maxLines = 1, fontSize = 11.sp)
                },
                onClick = { nav.navigate(Routes.savedItem(i)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SavedItemScreen(vm: AppViewModel, nav: NavController, index: Int) {
    val p = vm.state.saved.getOrNull(index) ?: return
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(3000); confirmDelete = false } }
    val rename = rememberTextInput("Nazwa projektu") { vm.renameProject(index, it) }
    ScreenList {
        header(p.name)
        item {
            ActionChip("Wczytaj do VLSM", secondary = p.base, icon = Icons.AutoMirrored.Filled.List, primary = true) {
                vm.loadProject(index)
                nav.navigate(Routes.VLSM) { popUpTo(Routes.HOME) }
            }
        }
        items(p.subnets) { s ->
            InfoRow(s.name, hostsLabel(s.hosts))
        }
        item { ActionChip("Zmień nazwę", icon = Icons.Default.Edit) { rename() } }
        item {
            ActionChip(if (confirmDelete) "Na pewno? Dotknij" else "Usuń", icon = Icons.Default.Delete, color = Bad) {
                if (confirmDelete) {
                    vm.deleteProject(index)
                    nav.popBackStack()
                } else confirmDelete = true
            }
        }
    }
}

@Composable
fun HelpScreen() {
    ScreenList {
        header("Pomoc")
        note("VLSM dzieli sieć na podsieci o różnych rozmiarach. Największe podsieci są przydzielane najpierw, więc nic się nie marnuje.", Color.White)
        header("Sterowanie")
        note("• Bezel: przewijanie list i zmiana liczby hostów (szybki obrót = skok ×10).")
        note("• Przesuń w prawo, aby wrócić.")
        note("• Klawiatura sama wstawia kropki i ukośnik. Przytrzymaj ⌫, by wyczyścić.")
        note("• Dotknij nazwy podsieci, by ją zmienić (klawiatura lub głos).")
        note("• Dotknij dowolnej podsieci w wynikach, by zobaczyć szczegóły i zapis binarny.")
        header("Formaty")
        note("192.168.1.0/24\n10.0.0.0/255.0.0.0\nBez prefiksu: maska klasowa.")
        note("Wszystko zapisuje się automatycznie.", Good)
    }
}
