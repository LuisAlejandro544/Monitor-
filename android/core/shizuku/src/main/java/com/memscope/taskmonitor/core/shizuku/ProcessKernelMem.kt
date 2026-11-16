package com.memscope.taskmonitor.core.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Modelo Parcelable que transporta vía Binder IPC las lecturas del núcleo Linux
 * para un PID específico:
 * - vmRssKb: Memoria física residente actual (VmRSS / smaps_rollup Rss).
 * - vmHwmKb: Pico máximo de RAM residente alcanzado durante la vida del proceso (VmHWM).
 * - vmSwapKb: Memoria en ZRAM / Swap (VmSwap / SwapPss).
 * - oomScoreAdj: Puntuación de prioridad OOM killer del kernel.
 * - procCpuJiffies: Tiempo acumulado de CPU del proceso (utime + stime en /proc/<pid>/stat).
 * - threadCount: Cantidad de hilos activos del proceso (Threads en /proc/<pid>/status).
 * - smapsRollupPssKb: PSS real leído directamente desde /proc/<pid>/smaps_rollup (kernel 4.14+).
 * - smapsRollupPrivateDirtyKb: Memoria privada sucia desde smaps_rollup.
 * - hasSmapsRollup: Indica si /proc/<pid>/smaps_rollup pudo leerse para evitar llamadas Binder a IActivityManager.
 */
@Parcelize
data class ProcessKernelMem(
    val pid: Int,
    val vmRssKb: Long,
    val vmHwmKb: Long,
    val vmSwapKb: Long,
    val oomScoreAdj: Int,
    val procName: String,
    val procCpuJiffies: Long = 0L,
    val threadCount: Int = 0,
    val smapsRollupPssKb: Long = 0L,
    val smapsRollupPrivateDirtyKb: Long = 0L,
    val hasSmapsRollup: Boolean = false
) : Parcelable
