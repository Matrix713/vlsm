package pl.vlsmcalc.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import pl.vlsmcalc.wear.core.Cidr
import pl.vlsmcalc.wear.core.Ip
import java.util.Locale

private val PL: Locale = Locale.forLanguageTag("pl-PL")

fun pct(v: Double): String = String.format(PL, "%.1f%%", v)

/** Binarny zapis podzielony na dwie linie, żeby zmieścił się na okrągłym ekranie. */
fun binaryTwoLines(value: Long, prefix: Int): String {
    val s = Ip.toBinaryWithPrefix(value, prefix)
    return s.substring(0, 18) + "\n" + s.substring(18)
}

/** Pełny zestaw informacji o sieci jako elementy listy. */
fun ScalingLazyListScope.cidrDetails(c: Cidr, requested: Long? = null, showAddress: Boolean = false) {
    if (showAddress && !c.isNetworkAddress) {
        item { InfoRow("Adres hosta", Ip.format(c.address), valueColor = Accent) }
    }
    item { InfoRow("Adres sieci", c.networkString()) }
    item { InfoRow("Maska", Ip.format(c.mask)) }
    item { InfoRow("Wildcard", Ip.format(c.wildcard)) }
    item { InfoRow("Pierwszy host", Ip.format(c.firstHost)) }
    item { InfoRow("Ostatni host", Ip.format(c.lastHost)) }
    item {
        InfoRow(
            "Broadcast",
            if (c.prefix >= 31) "brak (/${c.prefix})" else Ip.format(c.broadcast),
        )
    }
    item { InfoRow("Hosty użyteczne", fmt(c.usableHosts), valueColor = Good) }
    item { InfoRow("Adresów w bloku", fmt(c.size)) }
    if (requested != null) {
        item {
            InfoRow(
                "Wymagane · zapas",
                "${fmt(requested)} · ${fmt(c.usableHosts - requested)} (${pct(requested * 100.0 / c.usableHosts)})",
            )
        }
    }
    item { InfoRow("Klasa · typ", "${c.ipClass} · ${c.addressType.label}") }
    item {
        InfoRow(
            "Następna sieć /${c.prefix}",
            if (c.broadcast < Ip.MAX) Ip.format(c.broadcast + 1) else "—",
        )
    }
    item {
        InfoRow(
            "Poprzednia sieć /${c.prefix}",
            if (c.network >= c.size) Ip.format(c.network - c.size) else "—",
        )
    }
    item { InfoRow("Maska binarnie", binaryTwoLines(c.mask, c.prefix), mono = true) }
    item { InfoRow("Sieć binarnie", binaryTwoLines(c.network, c.prefix), mono = true) }
    item { InfoRow("Sieć hex", Ip.toHex(c.network)) }
    item { InfoRow("Odwrotny DNS", reverseZone(c)) }
}

/** Strefa odwrotnego DNS dla sieci (dla prefiksów nie będących wielokrotnością 8 - strefa nadrzędna). */
fun reverseZone(c: Cidr): String {
    val octets = Ip.octets(c.network)
    val keep = (c.prefix / 8).coerceIn(0, 3).let { if (it == 0) 1 else it }
    return octets.take(keep).reversed().joinToString(".") + ".in-addr.arpa"
}

@Composable
fun ValueChip(label: String, value: String, primary: Boolean = false, onClick: () -> Unit) {
    Chip(
        label = { Text(label, fontSize = 12.sp, color = if (primary) Color.Black else Muted, maxLines = 1) },
        secondaryLabel = {
            Text(
                value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (primary) Color.Black else Color.White,
            )
        },
        onClick = onClick,
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ActionChip(
    label: String,
    secondary: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean = false,
    color: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Chip(
        label = { Text(label, maxLines = 1, color = color ?: androidx.compose.ui.graphics.Color.Unspecified) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 2, fontSize = 11.sp) } },
        icon = icon?.let { { Icon(it, null, tint = color ?: (if (primary) Color.Black else Accent)) } },
        onClick = onClick,
        enabled = enabled,
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SwitchChip(label: String, secondary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleChip(
        checked = checked,
        onCheckedChange = onChange,
        label = { Text(label, maxLines = 2, fontSize = 13.sp) },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1, fontSize = 11.sp) } },
        toggleControl = { Switch(checked = checked) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Wiersz − /24 + do szybkiej zmiany prefiksu. */
@Composable
fun PrefixRow(prefix: Int, onDelta: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton(onClick = { onDelta(-1) }, enabled = prefix > 0, colors = ButtonDefaults.secondaryButtonColors()) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("/$prefix", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
            Text(Ip.format(Ip.mask(prefix)), fontSize = 10.sp, color = Muted)
        }
        CompactButton(onClick = { onDelta(1) }, enabled = prefix < 32, colors = ButtonDefaults.secondaryButtonColors()) {
            Icon(Icons.Default.Add, "Zwiększ prefiks", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}
