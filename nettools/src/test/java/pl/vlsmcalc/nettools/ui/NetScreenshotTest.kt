package pl.vlsmcalc.nettools.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.vlsmcalc.nettools.NetViewModel
import java.io.File

/** Renderuje ekrany NetTools na okrągłym ekranie 198 dp (test dymny + zrzuty do build/screenshots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w198dp-h198dp-round-watch-xhdpi")
class NetScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun vm() = NetViewModel(ApplicationProvider.getApplicationContext())

    private fun shot(name: String, route: String, setup: NetViewModel.() -> Unit = {}, action: () -> Unit = {}) {
        val vm = vm().apply(setup)
        compose.setContent { NetApp(vm, route) }
        compose.waitForIdle()
        action()
        compose.waitForIdle()
        val dir = System.getProperty("screenshot.dir") ?: return
        var bitmap: Bitmap? = null
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            val path = Path().apply {
                addRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), Path.Direction.CW)
                addCircle(bmp.width / 2f, bmp.height / 2f, bmp.width / 2f, Path.Direction.CCW)
            }
            Canvas(bmp).drawPath(path, Paint().apply { color = Color.argb(200, 255, 0, 0) })
            bitmap = bmp
        }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scrollAndClick(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test fun home() = shot("01_home", NetRoutes.HOME)
    @Test fun categories() = shot("02_categories", NetRoutes.CATEGORIES)
    @Test fun category() = shot("03_category_ospf", NetRoutes.CATEGORIES, action = { scrollAndClick("OSPF") })
    @Test fun command() = shot("04_command", NetRoutes.CATEGORIES, action = {
        scrollAndClick("NAT / PAT")
        scrollAndClick("ip nat inside source list <ACL> interface <if> overload")
    })
    @Test fun template() = shot("05_template", NetRoutes.CATEGORIES, action = {
        scrollAndClick("Gotowe konfiguracje")
        scrollAndClick("Dostęp SSH")
    })
    @Test fun search() = shot("06_search", NetRoutes.SEARCH, setup = { searchQuery = "sh ip int br" })
    @Test fun generator() = shot("07_generator", NetRoutes.GENERATOR, action = {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("OSPF"))
    })
    @Test fun info() = shot("08_info", NetRoutes.INFO)
    @Test fun ping() = shot("09_ping", NetRoutes.PING)
    @Test fun ports() = shot("10_ports", NetRoutes.PORTS)
    @Test fun dns() = shot("11_dns", NetRoutes.DNS)
    @Test fun lan() = shot("12_lan", NetRoutes.LAN)
    @Test fun wol() = shot("13_wol", NetRoutes.WOL, setup = { addWol("PC w domu", "0050.7966.6800") })
    @Test fun picker() = shot("14_picker", NetRoutes.pick(pl.vlsmcalc.nettools.HostTarget.PING).let { NetRoutes.PING }, action = {
        compose.onNodeWithText("Host").performClick()
    })
    @Test fun favorites() = shot("15_favorites", NetRoutes.FAVORITES, setup = {
        toggleFavorite(pl.vlsmcalc.nettools.ios.IosDatabase.commands.first().id)
    })
    @Test fun help() = shot("16_help", NetRoutes.HELP)

    @Test fun favoriteToggleFromDetail() {
        val vm = vm()
        compose.setContent { NetApp(vm, NetRoutes.CATEGORIES) }
        scrollAndClick("Podstawy i tryby")
        scrollAndClick("configure terminal")
        scrollAndClick("Dodaj do ulubionych")
        compose.waitForIdle()
        org.junit.Assert.assertTrue(vm.prefs.favorites.any { it.endsWith("|configure terminal") })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun bezelScrollsCommandList() {
        compose.setContent { NetApp(vm(), NetRoutes.CATEGORIES) }
        compose.waitForIdle()
        compose.onNodeWithText("Gotowe konfiguracje").assertDoesNotExist()
        repeat(20) {
            compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(200f) }
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Gotowe konfiguracje").assertExists()
    }
}
