package com.memscope.taskmonitor.core.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Implementación del Shizuku UserService (ejecutado en un proceso aislado con identidad
 * UID 0 Root o UID 2000 ADB Shell).
 *
 * Lee directamente el sistema de archivos /proc del kernel Linux para obtener:
 * - VmRSS, VmHWM (pico del kernel), VmSwap y Threads desde /proc/<pid>/status
 * - utime + stime desde /proc/<pid>/stat para calcular el porcentaje real de CPU
 * - /proc/stat para el total global de CPU jiffies
 * - /proc/meminfo para el desglose físico del sistema
 * También provee un respaldo compatible Android 11-15 para permisos runtime sin usar
 * el método obsoleto Shizuku#newProcess (reemplazado por UserService según la doc oficial).
 */
@Keep
class TaskMonitorUserService : ITaskMonitorService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    constructor(context: Context) : super() {
        Log.d(TAG, "TaskMonitorUserService inicializado con Context de Shizuku v13+")
    }

    /**
     * Método obligatorio de limpieza de Shizuku UserService (transacción 16777114 en AIDL).
     */
    override fun destroy() {
        Log.d(TAG, "Destruyendo TaskMonitorUserService y finalizando proceso.")
        exitProcess(0)
    }

    override fun getKernelProcessMemory(pids: IntArray?): Array<ProcessKernelMem> {
        if (pids == null || pids.isEmpty()) return emptyArray()

        val results = ArrayList<ProcessKernelMem>(pids.size)
        for (pid in pids) {
            if (pid <= 0) continue
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists() || !statusFile.canRead()) continue

            var name = ""
            var vmRss = 0L
            var vmHwm = 0L
            var vmSwap = 0L
            var threads = 0

            runCatching {
                statusFile.forEachLine { line ->
                    when {
                        line.startsWith("Name:") -> name = line.substringAfter(':').trim()
                        line.startsWith("VmRSS:") -> vmRss = parseKbValue(line)
                        line.startsWith("VmHWM:") -> vmHwm = parseKbValue(line)
                        line.startsWith("VmSwap:") -> vmSwap = parseKbValue(line)
                        line.startsWith("Threads:") -> {
                            threads = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }
                }
            }

            val oomAdj = runCatching {
                val adjFile = File("/proc/$pid/oom_score_adj")
                if (adjFile.exists() && adjFile.canRead()) {
                    adjFile.readText().trim().toIntOrNull() ?: 0
                } else {
                    0
                }
            }.getOrDefault(0)

            val cpuJiffies = readProcessCpuJiffies(pid)

            results.add(
                ProcessKernelMem(
                    pid = pid,
                    vmRssKb = vmRss,
                    vmHwmKb = vmHwm,
                    vmSwapKb = vmSwap,
                    oomScoreAdj = oomAdj,
                    procName = name,
                    procCpuJiffies = cpuJiffies,
                    threadCount = threads
                )
            )
        }
        return results.toTypedArray()
    }

    /**
     * Lee el tiempo total de CPU acumulado (utime + stime) desde /proc/<pid>/stat.
     * Se busca el último ')' para evitar fallos si el nombre del proceso contiene espacios o paréntesis.
     */
    private fun readProcessCpuJiffies(pid: Int): Long {
        return runCatching {
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists() || !statFile.canRead()) return 0L
            val content = statFile.readText()
            val closeParenIdx = content.lastIndexOf(')')
            if (closeParenIdx == -1 || closeParenIdx + 2 >= content.length) return 0L

            // Después de ") " el índice 0 es 'state' (campo 3).
            // Campo 14 (utime) = índice 11, Campo 15 (stime) = índice 12.
            val fields = content.substring(closeParenIdx + 2).trim().split(WHITESPACE_REGEX)
            if (fields.size <= 12) return 0L
            val utime = fields[11].toLongOrNull() ?: 0L
            val stime = fields[12].toLongOrNull() ?: 0L
            utime + stime
        }.getOrDefault(0L)
    }

    /**
     * Lee la línea agregada 'cpu ' de /proc/stat y suma todos los jiffies de CPU del sistema.
     */
    override fun readTotalCpuJiffies(): Long {
        return runCatching {
            val statFile = File("/proc/stat")
            if (!statFile.exists() || !statFile.canRead()) return 0L
            val firstLine = statFile.bufferedReader().use { it.readLine() } ?: return 0L
            if (!firstLine.startsWith("cpu ")) return 0L
            val parts = firstLine.trim().split(WHITESPACE_REGEX)
            var sum = 0L
            for (i in 1 until parts.size) {
                sum += parts[i].toLongOrNull() ?: 0L
            }
            sum
        }.getOrDefault(0L)
    }

    /**
     * Devuelve un arreglo de 6 elementos en KB:
     * [0] MemTotal, [1] MemAvailable, [2] Cached, [3] Buffers, [4] SwapTotal, [5] SwapFree
     */
    override fun readSystemProcMemInfo(): LongArray {
        val out = LongArray(6)
        val memInfoFile = File("/proc/meminfo")
        if (!memInfoFile.exists() || !memInfoFile.canRead()) return out

        runCatching {
            memInfoFile.forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> out[0] = parseKbValue(line)
                    line.startsWith("MemAvailable:") -> out[1] = parseKbValue(line)
                    line.startsWith("Cached:") -> out[2] = parseKbValue(line)
                    line.startsWith("Buffers:") -> out[3] = parseKbValue(line)
                    line.startsWith("SwapTotal:") -> out[4] = parseKbValue(line)
                    line.startsWith("SwapFree:") -> out[5] = parseKbValue(line)
                }
            }
        }
        return out
    }

    /**
     * Respaldo seguro dentro del proceso UserService (UID 0 / 2000) para otorgar o revocar
     * permisos en Android 14/15 QPRs donde la firma AIDL de IPermissionManager haya cambiado.
     */
    override fun setRuntimePermissionViaUserService(
        packageName: String?,
        permissionName: String?,
        userId: Int,
        grant: Boolean
    ): Boolean {
        if (packageName.isNullOrBlank() || permissionName.isNullOrBlank()) return false
        return runCatching {
            val action = if (grant) "grant" else "revoke"
            val process = ProcessBuilder(
                "pm",
                action,
                "--user",
                userId.toString(),
                packageName,
                permissionName
            )
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun parseKbValue(line: String): Long {
        val raw = line.substringAfter(':').trim()
        val numberPart = raw.substringBefore(' ').trim()
        return numberPart.toLongOrNull() ?: 0L
    }

    companion object {
        private const val TAG = "TaskMonitorUserService"
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
