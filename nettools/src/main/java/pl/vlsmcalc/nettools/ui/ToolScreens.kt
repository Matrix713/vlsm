package pl.vlsmcalc.nettools.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
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
import pl.vlsmcalc.nettools.HostTarget
import pl.vlsmcalc.nettools.NetViewModel
import pl.vlsmcalc.nettools.PingMode
import pl.vlsmcalc.nettools.PortPreset
import pl.vlsmcalc.nettools.ios.IosDatabase
import pl.vlsmcalc.nettools.net.PortState
import pl.vlsmcalc.nettools.net.Ports
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.ui.Accent
import pl.vlsmcalc.wear.ui.ActionChip
import pl.vlsmcalc.wear.ui.Bad
import pl.vlsmcalc.wear.ui.Good
import pl.vlsmcalc.wear.ui.InfoRow
import pl.vlsmcalc.wear.ui.InputMode
import pl.vlsmcalc.wear.ui.InputRequest
import pl.vlsmcalc.wear.ui.Muted
import pl.vlsmcalc.wear.ui.ScreenList
import pl.vlsmcalc.wear.ui.SwitchChip
import pl.vlsmcalc.wear.ui.ValueChip
import pl.vlsmcalc.wear.ui.fmt
import pl.vlsmcalc.wear.ui.header
import pl.vlsmcalc.wear.ui.note
import pl.vlsmcalc.wear.ui.rememberTextInput

/** Ekran nie gaśnie, dopóki trwa skanowanie / ping. */
@Composable
fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun ProgressBar(done: Int, total: Int, color: Color = Accent) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF37474F)),
    ) {
        val f = if (total == 0) 0f else done.toFloat() / total
        drawRect(color, Offset.Zero, Size(size.width * f, size.height))
    }
}

@Composable
fun StartStopChip(running: Boolean, startLabel: String, onStart: () -> Unit, onStop: () -> Unit) {
    ActionChip(
        if (running) "Stop" else startLabel,
        icon = if (running) Icons.Default.Refresh else Icons.Default.PlayArrow,
        primary = !running,
        color = if (running) Bad else null,
    ) { if (running) onStop() else onStart() }
}

@Composable
fun NetHomeScreen(vm: NetViewModel, nav: NavController) {
    val search = rememberTextInput("Szukaj komendy") {
        vm.searchQuery = it
        nav.navigate(NetRoutes.SEARCH)
    }
    val p = vm.prefs
    ScreenList {
        header("NetTools")
        item {
            ActionChip(
                "Komendy IOS",
                secondary = "${IosDatabase.commands.size} komend · ${IosDatabase.categories.size} kategorii",
                icon = Icons.AutoMirrored.Filled.List,
                primary = true,
            ) { nav.navigate(NetRoutes.CATEGORIES) }
        }
        item { ActionChip("Szukaj komendy", secondary = "sh ip int br, nat, trunk…", icon = Icons.Default.Search) { search() } }
        item { ActionChip("Ulubione", secondary = "${p.favorites.size}", icon = Icons.Default.Star) { nav.navigate(NetRoutes.FAVORITES) } }
        item { ActionChip("Generator komend", secondary = p.generatorNet, icon = Icons.Default.Build) { nav.navigate(NetRoutes.GENERATOR) } }
        header("Narzędzia")
        item { ActionChip("Moja sieć", secondary = "IP, brama, DNS, Wi-Fi", icon = Icons.Default.Info) { nav.navigate(NetRoutes.INFO) } }
        item { ActionChip("Ping", secondary = p.pingHost, icon = Icons.Default.PlayArrow) { nav.navigate(NetRoutes.PING) } }
        item { ActionChip("Skaner portów", secondary = p.portsHost, icon = Icons.Default.Lock) { nav.navigate(NetRoutes.PORTS) } }
        item { ActionChip("DNS lookup", secondary = p.dnsHost, icon = Icons.Default.Search) { nav.navigate(NetRoutes.DNS) } }
        item { ActionChip("Skaner LAN", secondary = "urządzenia w Wi-Fi", icon = Icons.Default.Home) { nav.navigate(NetRoutes.LAN) } }
        item { ActionChip("Wake-on-LAN", secondary = "${p.wol.size} urządz.", icon = Icons.AutoMirrored.Filled.Send) { nav.navigate(NetRoutes.WOL) } }
        item { ActionChip("Pomoc", icon = Icons.Default.Info) { nav.navigate(NetRoutes.HELP) } }
    }
}

