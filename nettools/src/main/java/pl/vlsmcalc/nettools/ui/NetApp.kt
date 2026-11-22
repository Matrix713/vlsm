package pl.vlsmcalc.nettools.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import pl.vlsmcalc.nettools.HostTarget
import pl.vlsmcalc.nettools.NetViewModel
import pl.vlsmcalc.wear.ui.KeypadScreen
import pl.vlsmcalc.wear.ui.VlsmTheme

object NetRoutes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val CATEGORY = "category/{id}"
    const val COMMAND = "command?id={id}"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val GENERATOR = "generator"
    const val KEYPAD = "keypad"
    const val INFO = "info"
    const val PING = "ping"
    const val PORTS = "ports"
    const val DNS = "dns"
    const val LAN = "lan"
    const val HOST = "host/{ip}"
    const val PICK = "pick/{target}"
    const val WOL = "wol"
    const val HELP = "help"

    fun category(id: String) = "category/$id"
    fun command(id: String) = "command?id=${Uri.encode(id)}"
    fun host(ip: String) = "host/$ip"
    fun pick(target: HostTarget) = "pick/${target.name}"
}

@Composable
fun NetApp(vm: NetViewModel = viewModel(), startDestination: String = NetRoutes.HOME) {
    val nav = rememberSwipeDismissableNavController()
    VlsmTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = startDestination) {
            composable(NetRoutes.HOME) { NetHomeScreen(vm, nav) }
            composable(NetRoutes.CATEGORIES) { CategoriesScreen(nav) }
            composable(NetRoutes.CATEGORY, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                CategoryScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable(
                NetRoutes.COMMAND,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
            ) { CommandScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
            composable(NetRoutes.SEARCH) { SearchScreen(vm, nav) }
            composable(NetRoutes.FAVORITES) { FavoritesScreen(vm, nav) }
            composable(NetRoutes.GENERATOR) { GeneratorScreen(vm, nav) }
            composable(NetRoutes.KEYPAD) {
                val request = vm.input
                if (request != null) key(request) { KeypadScreen(request) { nav.popBackStack() } }
            }
            composable(NetRoutes.INFO) { NetInfoScreen(vm) }
            composable(NetRoutes.PING) { PingScreen(vm, nav) }
            composable(NetRoutes.PORTS) { PortsScreen(vm, nav) }
            composable(NetRoutes.DNS) { DnsScreen(vm, nav) }
            composable(NetRoutes.LAN) { LanScreen(vm, nav) }
            composable(NetRoutes.HOST, arguments = listOf(navArgument("ip") { type = NavType.StringType })) {
                HostActionsScreen(vm, nav, it.arguments?.getString("ip").orEmpty())
            }
            composable(NetRoutes.PICK, arguments = listOf(navArgument("target") { type = NavType.StringType })) {
                val target = runCatching { HostTarget.valueOf(it.arguments?.getString("target").orEmpty()) }.getOrDefault(HostTarget.PING)
                HostPickerScreen(vm, nav, target)
            }
            composable(NetRoutes.WOL) { WolScreen(vm) }
            composable(NetRoutes.HELP) { NetHelpScreen() }
        }
    }
}
