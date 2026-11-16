package com.memscope.taskmonitor.feature.monitor

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memscope.taskmonitor.core.ui.components.AppPackageIcon
import com.memscope.taskmonitor.core.ui.components.MemoryFormatter
import com.memscope.taskmonitor.core.ui.components.MemorySparklineChart
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors
import kotlinx.coroutines.launch

/**
 * Pantalla dedicada de detalle profundo para una aplicación específica.
 * Muestra la gráfica histórica de RAM mientras corría, picos (kernel VmHWM y sesión),
 * desglose por segmentos (Native/Dalvik/PrivateDirty/ZRAM), acciones de congelación/cierre
 * y un diálogo emergente para otorgar/revocar permisos vía IPackageManager + Shizuku.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: MonitorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val peaksByPkg by viewModel.peakHistoryByPackage.collectAsStateWithLifecycle()
    val samples by viewModel.observePackageSamples(packageName).collectAsStateWithLifecycle(emptyList())

    val liveApp = state.apps.firstOrNull { it.packageName == packageName }
    val historyRecord = peaksByPkg[packageName]

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var permissionsList by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }

    LaunchedEffect(state.actionFeedback) {
        val msg = state.actionFeedback
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppPackageIcon(packageName = packageName, size = 32.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = liveApp?.appName ?: historyRecord?.appName ?: packageName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 4 tarjetas métricas principales (RAM actual, RSS, Pico Kernel, Promedio histórico)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBoxCard(
                        title = "RAM Actual (PSS)",
                        value = MemoryFormatter.formatKb(liveApp?.currentPssKb ?: 0L),
                        accent = MemScopeColors.CyanTelemetry,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBoxCard(
                        title = "Pico Histórico",
                        value = MemoryFormatter.formatKb(
                            maxOf(
                                liveApp?.effectivePeakKb ?: 0L,
                                historyRecord?.peakPssKb ?: 0L,
                                historyRecord?.peakRssHwmKb ?: 0L
                            )
                        ),
                        accent = MemScopeColors.CrimsonPeak,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBoxCard(
                        title = "Pico Kernel (VmHWM)",
                        value = MemoryFormatter.formatKb(
                            maxOf(liveApp?.kernelPeakHwmKb ?: 0L, historyRecord?.peakRssHwmKb ?: 0L)
                        ),
                        accent = MemScopeColors.AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBoxCard(
                        title = "Promedio Activo",
                        value = MemoryFormatter.formatKb(historyRecord?.averagePssKb ?: liveApp?.currentPssKb ?: 0L),
                        accent = MemScopeColors.EmeraldHealthy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Gráfica de historial mientras la app corría
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Curva de Consumo Mientras Corría",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${samples.size} muestras",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val chartPoints = if (samples.isNotEmpty()) {
                            samples.map { it.pssKb.toFloat() }
                        } else {
                            liveApp?.recentSamplesKb?.map { it.toFloat() }.orEmpty()
                        }

                        MemorySparklineChart(
                            samples = chartPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val activeTime = historyRecord?.activeSecondsAccumulated ?: 0L
                        Text(
                            text = "Tiempo monitoreado en ejecución: ${MemoryFormatter.formatDurationSeconds(activeTime)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Desglose técnico de memoria del proceso
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Desglose de Segmentos de Memoria",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        DetailRow("Uso de CPU", MemoryFormatter.formatCpu(liveApp?.cpuUsagePercent ?: 0f))
                        DetailRow("Hilos Activos (Threads)", (liveApp?.totalThreads ?: 0).toString())
                        DetailRow("Memoria Física (RSS)", MemoryFormatter.formatKb(liveApp?.currentRssKb ?: 0L))
                        DetailRow("Private Dirty (Exclusiva)", MemoryFormatter.formatKb(liveApp?.privateDirtyKb ?: 0L))
                        DetailRow("Native Heap (C/C++)", MemoryFormatter.formatKb(liveApp?.nativePssKb ?: 0L))
                        DetailRow("Dalvik / ART (Kotlin/Java)", MemoryFormatter.formatKb(liveApp?.dalvikPssKb ?: 0L))
                        DetailRow("Swap / ZRAM", MemoryFormatter.formatKb(liveApp?.swapKb ?: 0L))
                        DetailRow("PIDs Activos", liveApp?.pids?.joinToString(", ") ?: "Inactiva")
                        DetailRow("OOM Score Adj", liveApp?.oomScoreAdj?.toString() ?: "—")
                    }
                }
            }

            // Panel de acciones privilegiadas Shizuku
            item {
                val isProtected = liveApp?.isCriticalProtected == true
                val isFrozen = liveApp?.isFrozen == true
                val isSoftHibernated = liveApp?.isSoftHibernated == true
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Acciones de Control (Shizuku)",
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (isProtected) {
                            Text(
                                text = "Paquete protegido contra bloqueo: ${liveApp?.protectionReason ?: "Componente crítico del sistema"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MemScopeColors.EmeraldHealthy
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.forceStopApp(
                                        packageName,
                                        liveApp?.appName ?: historyRecord?.appName ?: packageName
                                    )
                                },
                                enabled = !isProtected,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.StopCircle,
                                    contentDescription = null,
                                    tint = MemScopeColors.CrimsonPeak,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Forzar Cierre")
                            }

                            Button(
                                onClick = {
                                    viewModel.freezeOrUnfreezeApp(
                                        packageName = packageName,
                                        appName = liveApp?.appName ?: historyRecord?.appName ?: packageName,
                                        freeze = !isFrozen
                                    )
                                },
                                enabled = !isProtected || isFrozen,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MemScopeColors.IndigoFrozen
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AcUnit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isFrozen) "Descongelar" else "Congelar")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.softHibernateApp(
                                    packageName = packageName,
                                    appName = liveApp?.appName ?: historyRecord?.appName ?: packageName,
                                    hibernate = !isSoftHibernated
                                )
                            },
                            enabled = (!isProtected || isSoftHibernated) && state.shizukuStatus.isUserServiceConnected,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = null,
                                tint = if (isSoftHibernated) MemScopeColors.EmeraldHealthy else MemScopeColors.AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSoftHibernated) {
                                    "Despertar de Hibernación Suave"
                                } else {
                                    "Hibernación Suave (Sin ocultar ícono)"
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    permissionsList = viewModel.loadPackagePermissions(packageName)
                                    showPermissionsDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gestionar Permisos Runtime (IPackageManager)")
                        }
                    }
                }
            }
        }
    }

    if (showPermissionsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = { Text("Permisos Runtime del Paquete") },
            text = {
                if (permissionsList.isEmpty()) {
                    Text("Esta aplicación no declara permisos solicitables.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(permissionsList, key = { it.first }) { (perm, granted) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = perm.removePrefix("android.permission."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Switch(
                                    checked = granted,
                                    onCheckedChange = { enable ->
                                        val action = if (enable) {
                                            viewModel::grantPermission
                                        } else {
                                            viewModel::revokePermission
                                        }
                                        action(packageName, perm) { ok, err ->
                                            scope.launch {
                                                if (ok) {
                                                    permissionsList = viewModel.loadPackagePermissions(packageName)
                                                } else {
                                                    snackbarHostState.showSnackbar("Error: ${err ?: "No permitido"}")
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun MetricBoxCard(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
