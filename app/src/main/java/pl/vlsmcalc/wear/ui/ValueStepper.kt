package pl.vlsmcalc.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text

class SideAction(val icon: ImageVector, val description: String, val color: Color, val onClick: () -> Unit)

/**
 * Pełnoekranowy stepper: obrót bezela zmienia wartość (z przyspieszeniem),
 * przyciski +/− u góry i u dołu, dotknięcie liczby otwiera klawiaturę.
 */
@Composable
fun ValueStepper(
    value: Long,
    onValueChange: (Long) -> Unit,
    min: Long,
    max: Long,
    title: String,
    onTitleClick: (() -> Unit)?,
    lines: List<Pair<String, Color>>,
    onValueClick: () -> Unit,
    left: SideAction? = null,
    right: SideAction? = null,
) {
    fun change(delta: Long) = onValueChange((value + delta).coerceIn(min, max))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .rotaryStepper(value) { change(it) },
    ) {
        val d = minOf(maxWidth, maxHeight)
        val center = d / 2

        @Composable
        fun Round(cx: Dp, cy: Dp, size: Dp, bg: Color, content: @Composable () -> Unit, onClick: () -> Unit) {
            Box(
                Modifier
                    .offset(cx - size / 2, cy - size / 2)
                    .size(size)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { content() }
        }

        Round(center, d * 0.12f, d * 0.18f, SurfaceDark, {
            Icon(Icons.Default.Add, "Więcej", tint = Accent)
        }) { change(1) }
        Round(center, d * 0.88f, d * 0.18f, SurfaceDark, {
            Text("−", fontSize = 26.sp, color = Accent, fontWeight = FontWeight.Bold)
        }) { change(-1) }

        left?.let { a ->
            Round(d * 0.12f, center, d * 0.17f, a.color.copy(alpha = 0.25f), {
                Icon(a.icon, a.description, tint = a.color)
            }, a.onClick)
        }
        right?.let { a ->
            Round(d * 0.88f, center, d * 0.17f, a.color.copy(alpha = 0.25f), {
                Icon(a.icon, a.description, tint = a.color)
            }, a.onClick)
        }

        Column(
            Modifier
                .width(d * 0.6f)
                .offset(d * 0.2f, d * 0.235f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title + if (onTitleClick != null) " ✎" else "",
                fontSize = 13.sp, color = Muted, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            val digits = value.toString().length
            Text(
                fmt(value),
                fontSize = when {
                    digits <= 4 -> 32.sp
                    digits <= 6 -> 26.sp
                    else -> 18.sp
                },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onValueClick)
                    .padding(horizontal = 6.dp),
            )
            lines.forEach { (text, color) ->
                Text(text, fontSize = 12.sp, color = color, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

fun deleteAction(onClick: () -> Unit) = SideAction(Icons.Default.Delete, "Usuń", Bad, onClick)
fun doneAction(onClick: () -> Unit) = SideAction(Icons.Default.Check, "Gotowe", Good, onClick)
