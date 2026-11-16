package com.memscope.taskmonitor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memscope.taskmonitor.core.ui.theme.MemScopeTheme
import com.memscope.taskmonitor.feature.freezer.FreezerScreen
import com.memscope.taskmonitor.feature.freezer.FreezerViewModel
import com.memscope.taskmonitor.feature.monitor.AppDetailScreen
import com.memscope.taskmonitor.feature.monitor.MonitorScreen
import com.memscope.taskmonitor.feature.monitor.MonitorViewModel
import com.memscope.taskmonitor.feature.peaks.PeaksScreen
import com.memscope.taskmonitor.feature.peaks.PeaksViewModel
import com.memscope.taskmonitor.feature.shizuku_tools.ShizukuToolsScreen
import com.memscope.taskmonitor.feature.shizuku_tools.ShizukuToolsViewModel

/**
 * Rutas de navegación modular entre las 4 pantallas principales y la pantalla de detalle.
 */
private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    MONITOR("monitor", "Monitor", Icons.Rounded.MonitorHeart),
    PEAKS("peaks", "Picos RAM", Icons.Rounded.ShowChart),
    FREEZER("freezer", "Congelador", Icons.Rounded.AcUnit),
    SHIZUKU("shizuku", "Shizuku", Icons.Rounded.AdminPanelSettings)
}

private const val ROUTE_APP_DETAIL = "app_detail/{packageName}"

/**
 * Actividad principal de MemScope con navegación multi-pantalla Jetpack Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TaskMonitorApp).container

        setContent {
            MemScopeTheme {
                MemScopeRootNavigation(container = container)
            }
        }
    }
}

@Composable
private fun MemScopeRootNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    val monitorViewModel: MonitorViewModel = viewModel(
        factory = MonitorViewModel.Factory(
            repository = container.taskMonitorRepository,
            shizukuManager = container.shizukuManager,
            preferencesRepository = container.preferencesRepository,
            backgroundController = container.backgroundMonitorController
        )
    )
    val peaksViewModel: PeaksViewModel = viewModel(
        factory = PeaksViewModel.Factory(repository = container.taskMonitorRepository)
    )
    val freezerViewModel: FreezerViewModel = viewModel(
        factory = FreezerViewModel.Factory(
            repository = container.taskMonitorRepository,
            shizukuManager = container.shizukuManager
        )
    )
    val shizukuToolsViewModel: ShizukuToolsViewModel = viewModel(
        factory = ShizukuToolsViewModel.Factory(
            shizukuManager = container.shizukuManager,
            repository = container.taskMonitorRepository,
            preferencesRepository = container.preferencesRepository,
            backgroundController = container.backgroundMonitorController
        )
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.MONITOR.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.MONITOR.route) {
                MonitorScreen(
                    viewModel = monitorViewModel,
                    onOpenAppDetail = { pkg ->
                        navController.navigate("app_detail/${Uri.encode(pkg)}")
                    },
                    onNavigateToShizuku = {
                        navController.navigate(TopLevelDestination.SHIZUKU.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(TopLevelDestination.PEAKS.route) {
                PeaksScreen(
                    viewModel = peaksViewModel,
                    onOpenAppDetail = { pkg ->
                        navController.navigate("app_detail/${Uri.encode(pkg)}")
                    }
                )
            }

            composable(TopLevelDestination.FREEZER.route) {
                FreezerScreen(
                    viewModel = freezerViewModel,
                    onNavigateToShizuku = {
                        navController.navigate(TopLevelDestination.SHIZUKU.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(TopLevelDestination.SHIZUKU.route) {
                ShizukuToolsScreen(viewModel = shizukuToolsViewModel)
            }

            composable(
                route = ROUTE_APP_DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { entry ->
                val encoded = entry.arguments?.getString("packageName").orEmpty()
                val pkg = Uri.decode(encoded)
                AppDetailScreen(
                    packageName = pkg,
                    viewModel = monitorViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
