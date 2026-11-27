package pl.vlsmcalc.ccna.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import pl.vlsmcalc.ccna.CcnaViewModel
import pl.vlsmcalc.wear.ui.VlsmTheme

object Routes {
    const val HOME = "home"
    const val DOMAINS = "domains"
    const val DOMAIN = "domain/{id}"
    const val TOPIC = "topic/{id}"
    const val FLASH_MENU = "flash_menu"
    const val FLASH = "flash"
    const val QUIZ_MENU = "quiz_menu"
    const val EXAM_MENU = "exam_menu"
    const val QUIZ = "quiz"
    const val RESULT = "result"
    const val REVIEW = "review"
    const val SEARCH = "search"
    const val PROGRESS = "progress"
    const val ABOUT = "about"

    fun domain(id: Int) = "domain/$id"
    fun topic(id: String) = "topic/$id"
}

@Composable
fun CcnaApp(vm: CcnaViewModel = viewModel(), startDestination: String = Routes.HOME) {
    val nav = rememberSwipeDismissableNavController()
    VlsmTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = startDestination) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.DOMAINS) { DomainsScreen(vm, nav) }
            composable(Routes.DOMAIN, arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                DomainScreen(vm, nav, it.arguments?.getInt("id") ?: 1)
            }
            composable(Routes.TOPIC, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                TopicScreen(vm, nav, it.arguments?.getString("id").orEmpty())
            }
            composable(Routes.FLASH_MENU) { FlashMenuScreen(vm, nav) }
            composable(Routes.FLASH) { FlashScreen(vm, nav) }
            composable(Routes.QUIZ_MENU) { QuizMenuScreen(vm, nav) }
            composable(Routes.EXAM_MENU) { ExamMenuScreen(vm, nav) }
            composable(Routes.QUIZ) { QuizScreen(vm, nav) }
            composable(Routes.RESULT) { ResultScreen(vm, nav) }
            composable(Routes.REVIEW) { ReviewScreen(vm) }
            composable(Routes.SEARCH) { SearchScreen(vm, nav) }
            composable(Routes.PROGRESS) { ProgressScreen(vm) }
            composable(Routes.ABOUT) { AboutScreen() }
        }
    }
}