@Composable
fun NetInfoScreen(vm: NetViewModel) {
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshInfo()
            delay(3000)
        }
    }
    val info = vm.netInfo
    ScreenList {
        header("Moja sieć")
        item {
            SwitchChip(
                "Wymuś Wi-Fi",
                vm.wifiMessage ?: if (vm.wifiForced) "aktywne" else "dla narzędzi LAN",
                vm.wifiForced || vm.wifiBusy,
            ) { vm.setForceWifi(it) }
        }
        if (info == null) {
            note("Brak połączenia sieciowego", Bad)
        } else {
            item {
                InfoRow(
                    "Połączenie",
                    info.transport + if (info.validated) " · internet ✓" else " · bez internetu",
                    valueColor = if (info.validated) Good else Color(0xFFFFE082),
                )
            }
            if (!info.isWifi) note("Zegarek korzysta z sieci telefonu. Do skanowania LAN włącz „Wymuś Wi-Fi”.")
            info.ssid?.let { item { InfoRow("SSID", it) } }
            if (info.rssi != null || info.linkSpeedMbps != null) {
                item {
                    InfoRow(
                        "Sygnał",
                        listOfNotNull(
                            info.rssi?.let { "$it dBm" },
                            info.frequencyMhz?.let { if (it > 4900) "5 GHz" else "2,4 GHz" },
                            info.linkSpeedMbps?.let { "$it Mb/s" },
                        ).joinToString(" · "),
                    )
                }
            }
            info.ipv4.forEach { c ->
                item { InfoRow("Adres IPv4", c.toString(), valueColor = Accent) }
                item { InfoRow("Sieć · maska", "${c.networkString()} · ${Ip.format(c.mask)}") }
            }
            info.gateway?.let { item { InfoRow("Brama", it) } }
            if (info.dns.isNotEmpty()) item { InfoRow("DNS", info.dns.joinToString("\n")) }
            info.ipv6.take(3).forEach { item { InfoRow("IPv6", it, mono = true) } }
            info.interfaceName?.let { item { InfoRow("Interfejs", it) } }
            if (info.downKbps > 0) item { InfoRow("Pasmo (szac.)", "↓ ${fmt(info.downKbps / 1000L)} · ↑ ${fmt(info.upKbps / 1000L)} Mb/s") }
            item { InfoRow("Taryfowane", if (info.metered) "tak" else "nie") }
        }
    }
}

private fun pingCountLabel(count: Int) = if (count == 0) "ciągły (∞)" else "$count pakietów"

