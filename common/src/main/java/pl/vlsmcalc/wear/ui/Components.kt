package pl.vlsmcalc.wear.ui

import android.app.RemoteInput
import android.content.Intent
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.input.RemoteInputIntentHelper
import kotlin.math.abs
import kotlin.math.sign

val Accent = Color(0xFF4FC3F7)
val Good = Color(0xFF81C784)
val Bad = Color(0xFFE57373)
val Muted = Color(0xFF9AA7B0)
val SurfaceDark = Color(0xFF1E2A31)

/** Kolory kolejnych podsieci w wynikach i na pasku wykorzystania. */
val SubnetPalette = listOf(
    Color(0xFF4FC3F7), Color(0xFFA5D6A7), Color(0xFFFFE082), Color(0xFFFFAB91),
    Color(0xFFCE93D8), Color(0xFF80CBC4), Color(0xFFF48FB1), Color(0xFFB0BEC5),
)

@Composable
fun VlsmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Colors(
            primary = Accent,
            primaryVariant = Color(0xFF0288D1),
            secondary = Color(0xFFA5D6A7),
            secondaryVariant = Color(0xFF66BB6A),
            error = Bad,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            surface = SurfaceDark,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
        ),
        content = content,
    )
}

/** Standardowy ekran z przewijaną listą: zegar, wskaźnik pozycji, obsługa bezela. */
@Composable
fun ScreenList(
    state: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            // Lista wyrównana do góry (nagłówek pod zegarem), z zapasem na dole na okrągłym ekranie.
            autoCentering = null,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

fun ScalingLazyListScope.header(text: String) {
    item { ListHeader { Text(text, textAlign = TextAlign.Center, color = Accent, fontWeight = FontWeight.Bold) } }
}

fun ScalingLazyListScope.note(text: String, color: Color = Muted) {
    item {
        Text(
            text, color = color, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false, valueColor: Color = Color.White) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, fontSize = 11.sp, color = Muted)
        Text(
            value,
            fontSize = if (mono) 10.sp else 15.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = valueColor,
            fontWeight = if (mono) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

/** Poziomy pasek pokazujący, jak podsieci wypełniają przestrzeń adresową. */
@Composable
fun UsageBar(parts: List<Pair<Double, Color>>, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF37474F)),
    ) {
        var x = 0f
        parts.forEach { (fraction, color) ->
            val w = (fraction * size.width).toFloat().coerceAtLeast(1.5f)
            drawRoundRect(color, Offset(x, 0f), Size(w - 1f, size.height), CornerRadius(2f, 2f))
            x += (fraction * size.width).toFloat()
        }
    }
}

/**
 * Obsługa obrotu bezela/koronki z przyspieszeniem: szybkie kręcenie zmienia wartość o 10 i 100.
 * [onStep] dostaje kierunek pomnożony przez krok.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun Modifier.rotaryStepper(currentValue: Long, onStep: (Long) -> Unit): Modifier {
    // Fokus zgodny z SwipeDismissableNavHost: aktywny ekran automatycznie dostaje zdarzenia bezela.
    val focusRequester = rememberActiveFocusRequester()
    val view = LocalView.current
    val latestValue = rememberUpdatedState(currentValue)
    val latestOnStep = rememberUpdatedState(onStep)
    val tracker = remember { RotaryTracker() }
    return this
        .onRotaryScrollEvent { event ->
            val step = tracker.onDelta(event.verticalScrollPixels, latestValue.value)
            if (step != 0L) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                latestOnStep.value(step)
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}

private class RotaryTracker {
    private var accumulated = 0f
    private var lastStepAt = 0L
    private var streak = 0

    fun onDelta(pixels: Float, value: Long): Long {
        accumulated += pixels
        if (abs(accumulated) < THRESHOLD) return 0
        val direction = sign(accumulated).toLong()
        accumulated = 0f
        val now = SystemClock.uptimeMillis()
        streak = if (now - lastStepAt < FAST_MS) streak + 1 else 0
        lastStepAt = now
        val step = when {
            streak > 14 && value >= 500 -> 100L
            streak > 5 && value >= 30 -> 10L
            else -> 1L
        }
        return direction * step
    }

    companion object {
        const val THRESHOLD = 12f
        const val FAST_MS = 110L
    }
}

/** Uruchamia systemową klawiaturę/dyktowanie Wear OS do wpisania tekstu (np. nazwy podsieci). */
@Composable
fun rememberTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data: Intent = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(TEXT_KEY)?.toString()
        if (!text.isNullOrBlank()) latest.value(text.trim().take(24))
    }
    return remember(label) {
        {
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(
                intent, listOf(RemoteInput.Builder(TEXT_KEY).setLabel(label).build()),
            )
            launcher.launch(intent)
        }
    }
}

private const val TEXT_KEY = "text"

/** Okrągły przycisk z ikoną/tekstem o zadanym rozmiarze. */
@Composable
fun RoundKey(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SurfaceDark,
    contentColor: Color = Color.White,
    fontSize: Int = 18,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) background else background.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (enabled) contentColor else contentColor.copy(alpha = 0.3f),
            fontSize = fontSize.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
