package pl.vlsmcalc.nettools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import pl.vlsmcalc.nettools.NetViewModel
import pl.vlsmcalc.nettools.ios.CommandGenerator
import pl.vlsmcalc.nettools.ios.IosCommand
import pl.vlsmcalc.nettools.ios.IosDatabase
import pl.vlsmcalc.nettools.ios.IosSearch
import pl.vlsmcalc.nettools.ios.Mode
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip
import pl.vlsmcalc.wear.ui.Accent
import pl.vlsmcalc.wear.ui.ActionChip
import pl.vlsmcalc.wear.ui.ColorDot
import pl.vlsmcalc.wear.ui.InputMode
import pl.vlsmcalc.wear.ui.InputRequest
import pl.vlsmcalc.wear.ui.Muted
import pl.vlsmcalc.wear.ui.ScreenList
import pl.vlsmcalc.wear.ui.SubnetPalette
import pl.vlsmcalc.wear.ui.ValueChip
import pl.vlsmcalc.wear.ui.header
import pl.vlsmcalc.wear.ui.note
import pl.vlsmcalc.wear.ui.rememberTextInput

val Yellow = Color(0xFFFFE082)
private val CodeBg = Color(0xFF0E171C)

/** Blok kodu CLI: czcionka o stałej szerokości na ciemnym tle. */
@Composable
fun CodeBox(text: String, color: Color = Accent, size: Int = 12) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Długi kod dzielony na kawałki, żeby lista z bezelem przewijała się płynnie. */
fun ScalingLazyListScope.codeItems(text: String, color: Color = Accent, size: Int = 12) {
    text.lines().chunked(6).forEach { chunk -> item { CodeBox(chunk.joinToString("\n"), color, size) } }
}

fun categoryColor(id: String): Color {
    val i = IosDatabase.categories.indexOfFirst { it.id == id }.coerceAtLeast(0)
    return SubnetPalette[i % SubnetPalette.size]
}

@Composable
fun CommandChip(cmd: IosCommand, showCategory: Boolean = false, onClick: () -> Unit) {
    Chip(
        label = {
            if (cmd.isTemplate) {
                Text(cmd.title, maxLines = 2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(cmd.syntax, maxLines = 2, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        },
        secondaryLabel = {
            val sub = if (cmd.isTemplate) cmd.description else cmd.title
            val cat = if (showCategory) IosDatabase.category(cmd.category)?.name?.let { " · $it" } ?: "" else ""
            Text(sub + cat, maxLines = 1, fontSize = 11.sp, color = Muted)
        },
        icon = if (showCategory) ({ ColorDot(categoryColor(cmd.category), 8) }) else null,
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CategoriesScreen(nav: NavController) {
    ScreenList {
        header("Komendy IOS")
        items(IosDatabase.categories) { c ->
            Chip(
                label = { Text(c.name, maxLines = 1, fontSize = 14.sp) },
                secondaryLabel = { Text("${IosDatabase.inCategory(c.id).size} · ${c.description}", maxLines = 1, fontSize = 11.sp) },
                icon = { ColorDot(categoryColor(c.id), 12) },
                onClick = { nav.navigate(NetRoutes.category(c.id)) },
                colors = if (c.id == "configs") ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun CategoryScreen(nav: NavController, id: String) {
    val category = IosDatabase.category(id) ?: return
    ScreenList {
        header(category.name)
        items(IosDatabase.inCategory(id)) { cmd -> CommandChip(cmd) { nav.navigate(NetRoutes.command(cmd.id)) } }
    }
}

@Composable
fun CommandScreen(vm: NetViewModel, nav: NavController, id: String) {
    val cmd = IosDatabase.byId(id) ?: return
    val favorite = vm.isFavorite(id)
    ScreenList {
        header(cmd.title)
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (cmd.mode == Mode.ANY) "dowolny tryb" else cmd.mode.prompt,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Yellow,
                )
                Text(cmd.mode.label, fontSize = 10.sp, color = Muted)
            }
        }
        codeItems(cmd.syntax, size = if (cmd.isTemplate) 11 else 13)
        note(cmd.description, Color.White)
        cmd.example?.let { ex ->
            item { Text("Przykład", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(start = 6.dp)) }
            codeItems(ex, color = Color.White, size = 12)
        }
        cmd.tip?.let { note("💡 $it", Yellow) }
        item {
            ActionChip(
                if (favorite) "W ulubionych" else "Dodaj do ulubionych",
                icon = Icons.Default.Star,
                color = if (favorite) Yellow else null,
            ) { vm.toggleFavorite(id) }
        }
        item {
            ActionChip(
                "Więcej: ${IosDatabase.category(cmd.category)?.name}",
                secondary = "kategoria",
            ) { nav.navigate(NetRoutes.category(cmd.category)) { launchSingleTop = true } }
        }
    }
}

@Composable
fun SearchScreen(vm: NetViewModel, nav: NavController) {
    val query = vm.searchQuery
    val results = IosSearch.search(query)
    val newSearch = rememberTextInput("Szukaj komendy") { vm.searchQuery = it }
    ScreenList {
        header(if (query.isBlank()) "Szukaj" else "„$query”")
        item { ActionChip("Szukaj", secondary = "nowe zapytanie: klawiatura lub głos", icon = Icons.Default.Search) { newSearch() } }
        if (query.isNotBlank() && results.isEmpty()) note("Brak wyników. Spróbuj skrótu, np. „sh ip ro”.")
        if (query.isBlank()) {
            note("Szukaj po poleceniu (także skróty: sh ip int br, conf t) lub po polsku: „trasa domyślna”.")
            listOf("sh ip int br", "nat", "trunk", "ospf", "ssh", "dhcp").forEach { q ->
                item { ActionChip(q) { vm.searchQuery = q } }
            }
        }
        items(results) { cmd -> CommandChip(cmd, showCategory = true) { nav.navigate(NetRoutes.command(cmd.id)) } }
    }
}

@Composable
fun FavoritesScreen(vm: NetViewModel, nav: NavController) {
    val favorites = vm.prefs.favorites.mapNotNull { IosDatabase.byId(it) }
    ScreenList {
        header("Ulubione")
        if (favorites.isEmpty()) note("Pusto. Otwórz komendę i dotknij „Dodaj do ulubionych”.")
        items(favorites) { cmd -> CommandChip(cmd, showCategory = true) { nav.navigate(NetRoutes.command(cmd.id)) } }
    }
}

@Composable
fun GeneratorScreen(vm: NetViewModel, nav: NavController) {
    val cidr = Cidr.parse(vm.prefs.generatorNet)
    ScreenList {
        header("Generator komend")
        item {
            ValueChip("Sieć lub adres interfejsu", vm.prefs.generatorNet, primary = true) {
                vm.openInput(
                    InputRequest(
                        title = "Sieć / adres",
                        mode = InputMode.IP_CIDR,
                        initial = vm.prefs.generatorNet,
                        preview = { t -> Cidr.parse(t)?.let { "wildcard ${Ip.format(it.wildcard)}" } },
                        apply = { t -> Cidr.parse(t)?.let { vm.setGeneratorNet(it.toString()); null } ?: "Błędny adres" },
                    ),
                )
                nav.navigate(NetRoutes.KEYPAD)
            }
        }
        if (cidr != null) {
            note("maska ${Ip.format(cidr.mask)} · wildcard ${Ip.format(cidr.wildcard)}")
            CommandGenerator.generate(cidr).forEach { block ->
                item { Text(block.title, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp)) }
                codeItems(block.lines.joinToString("\n"), color = Color.White, size = 11)
            }
        }
    }
}