@Composable
fun PingScreen(vm: NetViewModel, nav: NavController) {
    val p = vm.prefs
    val ui = vm.ping
    KeepScreenOn(ui.running)
    ScreenList {
        header("Ping")
        item { ValueChip("Host", p.pingHost) { nav.navigate(NetRoutes.pick(HostTarget.PING)) } }
        item { StartStopChip(ui.running, "Start", vm::startPing, vm::stopPing) }
        ui.stats?.takeIf { it.sent > 0 }?.let { s ->
            item {
                Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${s.received}/${s.sent} · strata ${s.lossPercent}%",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (s.lossPercent == 0) Good else if (s.received == 0) Bad else Color(0xFFFFE082),
                    )
                    if (s.avg != null) {
                        Text(
                            "min/śr/max ${"%.1f".format(s.min)}/${"%.1f".format(s.avg)}/${"%.1f".format(s.max)} ms",
                            fontSize = 11.sp, color = Muted,
                        )
                    }
                    VSpace4()
                    RttChart(ui.entries.takeLast(30).map { it.millis })
                }
            }
        }
        ui.error?.let { note(it, Bad) }
        items(ui.entries.takeLast(20).reversed()) { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${e.seq}", fontSize = 11.sp, color = Muted, fontFamily = FontFamily.Monospace)
                Text(
                    e.millis?.let { "%.1f ms".format(it) } ?: "—",
                    fontSize = 12.sp, color = if (e.ok) Good else Bad, fontFamily = FontFamily.Monospace,
                )
                Text(e.text, fontSize = 10.sp, color = Muted, maxLines = 1)
            }
        }
        header("Ustawienia")
        item {
            ValueChip(
                if (p.pingMode == PingMode.ICMP) "Tryb · systemowy ping" else "Tryb · czas połączenia TCP",
                p.pingMode.label,
            ) { vm.cyclePingMode() }
        }
        if (p.pingMode == PingMode.TCP) {
            item {
                ValueChip("Port TCP", "${p.tcpPort} ${Ports.name(p.tcpPort)}") {
                    vm.openInput(portInput(p.tcpPort) { vm.setTcpPort(it) })
                    nav.navigate(NetRoutes.KEYPAD)
                }
            }
        }
        item { ValueChip("Liczba pakietów", pingCountLabel(p.pingCount)) { vm.cyclePingCount() } }
    }
}

@Composable
private fun VSpace4() = androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))

@Composable
fun RttChart(values: List<Double?>) {
    val max = (values.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width / 30f
        values.forEachIndexed { i, v ->
            val h = if (v == null) size.height else (v / max * size.height).toFloat().coerceAtLeast(2f)
            drawRect(
                if (v == null) Bad else Good,
                Offset(i * w, size.height - h),
                Size(w * 0.7f, h),
            )
        }
    }
}

fun portInput(initial: Int, onValid: (Int) -> Unit) = InputRequest(
    title = "Port TCP",
    mode = InputMode.NUMBER,
    initial = initial.toString(),
    preview = { t -> t.toIntOrNull()?.let { Ports.name(it).ifEmpty { null } } },
    apply = { t ->
        val n = t.toIntOrNull()
        if (n == null || n !in 1..65535) "Port 1–65535" else { onValid(n); null }
    },
)

@Composable
fun PortsScreen(vm: NetViewModel, nav: NavController) {
    val p = vm.prefs
    val ui = vm.ports
    KeepScreenOn(ui.running)
    ScreenList {
        header("Skaner portów")
        item { ValueChip("Host", p.portsHost) { nav.navigate(NetRoutes.pick(HostTarget.PORTS)) } }
        item {
            ValueChip("Zakres portów", p.portPreset.label) { vm.cyclePortPreset() }
        }
        if (p.portPreset == PortPreset.SINGLE) {
            item {
                ValueChip("Port", "${p.singlePort} ${Ports.name(p.singlePort)}") {
                    vm.openInput(portInput(p.singlePort) { vm.setSinglePort(it) })
                    nav.navigate(NetRoutes.KEYPAD)
                }
            }
        }
        item { StartStopChip(ui.running, "Skanuj", vm::startPortScan, vm::stopPortScan) }
        if (ui.total > 0 && ui.error == null) {
            item { ProgressBar(ui.done, ui.total) }
            note("${ui.target}\n${ui.done}/${ui.total} · otwarte: ${ui.open.size}")
        }
        ui.error?.let { note(it, Bad) }
        items(ui.open) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B3A2A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${r.port}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Good, fontFamily = FontFamily.Monospace)
                Text(Ports.name(r.port).ifEmpty { "?" }, fontSize = 13.sp, color = Color.White)
                Text("${r.millis} ms", fontSize = 11.sp, color = Muted)
            }
        }
        if (!ui.running && ui.results.isNotEmpty()) {
            val closed = ui.results.count { it.state == PortState.CLOSED }
            val filtered = ui.results.count { it.state == PortState.FILTERED }
            note("Zamknięte (RST): $closed · filtrowane: $filtered")
        }
    }
}

