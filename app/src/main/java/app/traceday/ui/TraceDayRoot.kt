package app.traceday.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.traceday.R
import app.traceday.ui.history.HistoryScreen
import app.traceday.ui.map.MapScreen
import app.traceday.ui.settings.SettingsScreen

private data class Tab(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun TraceDayRoot() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.MAP, Icons.Filled.Map, R.string.tab_map),
        Tab(Routes.HISTORY, Icons.Filled.History, R.string.tab_history),
        Tab(Routes.SETTINGS, Icons.Filled.Settings, R.string.tab_settings)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(padding)
        ) {
            composable(
                route = Routes.MAP,
                arguments = listOf(
                    navArgument("day") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                MapScreen(initialDay = it.arguments?.getString("day"))
            }
            composable(route = Routes.HISTORY) {
                HistoryScreen(
                    onOpenDay = { dayKey ->
                        navController.navigate("map?day=$dayKey") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(route = Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

object Routes {
    const val MAP = "map?day={day}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}
