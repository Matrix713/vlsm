package pl.vlsmcalc.wear.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import pl.vlsmcalc.wear.core.Allocation
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.core.VlsmOutcome
import pl.vlsmcalc.wear.core.VlsmResult

fun subnetColor(index: Int) = SubnetPalette[index % SubnetPalette.size]

/** Żądanie klawiatury dla adresu sieci z podglądem maski / adresu sieci. */
fun networkInput(title: String, initial: String, onValid: (Cidr) -> Unit) = InputRequest(
    title = title,
    mode = InputMode.IP_CIDR,
    initial = initial,
    preview = { t ->
        Cidr.parse(t)?.let { c ->
            if (t.contains('/')) "sieć ${Ip.format(c.network)} · ${fmt(c.usableHosts)} h"
            else "bez prefiksu → /${c.prefix} (klasowo)"
        }
    },
    apply = { t ->
        val c = Cidr.parse(t)
        if (c == null) "Błędny adres" else { onValid(c); null }
    },
)

fun hostsInput(title: String, initial: Long, allowSmall: Boolean, onValid: (Long) -> Unit) = InputRequest(
    title = title,
    mode = InputMode.NUMBER,
    initial = initial.toString(),
    preview = { t ->
        t.toLongOrNull()?.let { n -> Ip.prefixForHosts(n, allowSmall)?.let { "→ /$it · ${fmt(Ip.blockSize(it))} adr." } }
    },
    apply = { t ->
        val n = t.toLongOrNull()
        when {
            n == null || n < 1 -> "Minimum 1 host"
            n > AppViewModel.MAX_HOSTS -> "Za dużo dla IPv4"
            else -> { onValid(n); null }
        }
    },
)

@Composable
fun VlsmEditorScreen(vm: AppViewModel, nav: NavController) {
    val s = vm.state
    val context = LocalContext.current
    val outcome by remember { derivedStateOf { vm.computeVlsm() } }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) {
        if (confirmClear) { delay(3000); confirmClear = false }
    }

    ScreenList {
        header("VLSM")
        item {
            ValueChip("Sieć bazowa", s.vlsmBase) {
                vm.openInput(networkInput("Sieć bazowa", s.vlsmBase) { vm.setVlsmBase(it.normalized().toString()) })
                nav.navigate(Routes.KEYPAD)
            }
        }
        itemsIndexed(s.subnets) { index, sub ->
            val prefix = Ip.prefixForHosts(sub.hosts, s.allowSmall)
            Chip(
                label = { Text(sub.name, maxLines = 1) },
                secondaryLabel = { Text("${hostsLabel(sub.hosts)} → /${prefix ?: "?"}", maxLines = 1, fontSize = 12.sp) },
                icon = { ColorDot(subnetColor(index), 12) },
                onClick = { nav.navigate(Routes.subnetEdit(index)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                label = { Text("Dodaj podsieć") },
                icon = { androidx.wear.compose.material.Icon(Icons.Default.Add, null, tint = Accent) },
                onClick = {
                    val i = vm.addSubnet()
                    nav.navigate(Routes.subnetEdit(i))
                },
                colors = ChipDefaults.childChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val status = when (val o = outcome) {
                is VlsmOutcome.Success -> "Zajmie ${fmt(o.result.usedAddresses)}/${fmt(o.result.totalAddresses)} adr."
                is VlsmOutcome.DoesNotFit -> "Nie zmieści się! " + (o.suggestedPrefix?.let { "Potrzeba /$it" } ?: "")
                is VlsmOutcome.Invalid -> o.message
            }
            ActionChip(
                label = "Oblicz",
                secondary = status,
                icon = Icons.Default.Check,
                primary = true,
                enabled = s.subnets.isNotEmpty(),
            ) { nav.navigate(Routes.VLSM_RESULT) }
        }
        header("Opcje")
        item {
            SwitchChip("Największe najpierw", if (s.largestFirst) "klasyczny VLSM" else "kolejność wpisania", s.largestFirst) {
                vm.setLargestFirst(it)
            }
        }
        item {
            SwitchChip("/31 i /32 dla 1–2 hostów", "łącza punkt-punkt", s.allowSmall) { vm.setAllowSmall(it) }
        }
        item {
            ActionChip("Zapisz projekt", icon = Icons.Default.Star, enabled = s.subnets.isNotEmpty()) {
                vm.saveCurrentProject()
                Toast.makeText(context, "Zapisano", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            ActionChip("Wczytaj przykład", secondary = "6 podsieci w /24", icon = Icons.Default.Refresh) {
                vm.loadVlsmExample()
            }
        }
        item {
            ActionChip(
                if (confirmClear) "Na pewno? Dotknij" else "Wyczyść podsieci",
                icon = Icons.Default.Delete,
                color = Bad,
                enabled = s.subnets.isNotEmpty(),
            ) {
                if (confirmClear) { vm.clearVlsm(); confirmClear = false } else confirmClear = true
            }
        }
    }
}

@Composable
fun SubnetEditScreen(vm: AppViewModel, nav: NavController, index: Int) {
    val sub = vm.state.subnets.getOrNull(index) ?: return
    val allowSmall = vm.state.allowSmall
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) { delay(3000); confirmDelete = false }
    }
    val rename = rememberTextInput("Nazwa podsieci") { name -> vm.updateSubnet(index) { copy(name = name) } }
    val prefix = Ip.prefixForHosts(sub.hosts, allowSmall)

    ValueStepper(
        value = sub.hosts,
        onValueChange = { v -> vm.updateSubnet(index) { copy(hosts = v) } },
        min = 1,
        max = AppViewModel.MAX_HOSTS,
        title = if (confirmDelete) "Usunąć? Dotknij ponownie" else sub.name,
        onTitleClick = rename,
        lines = if (prefix != null) listOf(
            "${plural(sub.hosts, "host", "hosty", "hostów")} → /$prefix" to Accent,
            "blok ${fmt(Ip.blockSize(prefix))} · zapas ${fmt(Ip.usableHosts(prefix) - sub.hosts)}" to Muted,
        ) else listOf("Za dużo hostów" to Bad),
        onValueClick = {
            vm.openInput(hostsInput("Hosty: ${sub.name}", sub.hosts, allowSmall) { n -> vm.updateSubnet(index) { copy(hosts = n) } })
            nav.navigate(Routes.KEYPAD)
        },
        left = SideAction(Icons.Default.Delete, "Usuń", if (confirmDelete) Color(0xFFFF5252) else Bad) {
            if (confirmDelete) {
                vm.removeSubnet(index)
                nav.popBackStack()
            } else confirmDelete = true
        },
        right = doneAction { nav.popBackStack() },
    )
}

@Composable
fun VlsmResultScreen(vm: AppViewModel, nav: NavController) {
    val outcome by remember { derivedStateOf { vm.computeVlsm() } }
    ScreenList {
        when (val o = outcome) {
            is VlsmOutcome.Success -> resultItems(o.result, nav)
            is VlsmOutcome.DoesNotFit -> {
                header("Nie mieści się")
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Brak miejsca na „${o.failedAt}”", color = Bad, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Potrzeba ${fmt(o.neededAddresses)} adresów", fontSize = 12.sp)
                        Text("Sieć ${vm.state.vlsmBase} ma ${fmt(o.availableAddresses)}", fontSize = 12.sp, color = Muted)
                    }
                }
                o.suggestedPrefix?.let { p ->
                    item {
                        ActionChip("Użyj /$p", secondary = "powiększ sieć bazową", icon = Icons.Default.Check, primary = true) {
                            vm.setVlsmBasePrefix(p)
                        }
                    }
                }
                if (vm.state.largestFirst.not()) note("Włącz „Największe najpierw”, aby upakować ciaśniej.")
            }
            is VlsmOutcome.Invalid -> {
                header("Błąd")
                note(o.message, Bad)
            }
        }
    }
}

