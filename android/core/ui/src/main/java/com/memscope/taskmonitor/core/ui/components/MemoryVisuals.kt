package com.memscope.taskmonitor.core.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memscope.taskmonitor.core.model.ShizukuPrivilegeLevel
import com.memscope.taskmonitor.core.model.ShizukuStatus
import com.memscope.taskmonitor.core.model.SystemMemoryOverview
import com.memscope.taskmonitor.core.ui.theme.MemScopeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilidades de formateo de memoria (KB -> MB / GB) y marcas de tiempo legibles.
 */
object MemoryFormatter {
    fun formatKb(kb: Long): String {
        if (kb <= 0L) return "0 MB"
        val mb = kb / 1024.0
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    fun formatCpu(percent: Float): String {
        val safe = percent.coerceIn(0f, 100f)
        return String.format(Locale.US, "%.1f%%", safe)
    }

    fun formatDurationSeconds(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> "${hrs}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "—"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun ramSeverityColor(kb: Long): Color {
        val mb = kb / 1024.0
        return when {
            mb >= 512.0 -> MemScopeColors.CrimsonPeak
            mb >= 200.0 -> MemScopeColors.AmberWarning
            else -> MemScopeColors.EmeraldHealthy
        }
    }
}

/**
 * Carga el ícono de una aplicación instalada de forma asíncrona en Dispatchers.IO
 * para evitar caídas de FPS al desplazar listas largas en Jetpack Compose.
 */
@Composable
fun AppPackageIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable).asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = packageName,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(128)
    val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(128)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

/**
 * Gráfica lineal estilo Administrador de Tareas dibujada directamente sobre Compose Canvas.
 * Muestra la evolución del consumo de RAM junto con una línea punteada de referencia para el pico.
 */
@Composable
fun MemorySparklineChart(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MemScopeColors.CyanTelemetry,
    peakMarkerColor: Color = MemScopeColors.CrimsonPeak,
    showPeakLine: Boolean = true
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Cuadrícula sutil de telemetría
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        if (samples.isEmpty()) return@Canvas

        val maxVal = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minVal = 0f
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = if (samples.size > 1) w / (samples.size - 1).toFloat() else w

        val linePath = Path()
        val fillPath = Path()

        samples.forEachIndexed { index, value ->
            val x = if (samples.size > 1) index * stepX else w / 2f
            val normalized = ((value - minVal) / range).coerceIn(0f, 1f)
            val y = h - (normalized * (h * 0.88f)) - (h * 0.06f)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val lastX = if (samples.size > 1) (samples.size - 1) * stepX else w / 2f
        fillPath.lineTo(lastX, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.35f),
                    lineColor.copy(alpha = 0.02f)
                )
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        if (showPeakLine) {
            val peakY = h * 0.06f
            drawLine(
                color = peakMarkerColor.copy(alpha = 0.7f),
                start = Offset(0f, peakY),
                end = Offset(w, peakY),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

/**
 * Tarjeta superior con el resumen global de RAM del sistema, ZRAM/Swap y curva histórica.
 */
@Composable
fun SystemMemoryHeaderCard(
    overview: SystemMemoryOverview,
    shizukuStatus: ShizukuStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Memoria Física del Sistema",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                PrivilegeBadge(status = shizukuStatus)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "${MemoryFormatter.formatKb(overview.usedRamKb)} / ${MemoryFormatter.formatKb(overview.totalRamKb)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Libre: ${MemoryFormatter.formatKb(overview.availableRamKb)}  •  Caché: ${MemoryFormatter.formatKb(overview.cachedKb)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${(overview.usedPercent * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (overview.usedPercent > 0.85f) MemScopeColors.CrimsonPeak else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra segmentada de uso de RAM (En uso / Caché / Libre)
            val total = overview.totalRamKb.coerceAtLeast(1L).toFloat()
            val usedFrac = (overview.usedRamKb / total).coerceIn(0f, 1f)
            val cachedFrac = (overview.cachedKb / total).coerceIn(0f, (1f - usedFrac).coerceAtLeast(0f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (usedFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(usedFrac.coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                if (cachedFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(cachedFrac.coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(MemScopeColors.AmberWarning)
                    )
                }
                val freeFrac = (1f - usedFrac - cachedFrac).coerceAtLeast(0.01f)
                Box(
                    modifier = Modifier
                        .weight(freeFrac)
                        .height(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MemorySparklineChart(
                samples = overview.historyUsedPercent.map { it * 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                showPeakLine = false
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ZRAM/Swap: ${MemoryFormatter.formatKb(overview.swapUsedKb)} / ${MemoryFormatter.formatKb(overview.swapTotalKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Procesos activos: ${overview.activeProcessCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PrivilegeBadge(
    status: ShizukuStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg, label) = when (status.privilegeLevel) {
        ShizukuPrivilegeLevel.ROOT -> Triple(
            MemScopeColors.EmeraldHealthy.copy(alpha = 0.16f),
            MemScopeColors.EmeraldHealthy,
            "ROOT UID 0"
        )
        ShizukuPrivilegeLevel.ADB_SHELL -> Triple(
            MemScopeColors.CyanTelemetry.copy(alpha = 0.16f),
            MemScopeColors.CyanTelemetry,
            "ADB UID 2000"
        )
        ShizukuPrivilegeLevel.NONE -> Triple(
            MemScopeColors.CrimsonPeak.copy(alpha = 0.16f),
            MemScopeColors.CrimsonPeak,
            if (status.isBinderAlive) "SIN PERMISO" else "SHIZUKU INACTIVO"
        )
    }

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Banner de alerta interactivo cuando Shizuku/Sui no está activo o falta permiso.
 */
@Composable
fun ShizukuActionBanner(
    status: ShizukuStatus,
    onRequestPermission: () -> Unit,
    onOpenShizukuTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (status.isFullyOperational) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MemScopeColors.AmberWarning.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, MemScopeColors.AmberWarning.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MemScopeColors.AmberWarning,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            !status.isBinderAlive -> "Shizuku / Sui no está ejecutándose"
                            status.isPreV11Unsupported -> "Versión de Shizuku no compatible (< v11)"
                            else -> "Permiso de Shizuku requerido"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            !status.isBinderAlive -> "Inicia Shizuku (Depuración inalámbrica o Root) para leer procesos globales y congelar apps."
                            else -> "Autoriza a MemScope para acceder a IActivityManager, IPackageManager y /proc."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = if (status.isBinderAlive) onRequestPermission else onOpenShizukuTab,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MemScopeColors.AmberWarning,
                    contentColor = Color(0xFF1F1600)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (status.isBinderAlive) "Autorizar" else "Configurar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FrozenStateChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MemScopeColors.IndigoFrozen.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MemScopeColors.IndigoFrozen.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AcUnit,
                contentDescription = null,
                tint = MemScopeColors.IndigoFrozen,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "CONGELADA",
                style = MaterialTheme.typography.labelSmall,
                color = MemScopeColors.IndigoFrozen
            )
        }
    }
}

@Composable
fun SoftHibernatedStateChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MemScopeColors.AmberWarning.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MemScopeColors.AmberWarning.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = MemScopeColors.AmberWarning,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "HIBERNADA",
                style = MaterialTheme.typography.labelSmall,
                color = MemScopeColors.AmberWarning
            )
        }
    }
}

@Composable
fun ProtectedPackageChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MemScopeColors.EmeraldHealthy.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MemScopeColors.EmeraldHealthy.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MemScopeColors.EmeraldHealthy,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "PROTEGIDA",
                style = MaterialTheme.typography.labelSmall,
                color = MemScopeColors.EmeraldHealthy
            )
        }
    }
}
