package com.memscope.taskmonitor.core.model

/**
 * Representa el estado de ejecución e importancia de un proceso en Android
 * según la clasificación de ActivityManager.RunningAppProcessInfo.
 */
enum class ProcessImportance(val labelEs: String) {
    FOREGROUND("Primer plano"),
    VISIBLE("Visible"),
    FOREGROUND_SERVICE("Servicio activo"),
    PERCEPTIBLE("Perceptible"),
    CACHED("En caché"),
    SERVICE("Segundo plano"),
    UNKNOWN("Desconocido")
}

/**
 * Snapshot en tiempo real del consumo de memoria RAM de una aplicación activa.
 * Combina datos de IActivityManager (PSS/PrivateDirty/Dalvik/Native) y del
 * núcleo Linux via UserService (/proc/<pid>/status: VmRSS, VmHWM, VmSwap).
 *
 * @property packageName Identificador único del paquete Android.
 * @property appName Nombre legible de la aplicación.
 * @property pids Lista de IDs de proceso asociados a este paquete.
 * @property currentPssKb Memoria proporcional compartida actual (Proportional Set Size) en KB.
 * @property currentRssKb Memoria física residente actual (Resident Set Size) en KB.
 * @property kernelPeakHwmKb Pico máximo histórico de RAM física reportado por el kernel (VmHWM) en KB.
 * @property sessionPeakPssKb Pico máximo de PSS observado durante el monitoreo de la sesión actual en KB.
 * @property privateDirtyKb Memoria privada modificada (no compartible) en KB.
 * @property nativePssKb Memoria nativa (C/C++) en KB.
 * @property dalvikPssKb Memoria gestionada por ART/Dalvik (Kotlin/Java) en KB.
 * @property swapKb Memoria comprimida en ZRAM / Swap en KB.
 * @property importance Nivel de prioridad actual del proceso principal.
 * @property oomScoreAdj Prioridad OOM del kernel (-1000 a 1000; menor = más protegido contra cierre).
 * @property isSystemApp Indica si pertenece a la partición del sistema.
 * @property isFrozen Indica si el paquete está congelado por el usuario.
 * @property cpuUsagePercent Porcentaje estimado de CPU consumido por los procesos de la app.
 * @property totalThreads Número total de hilos (Threads) activos del paquete.
 * @property isCriticalProtected Indica si es un paquete crítico del sistema protegido contra congelación.
 * @property protectionReason Motivo de protección si aplica.
 * @property recentSamplesKb Serie corta reciente de muestras PSS en KB para mini-gráficas en vivo.
 * @property updatedAtMillis Marca de tiempo de la última lectura.
 */
data class AppMemoryInfo(
    val packageName: String,
    val appName: String,
    val pids: List<Int>,
    val currentPssKb: Long,
    val currentRssKb: Long,
    val kernelPeakHwmKb: Long,
    val sessionPeakPssKb: Long,
    val privateDirtyKb: Long,
    val nativePssKb: Long,
    val dalvikPssKb: Long,
    val swapKb: Long,
    val importance: ProcessImportance,
    val oomScoreAdj: Int,
    val isSystemApp: Boolean,
    val isFrozen: Boolean,
    val cpuUsagePercent: Float = 0f,
    val totalThreads: Int = 0,
    val isCriticalProtected: Boolean = false,
    val protectionReason: String? = null,
    val recentSamplesKb: List<Long> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    /** Pico real máximo combinando medición PSS de sesión y VmHWM del kernel. */
    val effectivePeakKb: Long
        get() = maxOf(kernelPeakHwmKb, sessionPeakPssKb, currentPssKb)
}

/**
 * Registro histórico acumulado del consumo de RAM de una aplicación mientras corría.
 * Se persiste en base de datos Room para consultar cuánto consumió cada app y sus picos.
 */
data class AppPeakRecord(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val peakPssKb: Long,
    val peakRssHwmKb: Long,
    val averagePssKb: Long,
    val lastMeasuredPssKb: Long,
    val sampleCount: Long,
    val activeSecondsAccumulated: Long,
    val peakTimestampMillis: Long,
    val lastSeenMillis: Long
)

/**
 * Muestra puntual de memoria en un instante dado para construir gráficas históricas.
 */
data class MemoryTimeSample(
    val packageName: String,
    val pssKb: Long,
    val rssKb: Long,
    val hwmKb: Long,
    val timestampMillis: Long
)

/**
 * Resumen global de la memoria RAM física y ZRAM/Swap del dispositivo.
 */
data class SystemMemoryOverview(
    val totalRamKb: Long,
    val availableRamKb: Long,
    val usedRamKb: Long,
    val cachedKb: Long,
    val buffersKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val isLowMemory: Boolean,
    val activeProcessCount: Int,
    val historyUsedPercent: List<Float> = emptyList()
) {
    val usedPercent: Float
        get() = if (totalRamKb > 0) (usedRamKb.toFloat() / totalRamKb.toFloat()).coerceIn(0f, 1f) else 0f

    val swapUsedKb: Long
        get() = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
}
