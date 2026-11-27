package pl.vlsmcalc.ccna.ui

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.vlsmcalc.ccna.CcnaViewModel
import pl.vlsmcalc.ccna.Scope
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w198dp-h198dp-round-watch-xhdpi")
class CcnaScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun vm() = CcnaViewModel(ApplicationProvider.getApplicationContext())

    private fun capture(name: String) {
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

    private fun show(vm: CcnaViewModel, route: String) {
        compose.setContent { CcnaApp(vm, route) }
        compose.waitForIdle()
    }

    private fun scrollAndClick(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    @Test fun home() { show(vm(), Routes.HOME); capture("01_home") }
    @Test fun domains() { show(vm(), Routes.DOMAINS); capture("02_domains") }

    @Test fun topicIpsec() {
        show(vm(), Routes.DOMAINS)
        scrollAndClick("Bezpieczeństwo i VPN")
        capture("03_domain_security")
        scrollAndClick("IPsec – AH, ESP i tryby")
        capture("04_topic_ipsec")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tryb tunelowy (tunnel mode)"))
        capture("05_topic_ipsec_scrolled")
    }

    @Test fun tableTopic() {
        show(vm(), Routes.DOMAINS)
        scrollAndClick("Łączność IP (routing)")
        scrollAndClick("Tablica routingu")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Dystans administracyjny (AD)"))
        capture("06_topic_table")
    }

    @Test fun flashcard() {
        val vm = vm().apply { startFlash(Scope.InTopic("ike")) }
        show(vm, Routes.FLASH)
        capture("07_flash_front")
        vm.flipCard()
        capture("08_flash_back")
        val before = vm.flash!!.index
        compose.onNodeWithContentDescriptionSafe("Wiem")
        assertEquals(before + 1, vm.flash!!.index)
        assertTrue(vm.progress.boxes.values.any { it == 1 })
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithContentDescriptionSafe(desc: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasContentDescription(desc))
        onNode(androidx.compose.ui.test.hasContentDescription(desc)).performClick()
        waitForIdle()
    }

    @Test fun quizAnswered() {
        val vm = vm().apply { startPractice(Scope.InTopic("ipsec"), 6) }
        show(vm, Routes.QUIZ)
        capture("09_quiz_question")
        val q = vm.quiz!!
        if (!q.current.question.isMulti) {
            vm.toggleOption(q.current.correctShown.first())
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("✓ Dobrze!"))
            capture("10_quiz_answered")
            assertTrue(vm.quiz!!.answered)
        }
    }

    @Test fun fullExamFlow() {
        val vm = vm().apply { startExam(20) }
        show(vm, Routes.QUIZ)
        capture("11_exam_question")
        var finished = false
        var guard = 0
        while (!finished && guard++ < 30) {
            val s = vm.quiz!!
            // Pierwsze 15 odpowiedzi poprawnie, resztę błędnie
            val target = if (s.index < 15) s.current.correctShown else setOf((s.current.correctShown.first() + 1) % s.current.options.size)
            target.forEach { vm.toggleOption(it) }
            finished = vm.next()
        }
        assertTrue(finished)
        assertNull(vm.quiz)
        val r = vm.result!!
        assertEquals(20, r.total)
        assertEquals(15, r.correct)
        assertEquals(75, r.percent)
        assertEquals(5, r.wrong.size)
        assertEquals(1, vm.progress.exams.size)
        // Ekran quizu sam przechodzi do wyniku po zakończeniu sesji
        compose.waitForIdle()
        compose.onNodeWithText("75%").assertExists()
        capture("12_result")
        scrollAndClick("Przejrzyj błędy (5)")
        capture("13_review")
    }

    @Test fun wrongQuestionsReview() {
        val vm = vm()
        vm.startPractice(Scope.InTopic("ike"), 3)
        repeat(3) {
            val s = vm.quiz!!
            val wrong = (s.current.correctShown.first() + 1) % s.current.options.size
            if (s.current.question.isMulti) { vm.toggleOption(wrong); vm.submit() } else vm.toggleOption(wrong)
            vm.next()
        }
        assertEquals(3, vm.wrongQuestions().size)
        show(vm, Routes.QUIZ_MENU)
        capture("14_quiz_menu")
    }

    @Test fun progress() {
        val vm = vm().apply { setRead("ipsec", true) }
        show(vm, Routes.PROGRESS)
        capture("15_progress")
    }

    @Test fun search() {
        val vm = vm().apply { searchQuery = "NAT-T" }
        show(vm, Routes.SEARCH)
        capture("16_search")
        assertTrue(vm.search("NAT-T").any { it.topic.id == "ike" })
        assertTrue(vm.search("dtp").any { it.topic.id == "trunk" })
        assertNotNull(vm.library.topic("ipsec"))
    }

    @Test fun examMenu() { show(vm(), Routes.EXAM_MENU); capture("17_exam_menu") }

    @OptIn(ExperimentalTestApi::class)
    @Test fun bezelScrollsTopic() {
        show(vm(), Routes.DOMAINS)
        scrollAndClick("Bezpieczeństwo i VPN")
        scrollAndClick("IKE – zestawianie tunelu IPsec")
        compose.onNodeWithText("Oznacz jako przeczytane").assertDoesNotExist()
        repeat(40) {
            compose.onRoot().performRotaryScrollInput { rotateToScrollVertically(250f) }
            compose.mainClock.advanceTimeBy(100)
        }
        compose.waitForIdle()
        compose.onNode(hasText("Dalej:", substring = true)).assertExists()
    }
}
