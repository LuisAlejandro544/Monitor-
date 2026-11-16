package com.memscope.taskmonitor.core.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.memscope.taskmonitor.core.data.db.MemoryHistoryDao
import com.memscope.taskmonitor.core.data.db.MemoryHistoryEntity
import com.memscope.taskmonitor.core.data.db.MemorySampleEntity
import com.memscope.taskmonitor.core.data.prefs.MonitorPreferencesRepository
import com.memscope.taskmonitor.core.data.protection.CriticalAppProtector
import com.memscope.taskmonitor.core.model.AppMemoryInfo
import com.memscope.taskmonitor.core.model.AppPeakRecord
import com.memscope.taskmonitor.core.model.InstalledAppFreezeInfo
import com.memscope.taskmonitor.core.model.MemoryTimeSample
import com.memscope.taskmonitor.core.model.ProcessImportance
import com.memscope.taskmonitor.core.model.ShizukuStatus
import com.memscope.taskmonitor.core.model.SystemMemoryOverview
import com.memscope.taskmonitor.core.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio principal de MemScope.
 *
 * Orquesta con corrutinas en segundo plano (Dispatchers.Default / Dispatchers.IO):
 * 1. Lectura periódica de procesos activos, RAM (PSS/RSS/VmHWM), CPU (%) e Hilos.
 * 2. Seguimiento de picos de sesión y cálculo de promedios mientras las apps corren.
 * 3. Persistencia optimizada por lotes de picos históricos y muestras temporales en Room.
 * 4. Protección crítica anti-bloqueo (CriticalAppProtector) y gestión de paquetes vía Shizuku.
 */
