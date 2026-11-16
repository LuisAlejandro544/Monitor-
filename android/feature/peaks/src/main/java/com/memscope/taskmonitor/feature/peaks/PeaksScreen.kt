package com.memscope.taskmonitor.feature.peaks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TrendingUp
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memscope.taskmonitor.core.model.AppPeakRecord
import com.memscope.taskmonitor.core.model.AppVisibilityFilter
import com.memscope.taskmonitor.core.ui.components.AppPackageIcon
import com.memscope.taskmonitor.core.ui.components.MemoryFormatter
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors

/**
 * Pantalla de Picos e Historial de Consumo de RAM.
 * Permite auditar cuánta memoria consumieron las aplicaciones mientras corrían,
 * su promedio activo, el pico máximo registrado por PSS y el pico del kernel (VmHWM).
 */
@Composable
fun PeaksScreen(
    viewModel: PeaksViewModel,
    onOpenAppDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                            text = "Picos e Historial RAM",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Consumo observado mientras corrían las apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Limpiar historial",
                            tint = MemScopeColors.CrimsonPeak
                        )
                    }
                }
            }

            // Tarjeta resumen de picos globales
            item {
                val topApp = state.records.firstOrNull()
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apps registradas: ${state.records.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (topApp != null) {
                                    "Mayor pico: ${topApp.appName}"
                                } else {
                                    "Sin registros históricos aún"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (topApp != null) {
                                Text(
                                    text = "Pico PSS: ${MemoryFormatter.formatKb(topApp.peakPssKb)} • VmHWM: ${MemoryFormatter.formatKb(topApp.peakRssHwmKb)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MemScopeColors.CrimsonPeak
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.ShowChart,
                            contentDescription = null,
                            tint = MemScopeColors.CyanTelemetry,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Filtrar historial por app...") },
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppVisibilityFilter.entries.forEach { f ->
                            FilterChip(
                                selected = state.filter == f,
                                onClick = { viewModel.onFilterChange(f) },
                                label = { Text(f.labelEs) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PeakSortMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.sortMode == mode,
                                onClick = { viewModel.onSortChange(mode) },
                                label = { Text(mode.labelEs) }
                            )
                        }
                    }
                }
            }

            items(state.records, key = { it.packageName }) { record ->
                PeakHistoryCard(
                    record = record,
                    maxReferenceKb = state.maxPeakOverallKb,
                    onClick = { onOpenAppDetail(record.packageName) }
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Borrar historial de picos") },
            text = { Text("Se eliminarán todas las muestras guardadas y los picos máximos históricos registrados en la base de datos local.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MemScopeColors.CrimsonPeak)
                ) {
                    Text("Borrar todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PeakHistoryCard(
    record: AppPeakRecord,
    maxReferenceKb: Long,
    onClick: () -> Unit
) {
    val peakMax = maxOf(record.peakPssKb, record.peakRssHwmKb)
    val ratio = (peakMax.toFloat() / maxReferenceKb.toFloat()).coerceIn(0.03f, 1f)
    val peakColor = MemoryFormatter.ramSeverityColor(record.peakPssKb)

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
                AppPackageIcon(packageName = record.packageName, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Activa: ${MemoryFormatter.formatDurationSeconds(record.activeSecondsAccumulated)} • Pico a las ${MemoryFormatter.formatTimestamp(record.peakTimestampMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = peakColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = MemoryFormatter.formatKb(record.peakPssKb),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = peakColor
                        )
                    }
                    Text(
                        text = "Prom: ${MemoryFormatter.formatKb(record.averagePssKb)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = peakColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pico Kernel (VmHWM): ${MemoryFormatter.formatKb(record.peakRssHwmKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Última: ${MemoryFormatter.formatKb(record.lastMeasuredPssKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
