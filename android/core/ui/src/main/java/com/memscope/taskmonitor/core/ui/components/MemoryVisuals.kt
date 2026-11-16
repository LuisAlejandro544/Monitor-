package com.memscope.taskmonitor.core.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Caché acotada de íconos decodificados para reducir el consumo propio de RAM de MemScope
 * y evitar reconstrucción de bitmaps al hacer scroll.
 */
private object AppIconMemoryCache {
    val cache = LruCache<String, ImageBitmap>(72)
}

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
 * con caché en LruCache y tamaño acotado a 96x96 px.
 */
@Composable
fun AppPackageIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf(AppIconMemoryCache.cache.get(packageName))
    }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    drawableToBitmap(drawable).asImageBitmap()
                }.getOrNull()
            }
            if (loaded != null) {
                AppIconMemoryCache.cache.put(packageName, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
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
        val src = drawable.bitmap
        return if (src.width > 96 || src.height > 96) {
            Bitmap.createScaledBitmap(src, 96, 96, true)
        } else {
            src
        }
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(96)
    val h = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(96)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

/**
 * Gráfica estilo Monitor de Rendimiento de PC (Administrador de Tareas de escritorio):
 * - Rejilla técnica horizontal y vertical
 * - Escala dinámica inteligente para apreciar variaciones y picos reales sin quedar aplastada
 * - Línea punteada de referencia de Pico Máximo, punto luminoso en vivo y relleno degradado
 */
@Composable
fun MemorySparklineChart(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MemScopeColors.CyanTelemetry,
    peakMarkerColor: Color = MemScopeColors.CrimsonPeak,
    showPeakLine: Boolean = true,
    dynamicScale: Boolean = true,
    fixedMax: Float? = null
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val canvasBg = MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
    val borderColor = MaterialTheme.colorScheme.outline

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(canvasBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Cuadrícula vertical estilo osciloscopio de PC (8 columnas)
        val cols = 8
        for (c in 1 until cols) {
            val x = w * (c / cols.toFloat())
            drawLine(
                color = gridColor.copy(alpha = 0.35f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
        }

        // Cuadrícula horizontal (4 filas)
        val rows = 4
        for (r in 1 until rows) {
            val y = h * (r / rows.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        if (samples.isEmpty()) return@Canvas

        val rawMax = samples.maxOrNull() ?: 1f
        val rawMin = samples.minOrNull() ?: 0f

        val (minVal, maxVal) = if (fixedMax != null) {
            0f to fixedMax.coerceAtLeast(1f)
        } else if (dynamicScale) {
            val span = (rawMax - rawMin).coerceAtLeast((rawMax * 0.12f).coerceAtLeast(2f))
            val low = (rawMin - span * 0.35f).coerceAtLeast(0f)
            val high = (rawMax + span * 0.25f).coerceAtLeast(low + 1f)
            low to high
        } else {
            0f to rawMax.coerceAtLeast(1f)
        }

        val range = (maxVal - minVal).coerceAtLeast(1f)
        val padTop = h * 0.08f
        val padBottom = h * 0.08f
        val usableHeight = (h - padTop - padBottom).coerceAtLeast(1f)
        val stepX = if (samples.size > 1) w / (samples.size - 1).toFloat() else w

        val linePath = Path()
        val fillPath = Path()
        var lastX = 0f
        var lastY = h - padBottom

        samples.forEachIndexed { index, value ->
            val x = if (samples.size > 1) index * stepX else w * 0.5f
            val normalized = ((value - minVal) / range).coerceIn(0f, 1f)
            val y = h - padBottom - (normalized * usableHeight)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }

        if (samples.size == 1) {
            linePath.moveTo(0f, lastY)
            linePath.lineTo(w, lastY)
            fillPath.reset()
            fillPath.moveTo(0f, h)
            fillPath.lineTo(0f, lastY)
            fillPath.lineTo(w, lastY)
            fillPath.lineTo(w, h)
            fillPath.close()
            lastX = w
        } else {
            fillPath.lineTo(lastX, h)
            fillPath.close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.38f),
                    lineColor.copy(alpha = 0.04f)
                )
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        if (showPeakLine) {
            val peakNorm = ((rawMax - minVal) / range).coerceIn(0f, 1f)
            val peakY = h - padBottom - (peakNorm * usableHeight)
            drawLine(
                color = peakMarkerColor.copy(alpha = 0.85f),
                start = Offset(0f, peakY),
                end = Offset(w, peakY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        }

        // Pulso luminoso en el último punto medido en tiempo real
        drawCircle(
            color = lineColor.copy(alpha = 0.35f),
            radius = 5.dp.toPx(),
            center = Offset(lastX, lastY)
        )
        drawCircle(
            color = lineColor,
            radius = 2.6.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}

/**
 * Gráfica avanzada de telemetría estilo PC (Administrador de Tareas / btop) con ejes,
 * escala numérica derecha y resumen inferior de Mínimo / Promedio / Pico / Actual.
 */
@Composable
fun PcTelemetryGraphPanel(
    samples: List<Float>,
    unitLabel: (Float) -> String,
    modifier: Modifier = Modifier,
    lineColor: Color = MemScopeColors.CyanTelemetry,
    peakMarkerColor: Color = MemScopeColors.CrimsonPeak,
    graphHeight: Dp = 116.dp,
    dynamicScale: Boolean = true
) {
    val rawMax = samples.maxOrNull() ?: 0f
    val rawMin = samples.minOrNull() ?: 0f
    val rawAvg = if (samples.isNotEmpty()) samples.sum() / samples.size else 0f
    val rawCurr = samples.lastOrNull() ?: 0f

    val span = (rawMax - rawMin).coerceAtLeast((rawMax * 0.12f).coerceAtLeast(2f))
    val axisMin = if (dynamicScale) (rawMin - span * 0.35f).coerceAtLeast(0f) else 0f
    val axisMax = if (dynamicScale) (rawMax + span * 0.25f).coerceAtLeast(axisMin + 1f) else rawMax.coerceAtLeast(1f)
    val axisMid = (axisMin + axisMax) * 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemorySparklineChart(
                samples = samples,
                modifier = Modifier
                    .weight(1f)
                    .height(graphHeight),
                lineColor = lineColor,
                peakMarkerColor = peakMarkerColor,
                showPeakLine = true,
                dynamicScale = dynamicScale
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier.height(graphHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = unitLabel(axisMax),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = unitLabel(axisMid),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
                Text(
                    text = unitLabel(axisMin),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TelemetryMiniStat("MÍN", unitLabel(rawMin), MaterialTheme.colorScheme.onSurfaceVariant)
            TelemetryMiniStat("PROM", unitLabel(rawAvg), MemScopeColors.EmeraldHealthy)
            TelemetryMiniStat("PICO", unitLabel(rawMax), peakMarkerColor)
            TelemetryMiniStat("ACTUAL", unitLabel(rawCurr), lineColor)
        }
    }
}

@Composable
private fun TelemetryMiniStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

/**
 * Tarjeta superior estilo Monitor de Recursos de PC:
 * - Barra segmentada de composición de memoria (Apps / Kernel / Caché / Libre) con leyenda técnica
 * - Barra independiente de compresión ZRAM / Swap
 * - Osciloscopio en vivo de picos y uso porcentual con rejilla y escala lateral
 */
@Composable
fun SystemMemoryHeaderCard(
    overview: SystemMemoryOverview,
    shizukuStatus: ShizukuStatus,
    modifier: Modifier = Modifier
) {
    val totalKb = overview.totalRamKb.coerceAtLeast(1L)
    val kernelKb = overview.kernelMemKb.coerceIn(0L, overview.usedRamKb)
    val appsAndOtherUsedKb = (overview.usedRamKb - kernelKb).coerceAtLeast(0L)
    val cacheAndBufKb = (overview.cachedKb + overview.buffersKb).coerceAtMost(overview.availableRamKb)
    val freePureKb = (overview.availableRamKb - cacheAndBufKb).coerceAtLeast(0L)

    val appsFrac = (appsAndOtherUsedKb.toFloat() / totalKb.toFloat()).coerceIn(0f, 1f)
    val kernelFrac = (kernelKb.toFloat() / totalKb.toFloat()).coerceIn(0f, (1f - appsFrac).coerceAtLeast(0f))
    val cacheFrac = (cacheAndBufKb.toFloat() / totalKb.toFloat()).coerceIn(0f, (1f - appsFrac - kernelFrac).coerceAtLeast(0f))
    val freeFrac = (1f - appsFrac - kernelFrac - cacheFrac).coerceAtLeast(0.01f)

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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "MEMORIA FÍSICA DEL SISTEMA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
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
                        text = "Disponible: ${MemoryFormatter.formatKb(overview.availableRamKb)}  •  Procesos: ${overview.activeProcessCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (overview.usedPercent > 0.85f) MemScopeColors.CrimsonPeak else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        (if (overview.usedPercent > 0.85f) MemScopeColors.CrimsonPeak else MaterialTheme.colorScheme.primary).copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "${(overview.usedPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (overview.usedPercent > 0.85f) MemScopeColors.CrimsonPeak else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra segmentada estilo Monitor de Recursos de PC (Apps / Kernel / Caché / Libre)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            ) {
                if (appsFrac > 0.005f) {
                    Box(
                        modifier = Modifier
                            .weight(appsFrac)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                if (kernelFrac > 0.005f) {
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    )
                    Box(
                        modifier = Modifier
                            .weight(kernelFrac)
                            .fillMaxHeight()
                            .background(MemScopeColors.IndigoFrozen)
                    )
                }
                if (cacheFrac > 0.005f) {
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    )
                    Box(
                        modifier = Modifier
                            .weight(cacheFrac)
                            .fillMaxHeight()
                            .background(MemScopeColors.AmberWarning)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(freeFrac)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Leyenda técnica de composición de memoria
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MemoryLegendItem("En uso", MemoryFormatter.formatKb(appsAndOtherUsedKb), MaterialTheme.colorScheme.primary)
                MemoryLegendItem("Kernel", MemoryFormatter.formatKb(kernelKb), MemScopeColors.IndigoFrozen)
                MemoryLegendItem("Caché", MemoryFormatter.formatKb(cacheAndBufKb), MemScopeColors.AmberWarning)
                MemoryLegendItem("Libre", MemoryFormatter.formatKb(freePureKb), MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Gráfica estilo Administrador de Tareas de PC
            PcTelemetryGraphPanel(
                samples = overview.historyUsedPercent.map { it * 100f },
                unitLabel = { pct -> "${pct.toInt().coerceIn(0, 100)}%" },
                graphHeight = 86.dp,
                lineColor = MaterialTheme.colorScheme.primary,
                peakMarkerColor = MemScopeColors.CrimsonPeak,
                dynamicScale = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Barra compacta de ZRAM / Swap
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ZRAM/Swap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val zramFrac = overview.swapUsedPercent.coerceIn(0f, 1f)
                    if (zramFrac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zramFrac)
                                .fillMaxHeight()
                                .background(MemScopeColors.EmeraldHealthy)
                        )
                    }
                }
                Text(
                    text = "${MemoryFormatter.formatKb(overview.swapUsedKb)} / ${MemoryFormatter.formatKb(overview.swapTotalKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MemoryLegendItem(label: String, value: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
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