private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.resultItems(r: VlsmResult, nav: NavController) {
    header("Wynik VLSM")
    item {
        Card(onClick = { nav.navigate(Routes.detail(r.base, "Sieć bazowa")) }, modifier = Modifier.fillMaxWidth()) {
            Text(r.base.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            VSpace(4)
            UsageBar(
                r.allocations.map { (it.subnet.size.toDouble() / r.totalAddresses) to subnetColor(it.requestIndex) } +
                    listOf((r.totalAddresses - r.usedAddresses).toDouble() / r.totalAddresses to Color.Transparent),
            )
            VSpace(4)
            Text("Zajęte ${fmt(r.usedAddresses)}/${fmt(r.totalAddresses)} · ${pct(r.usagePercent)}", fontSize = 12.sp)
            Text("Efektywność ${pct(r.efficiencyPercent)}", fontSize = 12.sp, color = Muted)
            Text("Wolne: ${fmt(r.totalAddresses - r.usedAddresses)} adr.", fontSize = 12.sp, color = Good)
        }
    }
    items(r.allocations) { a -> AllocationCard(a) { nav.navigate(Routes.detail(a.subnet, a.request.name, a.request.hosts)) } }
    if (r.free.isNotEmpty()) {
        header("Wolne bloki")
        items(r.free) { f ->
            Chip(
                label = { Text(f.toString(), maxLines = 1) },
                secondaryLabel = { Text("${fmt(f.size)} adr. · ${hostsLabel(f.usableHosts)}", fontSize = 11.sp) },
                onClick = { nav.navigate(Routes.detail(f, "Wolny blok")) },
                colors = ChipDefaults.childChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AllocationCard(a: Allocation, onClick: () -> Unit) {
    val c = a.subnet
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorDot(subnetColor(a.requestIndex))
            Text(a.request.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            Text("/${c.prefix}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Accent)
        }
        Text(Ip.format(c.network), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Text(
            "${Ip.shortForm(c.firstHost, c.network).let { if (c.prefix >= 31) Ip.format(c.firstHost) else it }} – ${Ip.shortForm(c.lastHost, c.network)}" +
                if (c.prefix < 31) " · bc ${Ip.shortForm(c.broadcast, c.network)}" else "",
            fontSize = 12.sp, color = Muted, maxLines = 1,
        )
        Text(
            "${fmt(a.request.hosts)}/${fmt(c.usableHosts)} h · zapas ${fmt(a.spareHosts)}",
            fontSize = 11.sp, color = Good, maxLines = 1,
        )
        Text(Ip.format(c.mask), fontSize = 11.sp, color = Muted, maxLines = 1)
    }
}
