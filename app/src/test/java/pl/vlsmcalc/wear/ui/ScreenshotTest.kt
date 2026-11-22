package pl.vlsmcalc.wear.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renderuje każdy ekran na okrągłym ekranie zegarka (198 dp jak Galaxy Watch 4 Classic 42 mm).
 * Działa też jako test dymny: każdy ekran musi się wyrenderować bez wyjątku.
 * Zrzuty trafiają do katalogu app/build/screenshots (obszar poza okręgiem zaznaczony na czerwono).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w198dp-h198dp-round-watch-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun vm() = AppViewModel(ApplicationProvider.getApplicationContext()).apply { loadVlsmExample() }

    private fun shot(
        name: String,
        route: String,
        action: () -> Unit = {},
        setup: AppViewModel.() -> Unit = {},
    ) {
        val vm = vm().apply(setup)
        compose.setContent { VlsmApp(vm, route) }
        compose.waitForIdle()
        action()
        compose.waitForIdle()
        val dir = System.getProperty("screenshot.dir") ?: return
        var bitmap: Bitmap? = null
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            // Maska okrągłego ekranu
            val c = Canvas(bmp)
            val path = Path().apply {
                addRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), Path.Direction.CW)
                addCircle(bmp.width / 2f, bmp.height / 2f, bmp.width / 2f, Path.Direction.CCW)
            }
            c.drawPath(path, Paint().apply { color = Color.argb(200, 255, 0, 0) })
            bitmap = bmp
        }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("01_home", Routes.HOME)
    @Test fun vlsm() = shot("02_vlsm", Routes.VLSM)
    @Test fun subnetEdit() = shot("03_subnet_edit", Routes.subnetEdit(0))
    @Test fun result() = shot("04_result", Routes.VLSM_RESULT)
    @Test fun resultDoesNotFit() = shot("05_result_nofit", Routes.VLSM_RESULT) { setVlsmBase("192.168.1.0/26") }
    @Test fun keypadIp() = shot("06_keypad_ip", Routes.KEYPAD) {
        openInput(networkInput("Sieć bazowa", "192.168.1.0/24") {})
    }
    @Test fun keypadNumber() = shot("07_keypad_num", Routes.KEYPAD) {
        openInput(hostsInput("Hosty: Biuro", 60, false) {})
    }
    @Test fun ipCalc() = shot("08_ipcalc", Routes.IPCALC)
    @Test fun flsm() = shot("09_flsm", Routes.FLSM)
    @Test fun summary() = shot("10_summary", Routes.SUMMARY) { loadSummaryExample() }
    @Test fun check() = shot("11_check", Routes.CHECK)
    @Test fun masks() = shot("12_masks", Routes.MASKS)
    @Test fun hosts() = shot("13_hosts", Routes.HOSTS)
    @Test fun saved() = shot("14_saved", Routes.SAVED) { saveCurrentProject() }
    @Test fun help() = shot("15_help", Routes.HELP)
    @Test fun detail() = shot("16_detail", Routes.VLSM_RESULT, action = {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Magazyn"))
        compose.onNodeWithText("Magazyn").performClick()
    })

    @Test fun keypadTyping() = shot("17_keypad_typing", Routes.KEYPAD, action = {
        // Szybkie wpisywanie: "10" "0" "0" "0" "22" -> 10.0.0.0/22 dzięki auto-kropkom
        listOf("1", "0", ".", "0", ".", "0", ".", "0", "/", "2", "2").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).performClick()
        }
    }) {
        openInput(networkInput("Sieć bazowa", "192.168.1.0/24") {})
    }

    @Test fun subnetEditAfterAdd() = shot("18_vlsm_add", Routes.VLSM, action = {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Dodaj podsieć"))
        compose.onNodeWithText("Dodaj podsieć").performClick()
    })

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun bezelChangesHosts() {
        val vm = vm()
        compose.setContent { VlsmApp(vm, Routes.HOSTS) }
        compose.waitForIdle()
        val before = vm.state.hostsTool
        // Trzy "kliknięcia" bezela w prawo
        repeat(3) {
            compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(40f) }
            compose.mainClock.advanceTimeBy(300)
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(before + 3, vm.state.hostsTool)
        compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(-40f) }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(before + 2, vm.state.hostsTool)
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun bezelScrollsLists() {
        compose.setContent { VlsmApp(vm(), Routes.HOME) }
        compose.waitForIdle()
        compose.onNodeWithText("Pomoc").assertDoesNotExist()
        repeat(10) {
            compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(150f) }
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Pomoc").assertExists()
    }
}
