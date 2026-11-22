package pl.vlsmcalc.wear.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import pl.vlsmcalc.wear.core.Cidr

object Routes {
    const val HOME = "home"
    const val VLSM = "vlsm"
    const val SUBNET_EDIT = "subnet/{i}"
    const val VLSM_RESULT = "vlsm_result"
    const val DETAIL = "detail?a={a}&p={p}&t={t}&h={h}"
    const val KEYPAD = "keypad"
    const val IPCALC = "ipcalc"
    const val FLSM = "flsm"
    const val SUMMARY = "summary"
    const val CHECK = "check"
    const val MASKS = "masks"
    const val HOSTS = "hosts"
    const val SAVED = "saved"
    const val SAVED_ITEM = "saved/{i}"
    const val HELP = "help"

    fun subnetEdit(i: Int) = "subnet/$i"
    fun savedItem(i: Int) = "saved/$i"
    fun detail(c: Cidr, title: String, requested: Long? = null) =
        "detail?a=${c.address}&p=${c.prefix}&t=${Uri.encode(title)}&h=${requested ?: -1}"
}

@Composable
fun VlsmApp(vm: AppViewModel = viewModel(), startDestination: String = Routes.HOME) {
    val nav = rememberSwipeDismissableNavController()
    VlsmTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = startDestination) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.VLSM) { VlsmEditorScreen(vm, nav) }
            composable(Routes.SUBNET_EDIT, arguments = listOf(navArgument("i") { type = NavType.IntType })) {
                SubnetEditScreen(vm, nav, it.arguments?.getInt("i") ?: 0)
            }
            composable(Routes.VLSM_RESULT) { VlsmResultScreen(vm, nav) }
            composable(
                Routes.DETAIL,
                arguments = listOf(
                    navArgument("a") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("p") { type = NavType.IntType; defaultValue = 24 },
                    navArgument("t") { type = NavType.StringType; defaultValue = "Szczegóły" },
                    navArgument("h") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                val args = it.arguments!!
                val h = args.getLong("h")
                DetailScreen(
                    Cidr(args.getLong("a"), args.getInt("p")),
                    args.getString("t") ?: "Szczegóły",
                    if (h >= 0) h else null,
                )
            }
            composable(Routes.KEYPAD) {
                val request = vm.input
                if (request != null) key(request) { KeypadScreen(request) { nav.popBackStack() } }
            }
            composable(Routes.IPCALC) { IpCalcScreen(vm, nav) }
            composable(Routes.FLSM) { FlsmScreen(vm, nav) }
            composable(Routes.SUMMARY) { SummaryScreen(vm, nav) }
            composable(Routes.CHECK) { CheckScreen(vm, nav) }
            composable(Routes.MASKS) { MasksScreen(vm, nav) }
            composable(Routes.HOSTS) { HostsToolScreen(vm, nav) }
            composable(Routes.SAVED) { SavedScreen(vm, nav) }
            composable(Routes.SAVED_ITEM, arguments = listOf(navArgument("i") { type = NavType.IntType })) {
                SavedItemScreen(vm, nav, it.arguments?.getInt("i") ?: 0)
            }
            composable(Routes.HELP) { HelpScreen() }
        }
    }
}
