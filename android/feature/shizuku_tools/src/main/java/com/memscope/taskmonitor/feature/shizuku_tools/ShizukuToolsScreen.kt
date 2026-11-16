package com.memscope.taskmonitor.feature.shizuku_tools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memscope.taskmonitor.core.ui.components.PrivilegeBadge
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors

/**
 * Pantalla avanzada de Shizuku, UserService y Herramientas de Sistema.
 * Incluye control del Foreground Service de monitoreo continuo, frecuencia de muestreo
 * y herramienta de permisos runtime compatible con Android 8-15.
 */
@Composable
fun ShizukuToolsScreen(
    viewModel: ShizukuToolsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundMonitoring(true)
        }
    }

    var targetPackage by remember { mutableStateOf("") }
    var targetPermission by remember { mutableStateOf("android.permission.PACKAGE_USAGE_STATS") }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Shizuku y Diagnóstico",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Estado del Binder, UserService y privilegios del sistema",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = viewModel::refreshStatus) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Comprobar estado")
                    }
                }
            }

            // Tarjeta de diagnóstico Shizuku / Sui
            item {
                val st = state.status
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Estado del Puente Shizuku", style = MaterialTheme.typography.titleMedium)
                            }
                            PrivilegeBadge(status = st)
                        }

                        StatusRow("Binder activo (pingBinder)", st.isBinderAlive)
                        StatusRow("Permiso Shizuku otorgado", st.isPermissionGranted)
                        StatusRow("UserService conectado (/proc)", st.isUserServiceConnected)
                        StatusRow("Sui (Magisk) detectado", st.isSuiDetected)

                        Text(
                            text = "Versión API Shizuku: ${if (st.serverVersion >= 0) st.serverVersion else "—"}  •  UID: ${if (st.uid >= 0) st.uid else "—"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = viewModel::requestPermission,
                                enabled = st.isBinderAlive && !st.isPermissionGranted,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Autorizar Shizuku")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (st.isUserServiceConnected) {
                                        viewModel.unbindUserService()
                                    } else {
                                        viewModel.bindUserService()
                                    }
                                },
                                enabled = st.isFullyOperational,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (st.isUserServiceConnected) "Desconectar SVC" else "Conectar SVC")
                            }
                        }
                    }
                }
            }

            // Monitoreo continuo en segundo plano (Foreground Service + Notificación)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = if (state.preferences.backgroundMonitoringEnabled) {
                                    MemScopeColors.EmeraldHealthy
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Monitoreo Real en Segundo Plano",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Mantiene un Foreground Service con notificación en vivo para registrar picos de RAM y CPU mientras usas otras apps.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = state.preferences.backgroundMonitoringEnabled,
                            onCheckedChange = { enable ->
                                if (!enable) {
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
                        )
                    }
                }
            }

            // Frecuencia de muestreo del monitor
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
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Intervalo de Muestreo de RAM", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Un intervalo menor detecta picos rápidos con mayor precisión; un intervalo mayor ahorra batería.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val options = listOf(1000L to "1s (Alta)", 2000L to "2s (Equilibrada)", 3000L to "3s", 5000L to "5s (Ahorro)")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEach { (ms, label) ->
                                FilterChip(
                                    selected = state.preferences.pollingIntervalMs == ms,
                                    onClick = { viewModel.setPollingInterval(ms) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // Herramienta directa de otorgamiento de permisos (IPackageManager + IPermissionManager + UserService)
            item {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MemScopeColors.AmberWarning
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Otorgar Permiso (Android 8–15)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Usa IPackageManager / IPermissionManager con ShizukuBinderWrapper (y respaldo en UserService para Android 14–15) para conceder permisos runtime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = targetPackage,
                            onValueChange = { targetPackage = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Nombre del paquete (ej. com.ejemplo.app)") },
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = targetPermission,
                            onValueChange = { targetPermission = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Permiso Android (ej. android.permission.READ_EXTERNAL_STORAGE)") },
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = { viewModel.grantRuntimePermission(targetPackage, targetPermission) },
                            enabled = state.status.isFullyOperational,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Otorgar Permiso con Shizuku")
                        }
                    }
                }
            }

            // Guía rápida para activar sin PC (Android 11+)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Activar Shizuku sin PC (Android 11+)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Instala la app oficial de Shizuku.\n2. Activa Opciones de Desarrollador y Depuración Inalámbrica.\n3. En Shizuku elige 'Iniciar mediante depuración inalámbrica', empareja el código y vuelve aquí para autorizar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Icon(
            imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MemScopeColors.EmeraldHealthy else MemScopeColors.CrimsonPeak,
            modifier = Modifier.size(18.dp)
        )
    }
}
