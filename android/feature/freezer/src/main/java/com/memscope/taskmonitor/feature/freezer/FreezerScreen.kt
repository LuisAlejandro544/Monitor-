package com.memscope.taskmonitor.feature.freezer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memscope.taskmonitor.core.model.InstalledAppFreezeInfo
import com.memscope.taskmonitor.core.ui.components.AppPackageIcon
import com.memscope.taskmonitor.core.ui.components.FrozenStateChip
import com.memscope.taskmonitor.core.ui.components.MemoryFormatter
import com.memscope.taskmonitor.core.ui.components.ProtectedPackageChip
import com.memscope.taskmonitor.core.ui.components.ShizukuActionBanner
import com.memscope.taskmonitor.core.ui.components.SoftHibernatedStateChip
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors

/**
 * Pantalla del Congelador de Aplicaciones (App Freezer) e Hibernación Suave.
 * Permite:
 * - Hibernación suave (AppOps + Standby Bucket): detiene procesos y restringe segundo plano
 *   SIN ocultar el ícono de la app en el lanzador.
 * - Congelación completa (DISABLED_USER): deshabilita el paquete hasta que el usuario lo active.
 */
@Composable
fun FreezerScreen(
    viewModel: FreezerViewModel,
    onNavigateToShizuku: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingApp by remember { mutableStateOf<InstalledAppFreezeInfo?>(null) }
    var pendingSoftApp by remember { mutableStateOf<InstalledAppFreezeInfo?>(null) }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Congelador de Apps",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Hibernadas (visibles): ${state.softHibernatedCount} • Congeladas: ${state.frozenCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = viewModel::refreshApps) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Recargar aplicaciones"
                        )
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

            if (state.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Buscar paquete para congelar...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FreezerFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.onFilterChange(f) },
                            label = { Text(f.labelEs) }
                        )
                    }
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                FreezerAppCard(
                    app = app,
                    shizukuReady = state.shizukuStatus.isFullyOperational,
                    userServiceReady = state.shizukuStatus.isUserServiceConnected,
                    onFreezeActionClick = { pendingApp = app },
                    onSoftHibernateClick = { pendingSoftApp = app }
                )
            }
        }
    }

    val confirmApp = pendingApp
    if (confirmApp != null) {
        val willFreeze = !confirmApp.isFrozen
        AlertDialog(
            onDismissRequest = { pendingApp = null },
            icon = {
                Icon(
                    imageVector = if (confirmApp.isSystemApp && willFreeze) {
                        Icons.Rounded.WarningAmber
                    } else {
                        Icons.Rounded.AcUnit
                    },
                    contentDescription = null,
                    tint = if (confirmApp.isSystemApp && willFreeze) {
                        MemScopeColors.AmberWarning
                    } else {
                        MemScopeColors.IndigoFrozen
                    }
                )
            },
            title = {
                Text(if (willFreeze) "Congelar ${confirmApp.appName}" else "Descongelar ${confirmApp.appName}")
            },
            text = {
                val warning = if (confirmApp.isSystemApp && willFreeze) {
                    "ATENCIÓN: Es una aplicación del sistema. Congelar componentes críticos del sistema puede causar inestabilidad o fallos en servicios esenciales.\n\n"
                } else {
                    ""
                }
                Text(
                    text = warning + if (willFreeze) {
                        "Se ejecutará setApplicationEnabledSetting(DISABLED_USER) y forceStopPackage para ${confirmApp.packageName}."
                    } else {
                        "Se restaurará el estado habilitado para ${confirmApp.packageName} y volverá a aparecer en el lanzador."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleFreeze(confirmApp)
                        pendingApp = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willFreeze) MemScopeColors.IndigoFrozen else MemScopeColors.EmeraldHealthy
                    )
                ) {
                    Text(if (willFreeze) "Congelar" else "Descongelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingApp = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val confirmSoftApp = pendingSoftApp
    if (confirmSoftApp != null) {
        val willHibernate = !confirmSoftApp.isSoftHibernated
        AlertDialog(
            onDismissRequest = { pendingSoftApp = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = if (willHibernate) MemScopeColors.AmberWarning else MemScopeColors.EmeraldHealthy
                )
            },
            title = {
                Text(
                    if (willHibernate) {
                        "Hibernación suave: ${confirmSoftApp.appName}"
                    } else {
                        "Despertar: ${confirmSoftApp.appName}"
                    }
                )
            },
            text = {
                Text(
                    text = if (willHibernate) {
                        "Restringe el segundo plano vía AppOps (RUN_ANY_IN_BACKGROUND) + Standby Bucket RESTRICTED y detiene sus procesos activos.\n\nSu ícono SEGUIRÁ VISIBLE en tu lanzador y podrás abrirla cuando quieras."
                    } else {
                        "Restaura los permisos de segundo plano (allow) y el Standby Bucket activo para ${confirmSoftApp.packageName}."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleSoftHibernation(confirmSoftApp)
                        pendingSoftApp = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willHibernate) MemScopeColors.AmberWarning else MemScopeColors.EmeraldHealthy
                    )
                ) {
                    Text(if (willHibernate) "Hibernar" else "Despertar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSoftApp = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun FreezerAppCard(
    app: InstalledAppFreezeInfo,
    shizukuReady: Boolean,
    userServiceReady: Boolean,
    onFreezeActionClick: () -> Unit,
    onSoftHibernateClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppPackageIcon(packageName = app.packageName, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))

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
                        } else if (app.isSoftHibernated) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SoftHibernatedStateChip()
                        } else if (app.isCriticalProtected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ProtectedPackageChip()
                        }
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val runtimeNote = when {
                        app.isCriticalProtected && app.protectionReason != null ->
                            "Protegida: ${app.protectionReason}"
                        app.isRunningNow ->
                            "En ejecución: ${MemoryFormatter.formatKb(app.currentRamKb)}"
                        else ->
                            "v${app.versionName} • ${if (app.isSystemApp) "Sistema" else "Usuario"}"
                    }
                    Text(
                        text = runtimeNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            app.isCriticalProtected -> MemScopeColors.EmeraldHealthy
                            app.isRunningNow -> MemScopeColors.CyanTelemetry
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSoftHibernateClick,
                    enabled = shizukuReady && userServiceReady && (!app.isCriticalProtected || app.isSoftHibernated),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = if (app.isSoftHibernated) MemScopeColors.EmeraldHealthy else MemScopeColors.AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (app.isSoftHibernated) "Despertar" else "Hibernar",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (app.isFrozen) {
                    OutlinedButton(
                        onClick = onFreezeActionClick,
                        enabled = shizukuReady,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MemScopeColors.EmeraldHealthy,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Activar", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onFreezeActionClick,
                        enabled = shizukuReady && !app.isCriticalProtected,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MemScopeColors.IndigoFrozen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Congelar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
