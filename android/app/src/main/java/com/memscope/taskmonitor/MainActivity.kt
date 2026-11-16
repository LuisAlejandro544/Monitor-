package com.memscope.taskmonitor

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors
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
 * Actividad principal de MemScope con navegación multi-pantalla Jetpack Compose
 * y solicitud automática de permisos necesarios al iniciar.
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

private fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun MemScopeRootNavigation(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    val shizukuStatus by container.shizukuManager.status.collectAsStateWithLifecycle()
    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var usageStatsGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var startupPromptRan by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted || hasNotificationPermission(context)
        if (shizukuStatus.isBinderAlive && !shizukuStatus.isPermissionGranted && !shizukuStatus.shouldShowRationale) {
            container.shizukuManager.requestPermission()
        } else if (!shizukuStatus.isFullyOperational || !notifGranted) {
            showPermissionsDialog = true
        }
    }

    // Reevaluar permisos al regresar de Ajustes o de la app Shizuku
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                container.shizukuManager.refreshState()
                notifGranted = hasNotificationPermission(context)
                usageStatsGranted = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Al entrar a la app, solicitar automáticamente los permisos necesarios
    LaunchedEffect(Unit) {
        if (!startupPromptRan) {
            startupPromptRan = true
            notifGranted = hasNotificationPermission(context)
            usageStatsGranted = hasUsageStatsPermission(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (shizukuStatus.isBinderAlive && !shizukuStatus.isPermissionGranted && !shizukuStatus.shouldShowRationale) {
                container.shizukuManager.requestPermission()
            } else if (!shizukuStatus.isFullyOperational || !notifGranted) {
                showPermissionsDialog = true
            }
        }
    }

    // Cuando Shizuku se autoriza, conectar UserService y conceder PACKAGE_USAGE_STATS automáticamente
    LaunchedEffect(shizukuStatus.isFullyOperational, shizukuStatus.isUserServiceConnected) {
        if (shizukuStatus.isFullyOperational) {
            container.shizukuManager.ensureUserServiceConnected()
            if (shizukuStatus.isUserServiceConnected && !usageStatsGranted) {
                container.taskMonitorRepository.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.PACKAGE_USAGE_STATS
                )
                usageStatsGranted = hasUsageStatsPermission(context)
            }
            if (notifGranted && usageStatsGranted) {
                showPermissionsDialog = false
            }
        }
    }

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
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
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

    if (showPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Permisos Necesarios") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Para escanear la RAM real del sistema, procesos y picos en segundo plano, MemScope requiere:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PermissionCheckRow(
                        icon = Icons.Rounded.AdminPanelSettings,
                        title = "Shizuku / Sui (Root o ADB)",
                        subtitle = if (shizukuStatus.isBinderAlive) {
                            "Servicio activo • Pulsa Autorizar"
                        } else {
                            "Inicia Shizuku desde su app primero"
                        },
                        granted = shizukuStatus.isFullyOperational,
                        actionLabel = if (shizukuStatus.isBinderAlive) "Autorizar" else "Abrir Shizuku",
                        onAction = {
                            if (shizukuStatus.isBinderAlive) {
                                container.shizukuManager.requestPermission()
                            } else {
                                val launchIntent = context.packageManager
                                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    showPermissionsDialog = false
                                    navController.navigate(TopLevelDestination.SHIZUKU.route) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionCheckRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Notificaciones en vivo",
                            subtitle = "Mantiene activo el monitor de picos en segundo plano",
                            granted = notifGranted,
                            actionLabel = "Permitir",
                            onAction = {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    }

                    PermissionCheckRow(
                        icon = Icons.Rounded.QueryStats,
                        title = "Acceso a uso de apps",
                        subtitle = "Respaldo de estadísticas del sistema",
                        granted = usageStatsGranted,
                        actionLabel = "Activar",
                        onAction = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPermissionsDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Más tarde")
                }
            }
        )
    }
}

@Composable
private fun PermissionCheckRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            if (granted) MemScopeColors.EmeraldHealthy.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) MemScopeColors.EmeraldHealthy else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Concedido",
                    tint = MemScopeColors.EmeraldHealthy,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