class TaskMonitorRepository(
    private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val historyDao: MemoryHistoryDao,
    private val preferencesRepository: MonitorPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val packageManager: PackageManager = context.packageManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val criticalProtector = CriticalAppProtector(context)

    private val _runningApps = MutableStateFlow<List<AppMemoryInfo>>(emptyList())
    val runningApps: StateFlow<List<AppMemoryInfo>> = _runningApps.asStateFlow()

    private val _systemOverview = MutableStateFlow(
        SystemMemoryOverview(
            totalRamKb = 0L,
            availableRamKb = 0L,
            usedRamKb = 0L,
            cachedKb = 0L,
            buffersKb = 0L,
            swapTotalKb = 0L,
            swapFreeKb = 0L,
            isLowMemory = false,
            activeProcessCount = 0
        )
    )
    val systemOverview: StateFlow<SystemMemoryOverview> = _systemOverview.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppFreezeInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppFreezeInfo>> = _installedApps.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(true)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuManager.status

    val peakHistory: Flow<List<AppPeakRecord>> = historyDao.observeAllHistory().map { list ->
        list.map { entity ->
            AppPeakRecord(
                packageName = entity.packageName,
                appName = entity.appName,
                isSystemApp = entity.isSystemApp,
                peakPssKb = entity.peakPssKb,
                peakRssHwmKb = entity.peakRssHwmKb,
                averagePssKb = entity.averagePssKb,
                lastMeasuredPssKb = entity.lastMeasuredPssKb,
                sampleCount = entity.sampleCount,
                activeSecondsAccumulated = entity.activeSecondsAccumulated,
                peakTimestampMillis = entity.peakTimestampMillis,
                lastSeenMillis = entity.lastSeenMillis
            )
        }
    }

    // Cachés en memoria para gráficas rápidas, picos de sesión y deltas de CPU
    private val recentSparklines = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val sessionPeaks = ConcurrentHashMap<String, Long>()
    private val prevPidCpuJiffies = ConcurrentHashMap<Int, Long>()
    private var prevTotalCpuJiffies: Long = 0L
    private val systemPercentHistory = ArrayDeque<Float>(30)
    private val appLabelCache = ConcurrentHashMap<String, Pair<String, Boolean>>()

    private var pollingJob: Job? = null
    private var currentIntervalMs: Long = 2000L

    init {
        startMonitoringLoop()
    }

    fun observePackageSamples(packageName: String): Flow<List<MemoryTimeSample>> {
        return historyDao.observeSamplesForPackage(packageName, limit = 60).map { entities ->
            entities.reversed().map {
                MemoryTimeSample(
                    packageName = it.packageName,
                    pssKb = it.pssKb,
                    rssKb = it.rssKb,
                    hwmKb = it.hwmKb,
                    timestampMillis = it.timestampMillis
                )
            }
        }
    }

    fun setMonitoringPaused(paused: Boolean) {
        _isMonitoringActive.value = !paused
    }

    private fun startMonitoringLoop() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                currentIntervalMs = prefs.pollingIntervalMs
                while (isActive) {
                    if (_isMonitoringActive.value) {
                        sampleOnce(intervalSeconds = (currentIntervalMs / 1000L).coerceAtLeast(1L))
                    }
                    delay(currentIntervalMs)
                }
            }
        }
    }

    /**
     * Ejecuta un ciclo completo de lectura de memoria, CPU e hilos del sistema y de procesos activos.
     */
    suspend fun sampleOnce(intervalSeconds: Long = 2L) = withContext(Dispatchers.IO) {
        val status = shizukuManager.status.value
        val procMemInfo = shizukuManager.readSystemProcMemInfo()
        val amMemInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(amMemInfo)

        val totalKb = procMemInfo?.getOrNull(0)?.takeIf { it > 0 } ?: (amMemInfo.totalMem / 1024L)
        val availKb = procMemInfo?.getOrNull(1)?.takeIf { it > 0 } ?: (amMemInfo.availMem / 1024L)
        val cachedKb = procMemInfo?.getOrNull(2) ?: 0L
        val buffersKb = procMemInfo?.getOrNull(3) ?: 0L
        val swapTotalKb = procMemInfo?.getOrNull(4) ?: 0L
        val swapFreeKb = procMemInfo?.getOrNull(5) ?: 0L
        val usedKb = (totalKb - availKb).coerceAtLeast(0L)

        val usedPercent = if (totalKb > 0) (usedKb.toFloat() / totalKb.toFloat()).coerceIn(0f, 1f) else 0f
        if (systemPercentHistory.size >= 30) systemPercentHistory.removeFirst()
        systemPercentHistory.addLast(usedPercent)

        if (!status.isFullyOperational) {
            _systemOverview.value = SystemMemoryOverview(
                totalRamKb = totalKb,
                availableRamKb = availKb,
                usedRamKb = usedKb,
                cachedKb = cachedKb,
                buffersKb = buffersKb,
                swapTotalKb = swapTotalKb,
                swapFreeKb = swapFreeKb,
                isLowMemory = amMemInfo.lowMemory,
                activeProcessCount = 0,
                historyUsedPercent = systemPercentHistory.toList()
            )
            return@withContext
        }

        val processes = shizukuManager.getRunningAppProcesses().getOrNull().orEmpty()
        if (processes.isEmpty()) {
            _systemOverview.value = SystemMemoryOverview(
                totalRamKb = totalKb,
                availableRamKb = availKb,
                usedRamKb = usedKb,
                cachedKb = cachedKb,
                buffersKb = buffersKb,
                swapTotalKb = swapTotalKb,
                swapFreeKb = swapFreeKb,
                isLowMemory = amMemInfo.lowMemory,
                activeProcessCount = 0,
                historyUsedPercent = systemPercentHistory.toList()
            )
            return@withContext
        }

        val allPids = processes.map { it.pid }.distinct().toIntArray()

        // Lectura en lotes de 40 PIDs para evitar saturar transacciones Binder
        val memoryInfoByPid = HashMap<Int, android.os.Debug.MemoryInfo>(allPids.size)
        allPids.toList().chunked(40).forEach { chunk ->
            val chunkArray = chunk.toIntArray()
            val memInfos = shizukuManager.getProcessMemoryInfo(chunkArray).getOrNull()
            if (memInfos != null) {
                for (i in chunkArray.indices) {
                    if (i < memInfos.size) {
                        memoryInfoByPid[chunkArray[i]] = memInfos[i]
                    }
                }
            }
        }

        // Lectura del kernel vía UserService: VmRSS, VmHWM, VmSwap, Threads y CPU jiffies
        val currTotalCpuJiffies = shizukuManager.readTotalCpuJiffies()
        val totalCpuDelta = if (prevTotalCpuJiffies > 0L && currTotalCpuJiffies > prevTotalCpuJiffies) {
            currTotalCpuJiffies - prevTotalCpuJiffies
        } else {
            0L
        }
        if (currTotalCpuJiffies > 0L) {
            prevTotalCpuJiffies = currTotalCpuJiffies
        }

        val kernelMemByPid = shizukuManager.getKernelProcessMemory(allPids)
        val activePidSet = HashSet<Int>(allPids.size)
        allPids.forEach { activePidSet.add(it) }
        prevPidCpuJiffies.keys.retainAll(activePidSet)

        // Agrupar procesos por paquete principal
        val groupedByPkg = LinkedHashMap<String, MutableList<ActivityManager.RunningAppProcessInfo>>()
        for (proc in processes) {
            val primaryPkg = proc.pkgList?.firstOrNull()
                ?: proc.processName.substringBefore(':')
            if (primaryPkg.isBlank()) continue
            groupedByPkg.getOrPut(primaryPkg) { mutableListOf() }.add(proc)
        }

        // Una única consulta previa a Room para evitar lecturas N+1 en el bucle
        val existingHistoryMap = historyDao.getAllHistoryOnce().associateBy { it.packageName }

        val now = System.currentTimeMillis()
        val updatedApps = ArrayList<AppMemoryInfo>(groupedByPkg.size)
        val entitiesToUpsert = ArrayList<MemoryHistoryEntity>(groupedByPkg.size)
        val samplesToInsert = ArrayList<MemorySampleEntity>(groupedByPkg.size)

        for ((pkg, procList) in groupedByPkg) {
            val (appName, isSystem) = resolveAppInfo(pkg)
            val protection = criticalProtector.evaluate(pkg)
            var totalPssKb = 0L
            var totalRssKb = 0L
            var totalHwmKb = 0L
            var totalPrivateDirtyKb = 0L
            var totalNativePssKb = 0L
            var totalDalvikPssKb = 0L
            var totalSwapKb = 0L
            var totalThreads = 0
            var pkgCpuDelta = 0L
            var bestImportance = ProcessImportance.UNKNOWN
            var bestImportanceRank = Int.MAX_VALUE
            var lowestOomAdj = 1000
            val pids = ArrayList<Int>(procList.size)

            for (proc in procList) {
                pids.add(proc.pid)
                val dbg = memoryInfoByPid[proc.pid]
                val kernel = kernelMemByPid[proc.pid]

                val pss = (dbg?.totalPss ?: 0).toLong()
                val privDirty = (dbg?.totalPrivateDirty ?: 0).toLong()
                val nativePss = (dbg?.nativePss ?: 0).toLong()
                val dalvikPss = (dbg?.dalvikPss ?: 0).toLong()
                val swappableOrSwap = kernel?.vmSwapKb ?: (dbg?.totalSwappablePss ?: 0).toLong()
                val rss = kernel?.vmRssKb?.takeIf { it > 0 } ?: pss
                val hwm = kernel?.vmHwmKb?.takeIf { it > 0 } ?: rss

                totalPssKb += pss
                totalRssKb += rss
                totalHwmKb += hwm
                totalPrivateDirtyKb += privDirty
                totalNativePssKb += nativePss
                totalDalvikPssKb += dalvikPss
                totalSwapKb += swappableOrSwap
                totalThreads += kernel?.threadCount ?: 0

                if (kernel != null && kernel.procCpuJiffies > 0L) {
                    val prevProc = prevPidCpuJiffies[proc.pid] ?: 0L
                    if (prevProc > 0L && kernel.procCpuJiffies >= prevProc) {
                        pkgCpuDelta += (kernel.procCpuJiffies - prevProc)
                    }
                    prevPidCpuJiffies[proc.pid] = kernel.procCpuJiffies
                }

                if (kernel != null && kernel.oomScoreAdj < lowestOomAdj) {
                    lowestOomAdj = kernel.oomScoreAdj
                }

                if (proc.importance < bestImportanceRank) {
                    bestImportanceRank = proc.importance
                    bestImportance = mapImportance(proc.importance)
                }
            }

            if (lowestOomAdj == 1000 && procList.isNotEmpty()) {
                lowestOomAdj = 0
            }

            val cpuPercent = if (totalCpuDelta > 0L) {
                ((pkgCpuDelta.toFloat() / totalCpuDelta.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }

            val sparkline = recentSparklines.getOrPut(pkg) { ArrayDeque(24) }
            if (sparkline.size >= 24) sparkline.removeFirst()
            sparkline.addLast(totalPssKb)

            val prevSessionPeak = sessionPeaks[pkg] ?: 0L
            val newSessionPeak = maxOf(prevSessionPeak, totalPssKb)
            sessionPeaks[pkg] = newSessionPeak

            val isFrozen = isPackageDisabledByUser(pkg)

            updatedApps.add(
                AppMemoryInfo(
                    packageName = pkg,
                    appName = appName,
                    pids = pids,
                    currentPssKb = totalPssKb,
                    currentRssKb = totalRssKb,
                    kernelPeakHwmKb = totalHwmKb,
                    sessionPeakPssKb = newSessionPeak,
                    privateDirtyKb = totalPrivateDirtyKb,
                    nativePssKb = totalNativePssKb,
                    dalvikPssKb = totalDalvikPssKb,
                    swapKb = totalSwapKb,
                    importance = bestImportance,
                    oomScoreAdj = lowestOomAdj,
                    isSystemApp = isSystem,
                    isFrozen = isFrozen,
                    cpuUsagePercent = cpuPercent,
                    totalThreads = totalThreads,
                    isCriticalProtected = protection.isProtected,
                    protectionReason = protection.reason,
                    recentSamplesKb = sparkline.toList(),
                    updatedAtMillis = now
                )
            )

            val existing = existingHistoryMap[pkg]
            val prevSamples = existing?.sampleCount ?: 0L
            val prevAvg = existing?.averagePssKb ?: totalPssKb
            val newSampleCount = prevSamples + 1L
            val newAvg = ((prevAvg * prevSamples) + totalPssKb) / newSampleCount
            val prevPeakPss = existing?.peakPssKb ?: 0L
            val newPeakPss = maxOf(prevPeakPss, totalPssKb)
            val peakTs = if (totalPssKb >= prevPeakPss) now else (existing?.peakTimestampMillis ?: now)
            val newPeakHwm = maxOf(existing?.peakRssHwmKb ?: 0L, totalHwmKb, totalRssKb)

            entitiesToUpsert.add(
                MemoryHistoryEntity(
                    packageName = pkg,
                    appName = appName,
                    isSystemApp = isSystem,
                    peakPssKb = newPeakPss,
                    peakRssHwmKb = newPeakHwm,
                    averagePssKb = newAvg,
                    lastMeasuredPssKb = totalPssKb,
                    sampleCount = newSampleCount,
                    activeSecondsAccumulated = (existing?.activeSecondsAccumulated ?: 0L) + intervalSeconds,
                    peakTimestampMillis = peakTs,
                    lastSeenMillis = now
                )
            )

            samplesToInsert.add(
                MemorySampleEntity(
                    packageName = pkg,
                    pssKb = totalPssKb,
                    rssKb = totalRssKb,
                    hwmKb = totalHwmKb,
                    timestampMillis = now
                )
            )
        }

        historyDao.upsertHistory(entitiesToUpsert)
        historyDao.insertSamples(samplesToInsert)
        historyDao.pruneSamplesOlderThan(now - 6 * 60 * 60 * 1000L)

        _runningApps.value = updatedApps.sortedByDescending { it.currentPssKb }
        _systemOverview.value = SystemMemoryOverview(
            totalRamKb = totalKb,
            availableRamKb = availKb,
            usedRamKb = usedKb,
            cachedKb = cachedKb,
            buffersKb = buffersKb,
            swapTotalKb = swapTotalKb,
            swapFreeKb = swapFreeKb,
            isLowMemory = amMemInfo.lowMemory,
            activeProcessCount = processes.size,
            historyUsedPercent = systemPercentHistory.toList()
        )
    }

    /**
     * Carga la lista completa de aplicaciones instaladas (activas, congeladas y protegidas).
     */
    suspend fun refreshInstalledApps() = withContext(Dispatchers.IO) {
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

        val runningByPkg = _runningApps.value.associateBy { it.packageName }

        val mapped = packages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            if (pkgInfo.packageName == context.packageName) return@mapNotNull null

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = runCatching { appInfo.loadLabel(packageManager).toString() }
                .getOrDefault(pkgInfo.packageName)
            val enabledState = runCatching {
                packageManager.getApplicationEnabledSetting(pkgInfo.packageName)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            val isFrozen = !appInfo.enabled ||
                enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            val running = runningByPkg[pkgInfo.packageName]
            val protection = criticalProtector.evaluate(pkgInfo.packageName)

            InstalledAppFreezeInfo(
                packageName = pkgInfo.packageName,
                appName = label,
                versionName = pkgInfo.versionName ?: "—",
                isSystemApp = isSystem,
                isFrozen = isFrozen,
                enabledSetting = enabledState,
                isRunningNow = running != null,
                currentRamKb = running?.currentPssKb ?: 0L,
                isCriticalProtected = protection.isProtected,
                protectionReason = protection.reason
            )
        }.sortedWith(compareByDescending<InstalledAppFreezeInfo> { it.isFrozen }.thenBy { it.appName.lowercase() })

        _installedApps.value = mapped
    }

    /**
     * Congela o descongela un paquete verificando la lista blanca crítica para evitar bloqueos.
     */
    suspend fun freezeOrUnfreezeApp(packageName: String, freeze: Boolean): Result<Unit> {
        if (freeze) {
            val protection = criticalProtector.evaluate(packageName)
            if (protection.isProtected) {
                return Result.failure(
                    IllegalStateException("Protegida contra bloqueo: ${protection.reason}")
                )
            }
        }

        val result = shizukuManager.setApplicationFrozen(packageName, freeze)
        if (result.isSuccess) {
            if (freeze) {
                shizukuManager.forceStopPackage(packageName)
            }
            refreshInstalledApps()
            sampleOnce()
        }
        return result
    }

    suspend fun forceStopApp(packageName: String): Result<Unit> {
        val result = shizukuManager.forceStopPackage(packageName)
        if (result.isSuccess) {
            sampleOnce()
        }
        return result
    }

    suspend fun grantRuntimePermission(packageName: String, permissionName: String): Result<Unit> {
        return shizukuManager.grantRuntimePermission(packageName, permissionName)
    }

    suspend fun revokeRuntimePermission(packageName: String, permissionName: String): Result<Unit> {
        return shizukuManager.revokeRuntimePermission(packageName, permissionName)
    }

    suspend fun getDeclaredPermissions(packageName: String): List<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                }
                val perms = info.requestedPermissions ?: return@runCatching emptyList()
                val flags = info.requestedPermissionsFlags ?: IntArray(perms.size)
                perms.indices.map { idx ->
                    val granted = (flags.getOrNull(idx) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                    perms[idx] to granted
                }
            }.getOrDefault(emptyList())
        }

    suspend fun clearPeakHistory() = withContext(Dispatchers.IO) {
        historyDao.clearHistory()
        historyDao.clearSamples()
        sessionPeaks.clear()
    }

    private fun resolveAppInfo(packageName: String): Pair<String, Boolean> {
        appLabelCache[packageName]?.let { return it }
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            val label = appInfo.loadLabel(packageManager).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            (label to isSystem).also { appLabelCache[packageName] = it }
        }.getOrDefault(packageName to false)
    }

    private fun isPackageDisabledByUser(packageName: String): Boolean {
        return runCatching {
            val state = packageManager.getApplicationEnabledSetting(packageName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    private fun mapImportance(importance: Int): ProcessImportance {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> ProcessImportance.FOREGROUND
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> ProcessImportance.VISIBLE
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> ProcessImportance.FOREGROUND_SERVICE
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> ProcessImportance.PERCEPTIBLE
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> ProcessImportance.CACHED
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> ProcessImportance.SERVICE
            else -> ProcessImportance.UNKNOWN
        }
    }
}
