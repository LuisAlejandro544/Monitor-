package com.memscope.taskmonitor.feature.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memscope.taskmonitor.core.model.AppMemoryInfo
import com.memscope.taskmonitor.core.model.AppVisibilityFilter
import com.memscope.taskmonitor.core.model.ProcessSortOrder
import com.memscope.taskmonitor.core.ui.components.AppPackageIcon
import com.memscope.taskmonitor.core.ui.components.FrozenStateChip
import com.memscope.taskmonitor.core.ui.components.MemoryFormatter
import com.memscope.taskmonitor.core.ui.components.MemorySparklineChart
import com.memscope.taskmonitor.core.ui.components.ProtectedPackageChip
import com.memscope.taskmonitor.core.ui.components.ShizukuActionBanner
import com.memscope.taskmonitor.core.ui.components.SystemMemoryHeaderCard
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors

/**
 * Pantalla principal del Administrador de Tareas en vivo.
 * Muestra la tarjeta de memoria del sistema, interruptor de monitoreo real en segundo plano
 * (Foreground Service), barra de búsqueda/filtros y listado con RAM, CPU %, hilos y picos.
 */
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    onOpenAppDetail: (String) -> Unit,
    onNavigateToShizuku: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingFreezeApp by remember { mutableStateOf<AppMemoryInfo?>(null) }

    val context = LocalContext.current
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundMonitoring(true)
        }
    }

    LaunchedEffect(state.actionFeedback) {
        val msg = state.actionFeedback
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Monitor de Tareas",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "RAM, CPU %, hilos y picos en tiempo real",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                if (state.backgroundMonitoringEnabled) {
                                    viewModel.setBackgroundMonitoring(false)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setBackgroundMonitoring(true)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (state.backgroundMonitoringEnabled) {
                                    Icons.Rounded.NotificationsActive
                                } else {
                                    Icons.Rounded.NotificationsOff
                                },
                                contentDescription = "Monitoreo en segundo plano",
                                tint = if (state.backgroundMonitoringEnabled) {
                                    MemScopeColors.EmeraldHealthy
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = viewModel::togglePause) {
                            Icon(
                                imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (state.isPaused) "Reanudar muestreo" else "Pausar muestreo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = viewModel::refreshNow) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Actualizar ahora",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                ShizukuActionBanner(
                    status = state.shizukuStatus,
                    onRequestPermission = viewModel::requestShizukuPermission,
                    onOpenShizukuTab = onNavigateToShizuku
                )
            }

            item {
                SystemMemoryHeaderCard(
                    overview = state.systemOverview,
                    shizukuStatus = state.shizukuStatus
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Buscar app o paquete...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Limpiar búsqueda")
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppVisibilityFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.visibilityFilter == filter,
                            onClick = { viewModel.onVisibilityFilterChange(filter) },
                            label = { Text(filter.labelEs) }
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(state.sortOrder.labelEs)
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        ProcessSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.labelEs) },
                                onClick = {
                                    viewModel.onSortOrderChange(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (state.apps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (state.shizukuStatus.isFullyOperational) {
                                    "No hay procesos que coincidan con el filtro actual."
                                } else {
                                    "Esperando autorización de Shizuku para inspeccionar procesos del sistema."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.apps,
                    key = { it.packageName }
                ) { app ->
                    ProcessTaskRowCard(
                        app = app,
                        totalSystemRamKb = state.systemOverview.totalRamKb,
                        onClick = { onOpenAppDetail(app.packageName) },
                        onForceStop = { viewModel.forceStopApp(app.packageName, app.appName) },
                        onFreezeClick = { pendingFreezeApp = app }
                    )
                }
            }
        }
    }

    val appToFreeze = pendingFreezeApp
    if (appToFreeze != null) {
        AlertDialog(
            onDismissRequest = { pendingFreezeApp = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.AcUnit,
                    contentDescription = null,
                    tint = MemScopeColors.IndigoFrozen
                )
            },
            title = {
                Text(
                    text = if (appToFreeze.isFrozen) {
                        "Descongelar ${appToFreeze.appName}"
                    } else {
                        "Congelar ${appToFreeze.appName}"
                    }
                )
            },
            text = {
                Text(
                    text = if (appToFreeze.isFrozen) {
                        "La aplicación volverá a estar habilitada y podrá ejecutarse normalmente."
                    } else {
                        "Se detendrá el proceso y se deshabilitará el paquete (${appToFreeze.packageName}) para que no vuelva a consumir RAM en segundo plano hasta que la descongeles."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.freezeOrUnfreezeApp(
                            packageName = appToFreeze.packageName,
                            appName = appToFreeze.appName,
                            freeze = !appToFreeze.isFrozen
                        )
                        pendingFreezeApp = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MemScopeColors.IndigoFrozen
                    )
                ) {
                    Text(if (appToFreeze.isFrozen) "Descongelar" else "Congelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFreezeApp = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ProcessTaskRowCard(
    app: AppMemoryInfo,
    totalSystemRamKb: Long,
    onClick: () -> Unit,
    onForceStop: () -> Unit,
    onFreezeClick: () -> Unit
) {
    val ramColor = MemoryFormatter.ramSeverityColor(app.currentPssKb)
    val shareFraction = if (totalSystemRamKb > 0) {
        (app.currentPssKb.toFloat() / totalSystemRamKb.toFloat()).coerceIn(0.02f, 1f)
    } else {
        0.05f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppPackageIcon(packageName = app.packageName, size = 42.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isFrozen) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FrozenStateChip()
                        } else if (app.isCriticalProtected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ProtectedPackageChip()
                        }
                    }

                    Text(
                        text = "${app.importance.labelEs} • CPU: ${MemoryFormatter.formatCpu(app.cpuUsagePercent)} • Hilos: ${app.totalThreads}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MemoryFormatter.formatKb(app.currentPssKb),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ramColor
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = MemScopeColors.CrimsonPeak,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Pico: ${MemoryFormatter.formatKb(app.effectivePeakKb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { shareFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ramColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RSS: ${MemoryFormatter.formatKb(app.currentRssKb)} • PIDs: ${app.pids.size} • OOM: ${app.oomScoreAdj} • ZRAM: ${MemoryFormatter.formatKb(app.swapKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                MemorySparklineChart(
                    samples = app.recentSamplesKb.map { it.toFloat() },
                    modifier = Modifier
                        .width(72.dp)
                        .height(26.dp),
                    lineColor = ramColor,
                    showPeakLine = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (app.isSystemApp) "SISTEMA" else "USUARIO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onForceStop,
                        enabled = !app.isCriticalProtected,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = null,
                            tint = if (app.isCriticalProtected) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MemScopeColors.CrimsonPeak
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detener", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = onFreezeClick,
                        enabled = !app.isCriticalProtected || app.isFrozen,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MemScopeColors.IndigoFrozen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (app.isFrozen) "Descongelar" else "Congelar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
