package com.memscope.taskmonitor.core.shizuku

import android.content.Context
import android.os.Build
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
 * - Pss, Rss, Private_Dirty y SwapPss desde /proc/<pid>/smaps_rollup (kernels 4.14+)
 * - utime + stime desde /proc/<pid>/stat para calcular el porcentaje real de CPU
 * - /proc/stat para el total global de CPU jiffies
 * - /proc/meminfo para el desglose físico del sistema
 *
 * Además gestiona permisos runtime en Android 11-15 y la hibernación suave
 * (AppOps RUN_ANY_IN_BACKGROUND + Standby Bucket) sin deshabilitar ni ocultar íconos del lanzador,
 * todo sin usar el método obsoleto Shizuku#newProcess.
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
            if (!statusFile.exists()) continue

            var name = ""
            var vmRss = 0L
            var vmHwm = 0L
            var vmSwap = 0L
            var threads = 0
            var readOk = false

            runCatching {
                statusFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        readOk = true
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
            }

            if (!readOk) continue

            // Lectura rápida de /proc/<pid>/smaps_rollup (Linux 4.14+) para obtener PSS real
            // sin saturar Binder con IActivityManager.getProcessMemoryInfo.
            val rollup = readSmapsRollup(pid)
            val effectiveRss = if (rollup.rssKb > 0L) rollup.rssKb else vmRss
            val effectiveSwap = if (rollup.swapKb > 0L) rollup.swapKb else vmSwap

            val oomAdj = runCatching {
                File("/proc/$pid/oom_score_adj").bufferedReader().use { it.readText() }
                    .trim().toIntOrNull() ?: 0
            }.getOrDefault(0)

            val cpuJiffies = readProcessCpuJiffies(pid)

            results.add(
                ProcessKernelMem(
                    pid = pid,
                    vmRssKb = effectiveRss,
                    vmHwmKb = maxOf(vmHwm, effectiveRss),
                    vmSwapKb = effectiveSwap,
                    oomScoreAdj = oomAdj,
                    procName = name,
                    procCpuJiffies = cpuJiffies,
                    threadCount = threads,
                    smapsRollupPssKb = rollup.pssKb,
                    smapsRollupPrivateDirtyKb = rollup.privateDirtyKb,
                    hasSmapsRollup = rollup.available
                )
            )
        }
        return results.toTypedArray()
    }

    private data class SmapsRollupStats(
        val available: Boolean = false,
        val pssKb: Long = 0L,
        val rssKb: Long = 0L,
        val privateDirtyKb: Long = 0L,
        val swapKb: Long = 0L
    )

    /**
     * Lee el resumen agregado del kernel en /proc/<pid>/smaps_rollup.
     * Si el kernel es < 4.14 o SELinux restringe la lectura en modo ADB, devuelve available = false
     * para que el repositorio aplique respaldo selectivo con IActivityManager.
     */
    private fun readSmapsRollup(pid: Int): SmapsRollupStats {
        val rollupFile = File("/proc/$pid/smaps_rollup")
        if (!rollupFile.exists()) {
            return SmapsRollupStats()
        }

        var pss = 0L
        var rss = 0L
        var privDirty = 0L
        var swapPss = 0L
        var swap = 0L
        var parsedAny = false

        return runCatching {
            rollupFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("Rss:") -> rss = parseKbValue(line)
                        line.startsWith("Pss:") -> {
                            pss = parseKbValue(line)
                            parsedAny = true
                        }
                        line.startsWith("Private_Dirty:") -> privDirty = parseKbValue(line)
                        line.startsWith("SwapPss:") -> swapPss = parseKbValue(line)
                        line.startsWith("Swap:") -> swap = parseKbValue(line)
                    }
                }
            }
            SmapsRollupStats(
                available = parsedAny && pss > 0L,
                pssKb = pss,
                rssKb = rss,
                privateDirtyKb = privDirty,
                swapKb = if (swapPss > 0L) swapPss else swap
            )
        }.getOrDefault(SmapsRollupStats())
    }

    /**
     * Lee el tiempo total de CPU acumulado (utime + stime) desde /proc/<pid>/stat.
     * Se busca el último ')' para evitar fallos si el nombre del proceso contiene espacios o paréntesis.
     */
    private fun readProcessCpuJiffies(pid: Int): Long {
        return runCatching {
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists()) return 0L
            val content = statFile.bufferedReader().use { it.readText() }
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
            if (!statFile.exists()) return 0L
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
     * Devuelve un arreglo de 8 elementos en KB:
     * [0] MemTotal, [1] MemAvailable, [2] Cached + SReclaimable, [3] Buffers,
     * [4] SwapTotal, [5] SwapFree, [6] Kernel (Slab + KernelStack + PageTables + VmallocUsed), [7] MemFree
     */
    override fun readSystemProcMemInfo(): LongArray {
        val out = LongArray(8)
        var parsedTotal = 0L
        var cached = 0L
        var sReclaimable = 0L
        var slab = 0L
        var kernelStack = 0L
        var pageTables = 0L
        var vmallocUsed = 0L

        fun parseLine(line: String) {
            when {
                line.startsWith("MemTotal:") -> {
                    parsedTotal = parseKbValue(line)
                    out[0] = parsedTotal
                }
                line.startsWith("MemFree:") -> out[7] = parseKbValue(line)
                line.startsWith("MemAvailable:") -> out[1] = parseKbValue(line)
                line.startsWith("Cached:") -> cached = parseKbValue(line)
                line.startsWith("SReclaimable:") -> sReclaimable = parseKbValue(line)
                line.startsWith("Buffers:") -> out[3] = parseKbValue(line)
                line.startsWith("SwapTotal:") -> out[4] = parseKbValue(line)
                line.startsWith("SwapFree:") -> out[5] = parseKbValue(line)
                line.startsWith("Slab:") -> slab = parseKbValue(line)
                line.startsWith("KernelStack:") -> kernelStack = parseKbValue(line)
                line.startsWith("PageTables:") -> pageTables = parseKbValue(line)
                line.startsWith("VmallocUsed:") -> vmallocUsed = parseKbValue(line)
            }
        }

        val directOk = runCatching {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.forEach(::parseLine)
            }
            parsedTotal > 0L
        }.getOrDefault(false)

        if (!directOk) {
            val cmd = runCommandWithOutput("cat", "/proc/meminfo")
            if (cmd.first) {
                cmd.second.lineSequence().forEach(::parseLine)
            }
        }

        out[2] = cached + sReclaimable
        out[6] = slab + kernelStack + pageTables + vmallocUsed

        // Si MemAvailable no vino o es 0 (kernels antiguos), aproximar con MemFree + Cached + Buffers
        if (out[1] == 0L && out[0] > 0L) {
            out[1] = (out[7] + out[2] + out[3]).coerceAtMost(out[0])
        }

        // Respaldo para ZRAM si SwapTotal no apareció en /proc/meminfo
        if (out[4] == 0L) {
            runCatching {
                val diskBytes = File("/sys/block/zram0/disksize").bufferedReader().use { it.readText() }
                    .trim().toLongOrNull() ?: 0L
                if (diskBytes > 0L) {
                    out[4] = diskBytes / 1024L
                    val mmStat = File("/sys/block/zram0/mm_stat").bufferedReader().use { it.readText() }
                        .trim().split(WHITESPACE_REGEX)
                    val origDataKb = (mmStat.firstOrNull()?.toLongOrNull() ?: 0L) / 1024L
                    out[5] = (out[4] - origDataKb).coerceAtLeast(0L)
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
        if (permissionName == "android.permission.PACKAGE_USAGE_STATS") {
            val mode = if (grant) "allow" else "ignore"
            return runCommandOk(
                "cmd", "appops", "set",
                "--user", userId.toString(),
                packageName,
                "GET_USAGE_STATS",
                mode
            )
        }
        val action = if (grant) "grant" else "revoke"
        return runCommandOk(
            "pm",
            action,
            "--user",
            userId.toString(),
            packageName,
            permissionName
        )
    }

    /**
     * Aplica o revierte Hibernación Suave SIN ocultar el ícono de la aplicación en el lanzador.
     * - Restringe ejecución en segundo plano vía AppOps (RUN_ANY_IN_BACKGROUND en API 28+ / RUN_IN_BACKGROUND en API 26-27).
     * - Ajusta el Standby Bucket a RESTRICTED (Android 12+) o RARE (Android 9-11), y lo devuelve a ACTIVE al despertar.
     */
    override fun setSoftHibernation(
        packageName: String?,
        userId: Int,
        hibernate: Boolean
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        val mode = if (hibernate) "ignore" else "allow"

        val primaryOpOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCommandOk(
                "cmd", "appops", "set",
                "--user", userId.toString(),
                packageName,
                "RUN_ANY_IN_BACKGROUND",
                mode
            )
        } else {
            false
        }

        val legacyOpOk = runCommandOk(
            "cmd", "appops", "set",
            "--user", userId.toString(),
            packageName,
            "RUN_IN_BACKGROUND",
            mode
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (hibernate) {
                val restrictedOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCommandOk(
                        "am", "set-standby-bucket",
                        "--user", userId.toString(),
                        packageName,
                        "restricted"
                    )
                } else {
                    false
                }
                if (!restrictedOk) {
                    runCommandOk(
                        "am", "set-standby-bucket",
                        "--user", userId.toString(),
                        packageName,
                        "rare"
                    )
                }
            } else {
                runCommandOk(
                    "am", "set-standby-bucket",
                    "--user", userId.toString(),
                    packageName,
                    "active"
                )
            }
        }

        return primaryOpOk || legacyOpOk
    }

    /**
     * Lista todos los paquetes actualmente restringidos en segundo plano (hibernación suave)
     * en una sola consulta rápida desde el UserService.
     */
    override fun getSoftHibernatedPackages(userId: Int): Array<String> {
        val opName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "RUN_ANY_IN_BACKGROUND"
        } else {
            "RUN_IN_BACKGROUND"
        }

        val queryResult = runCommandWithOutput(
            "cmd", "appops", "query-op",
            "--user", userId.toString(),
            opName,
            "ignore"
        )
        if (queryResult.first) {
            return queryResult.second
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("No ") && it.contains('.') }
                .distinct()
                .toList()
                .toTypedArray()
        }

        // Respaldo para Android 8-10 donde `cmd appops query-op` puede no estar disponible
        val dumpResult = runCommandWithOutput("dumpsys", "appops")
        if (!dumpResult.first) return emptyArray()

        val found = LinkedHashSet<String>()
        var currentPkg: String? = null
        dumpResult.second.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("Package ")) {
                currentPkg = line.removePrefix("Package ").substringBefore(':').trim()
            } else if (currentPkg != null &&
                (line.contains("RUN_ANY_IN_BACKGROUND") || line.contains("RUN_IN_BACKGROUND")) &&
                line.contains("ignore")
            ) {
                found.add(currentPkg!!)
            }
        }
        return found.toTypedArray()
    }

    private fun runCommandOk(vararg args: String): Boolean {
        return runCatching {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun runCommandWithOutput(vararg args: String): Pair<Boolean, String> {
        return runCatching {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(6, TimeUnit.SECONDS)
            (finished && process.exitValue() == 0) to output
        }.getOrDefault(false to "")
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
