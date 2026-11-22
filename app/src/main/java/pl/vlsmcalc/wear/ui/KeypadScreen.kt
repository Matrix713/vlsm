package pl.vlsmcalc.wear.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

private data class Key(val label: String, val char: Char?, val kind: Kind) {
    enum class Kind { DIGIT, SYMBOL, BACK, OK }
}

/**
 * Pełnoekranowa klawiatura numeryczna zaprojektowana pod okrągły ekran.
 * Układ: 1–5 / 6–0 / [. /] ⌫ OK. Długie przytrzymanie ⌫ czyści całość.
 * Pierwsze naciśnięcie cyfry zastępuje wartość początkową (jak zaznaczenie całości).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(request: InputRequest, onDone: () -> Unit) {
    var text by remember { mutableStateOf(request.initial) }
    var fresh by remember { mutableStateOf(request.initial.isNotEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    fun press(key: Key) {
        tap()
        error = null
        when (key.kind) {
            Key.Kind.DIGIT, Key.Kind.SYMBOL -> {
                val base = if (fresh && key.kind == Key.Kind.DIGIT) "" else text
                text = InputEditor.append(base, key.char!!, request.mode)
                fresh = false
            }
            Key.Kind.BACK -> {
                text = InputEditor.backspace(text)
                fresh = false
            }
            Key.Kind.OK -> {
                if (text.isEmpty() && !request.allowEmpty) {
                    error = "Wpisz wartość"
                    return
                }
                val result = request.apply(text)
                if (result == null) onDone() else {
                    error = result
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }

    val digitsTop = "12345".map { Key(it.toString(), it, Key.Kind.DIGIT) }
    val digitsBottom = "67890".map { Key(it.toString(), it, Key.Kind.DIGIT) }
    val actions = buildList {
        if (request.mode != InputMode.NUMBER) add(Key(".", '.', Key.Kind.SYMBOL))
        if (request.mode == InputMode.IP_CIDR) add(Key("/", '/', Key.Kind.SYMBOL))
        add(Key("⌫", null, Key.Kind.BACK))
        add(Key("OK", null, Key.Kind.OK))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val d = minOf(maxWidth, maxHeight)
        val keySize = d * 0.168f
        val pitch = d * 0.182f

        @Composable
        fun KeyButton(key: Key, cx: Dp, cy: Dp, size: Dp = keySize) {
            val bg = when (key.kind) {
                Key.Kind.OK -> Accent
                Key.Kind.BACK -> Color(0xFF4A3434)
                Key.Kind.SYMBOL -> Color(0xFF2C3E48)
                Key.Kind.DIGIT -> SurfaceDark
            }
            val fg = if (key.kind == Key.Kind.OK) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .offset(cx - size / 2, cy - size / 2)
                    .size(size)
                    .clip(CircleShape)
                    .background(bg)
                    .combinedClickable(
                        onClick = { press(key) },
                        onLongClick = if (key.kind == Key.Kind.BACK) {
                            { tap(); text = ""; fresh = false; error = null }
                        } else null,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    key.label, color = fg, fontWeight = FontWeight.Bold,
                    fontSize = if (key.kind == Key.Kind.OK) 14.sp else 19.sp,
                )
            }
        }

        // Wyświetlacz
        val shown = if (text.isEmpty()) request.title else text
        val previewText = error ?: if (text.isNotEmpty()) request.preview(text) else null
        Column(
            modifier = Modifier
                .width(d * 0.84f)
                .offset(d * 0.08f, d * 0.10f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (text.isNotEmpty()) {
                Text(request.title, fontSize = 10.sp, color = Muted, maxLines = 1)
            }
            Text(
                shown,
                fontSize = when {
                    text.isEmpty() -> 14.sp
                    text.length > 18 -> 12.sp
                    text.length > 15 -> 14.sp
                    else -> 17.sp
                },
                color = when {
                    text.isEmpty() -> Muted
                    fresh -> Accent
                    else -> Color.White
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
            )
            if (previewText != null) {
                Text(
                    previewText, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
                    color = if (error != null) Bad else Good,
                )
            }
        }

        val center = d / 2
        val rowY = listOf(d * 0.43f, d * 0.615f, d * 0.80f)
        digitsTop.forEachIndexed { i, k -> KeyButton(k, center + pitch * (i - 2), rowY[0]) }
        digitsBottom.forEachIndexed { i, k -> KeyButton(k, center + pitch * (i - 2), rowY[1]) }
        // Dolny rząd jest węższy (krzywizna ekranu), więc klawisze są rozstawione ciaśniej i centralnie.
        val n = actions.size
        val actionPitch = if (n <= 2) d * 0.24f else d * 0.19f
        val actionSize = if (n <= 2) keySize * 1.15f else keySize
        actions.forEachIndexed { i, k ->
            val offset = (i - (n - 1) / 2f)
            KeyButton(k, center + actionPitch * offset, rowY[2] - if (n == 4 && (i == 0 || i == 3)) d * 0.03f else d * 0f, actionSize)
        }
    }
}