@Composable
fun DnsScreen(vm: NetViewModel, nav: NavController) {
    val ui = vm.dns
    LaunchedEffect(Unit) { vm.refreshInfo() }
    ScreenList {
        header("DNS lookup")
        item { ValueChip("Nazwa lub IP (PTR)", vm.prefs.dnsHost) { nav.navigate(NetRoutes.pick(HostTarget.DNS)) } }
        item { ActionChip("Sprawdź", icon = Icons.Default.Search, primary = true, enabled = !ui.running) { vm.runDns() } }
        ui.error?.let { note(it, Bad) }
        if (ui.v4.isNotEmpty()) item { InfoRow("A (IPv4)", ui.v4.joinToString("\n"), valueColor = Accent) }
        if (ui.v6.isNotEmpty()) item { InfoRow("AAAA (IPv6)", ui.v6.joinToString("\n"), mono = true) }
        ui.ptr?.let { item { InfoRow("PTR", it, valueColor = Accent) } }
        ui.millis?.let { note("Czas: $it ms") }
        vm.netInfo?.dns?.takeIf { it.isNotEmpty() }?.let { servers ->
            item { InfoRow("Serwery DNS", servers.joinToString("\n")) }
        }
    }
}

@Composable
fun LanScreen(vm: NetViewModel, nav: NavController) {
    val ui = vm.lan
    KeepScreenOn(ui.running)
    ScreenList {
        header("Skaner LAN")
        item { StartStopChip(ui.running, "Skanuj sieć Wi-Fi", vm::startLanScan, vm::stopLanScan) }
        ui.error?.let { note(it, Bad) }
        ui.subnet?.let { s ->
            item { ProgressBar(ui.done, ui.total) }
            note("$s · ${ui.done}/${ui.total}\nZnaleziono: ${ui.hosts.size}")
        }
        items(ui.hosts) { h ->
            val ip = Ip.format(h.ip)
            val tag = when {
                h.isSelf -> " (zegarek)"
                h.isGateway -> " (brama)"
                else -> ""
            }
            Chip(
                label = { Text(ip + tag, maxLines = 1, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                secondaryLabel = {
                    val ports = h.open.joinToString(", ") { Ports.name(it).ifEmpty { it.toString() } }
                    Text(listOfNotNull(h.name, ports.ifEmpty { null }).joinToString(" · ").ifEmpty { "odpowiada" }, maxLines = 1, fontSize = 11.sp)
                },
                onClick = { nav.navigate(NetRoutes.host(ip)) },
                colors = if (h.isGateway) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.subnet == null && !ui.running && ui.error == null) {
            note("Wykrywa urządzenia w Twojej sieci Wi-Fi (do /24). Zegarek na chwilę włączy Wi-Fi.")
        }
    }
}

@Composable
fun HostActionsScreen(vm: NetViewModel, nav: NavController, ip: String) {
    ScreenList {
        header(ip)
        item {
            ActionChip("Ping", icon = Icons.Default.PlayArrow, primary = true) {
                vm.setHost(HostTarget.PING, ip)
                vm.startPing()
                nav.navigate(NetRoutes.PING)
            }
        }
        item {
            ActionChip("Skanuj porty", icon = Icons.Default.Lock) {
                vm.setHost(HostTarget.PORTS, ip)
                vm.startPortScan()
                nav.navigate(NetRoutes.PORTS)
            }
        }
        item {
            ActionChip("Nazwa (PTR)", icon = Icons.Default.Search) {
                vm.setHost(HostTarget.DNS, ip)
                vm.runDns()
                nav.navigate(NetRoutes.DNS)
            }
        }
    }
}

@Composable
fun HostPickerScreen(vm: NetViewModel, nav: NavController, target: HostTarget) {
    LaunchedEffect(Unit) { vm.refreshInfo() }
    fun pick(host: String) {
        vm.setHost(target, host)
        nav.popBackStack()
    }
    val typed = rememberTextInput("Host lub adres IP") { pick(it) }
    val info = vm.netInfo
    ScreenList {
        header("Wybierz host")
        item { ActionChip("Klawiatura / głos", secondary = "nazwa lub IP", icon = Icons.Default.Edit) { typed() } }
        item {
            ActionChip("Szybko: adres IP", secondary = "klawiatura numeryczna", icon = Icons.Default.Edit) {
                vm.openInput(
                    InputRequest(
                        title = "Adres IP",
                        mode = InputMode.IP,
                        initial = "",
                        apply = { t -> if (Ip.parse(t) == null) "Błędny adres" else { vm.setHost(target, t); null } },
                    ),
                )
                nav.popBackStack()
                nav.navigate(NetRoutes.KEYPAD)
            }
        }
        info?.gateway?.let { gw -> item { ActionChip("Brama: $gw") { pick(gw) } } }
        info?.dns?.firstOrNull()?.let { d -> item { ActionChip("DNS: $d") { pick(d) } } }
        val quick = listOf("8.8.8.8", "1.1.1.1", "cisco.com", "google.com")
        val recent = vm.prefs.recentHosts.filter { it !in quick }
        if (recent.isNotEmpty()) header("Ostatnie")
        items(recent) { h -> ActionChip(h) { pick(h) } }
        header("Szybki wybór")
        items(quick) { h -> ActionChip(h) { pick(h) } }
    }
}

@Composable
fun WolScreen(vm: NetViewModel) {
    var pendingName by remember { mutableStateOf<String?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val askMac = rememberTextInput("Adres MAC") { mac ->
        error = vm.addWol(pendingName ?: "", mac)
        pendingName = null
    }
    val askName = rememberTextInput("Nazwa urządzenia") { name ->
        pendingName = name
        askMac()
    }
    ScreenList {
        header("Wake-on-LAN")
        vm.wolStatus?.let { note(it, if (it.contains("✓")) Good else Muted) }
        error?.let { note(it, Bad) }
        if (vm.prefs.wol.isEmpty()) note("Dodaj komputer (nazwa + MAC). Dotknięcie wyśle pakiet budzący przez Wi-Fi.")
        itemsIndexed(vm.prefs.wol) { i, d ->
            Chip(
                label = { Text(d.name, maxLines = 1) },
                secondaryLabel = { Text(d.mac, maxLines = 1, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                icon = { androidx.wear.compose.material.Icon(if (deleteMode) Icons.Default.Delete else Icons.AutoMirrored.Filled.Send, null, tint = if (deleteMode) Bad else Accent) },
                onClick = { if (deleteMode) vm.removeWol(i) else vm.sendWol(d) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ActionChip("Dodaj urządzenie", secondary = "nazwa, potem MAC", icon = Icons.Default.Add) { askName() } }
        if (vm.prefs.wol.isNotEmpty()) {
            item {
                ActionChip(if (deleteMode) "Gotowe" else "Usuń urządzenia", icon = Icons.Default.Delete, color = if (deleteMode) null else Bad) {
                    deleteMode = !deleteMode
                }
            }
        }
        note("MAC: AA:BB:CC:DD:EE:FF, AA-BB-…, aabb.ccdd.eeff")
    }
}

@Composable
fun NetHelpScreen() {
    ScreenList {
        header("Pomoc")
        note("Komendy IOS działają offline. Szukaj skrótami jak w CLI (sh ip int br) albo po polsku.", Color.White)
        note("Generator zamienia sieć na gotowe linie: ip address, OSPF/EIGRP z wildcard, ACL, DHCP, NAT, BGP.")
        header("Sieć zegarka")
        note("Połączony z telefonem zegarek używa Bluetooth. Ping do Internetu działa, ale do LAN włącz „Wymuś Wi-Fi” w „Moja sieć”.")
        note("Gdy systemowy ping (ICMP) nie działa, przełącz tryb na TCP – mierzy czas połączenia z portem.")
        note("Skaner LAN wykrywa hosty po odpowiedziach TCP (także odrzuconych) i ICMP; urządzenia z pełnym firewallem mogą się nie pokazać.")
        header("Sterowanie")
        note("Bezel przewija listy. Przesuń w prawo, aby wrócić. Ekran nie gaśnie podczas skanowania.")
    }
}
