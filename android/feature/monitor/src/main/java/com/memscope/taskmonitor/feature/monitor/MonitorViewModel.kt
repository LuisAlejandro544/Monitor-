package com.memscope.taskmonitor.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memscope.taskmonitor.core.data.prefs.MonitorPreferencesRepository
import com.memscope.taskmonitor.core.data.repository.TaskMonitorRepository
import com.memscope.taskmonitor.core.data.service.BackgroundMonitorController
import com.memscope.taskmonitor.core.model.AppMemoryInfo
import com.memscope.taskmonitor.core.model.AppPeakRecord
import com.memscope.taskmonitor.core.model.AppVisibilityFilter
import com.memscope.taskmonitor.core.model.MemoryTimeSample
import com.memscope.taskmonitor.core.model.ProcessSortOrder
import com.memscope.taskmonitor.core.model.ShizukuStatus
import com.memscope.taskmonitor.core.model.SystemMemoryOverview
import com.memscope.taskmonitor.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de UI para la pantalla principal de Monitor de Tareas en vivo.
 */
data class MonitorUiState(
    val searchQuery: String = "",
    val sortOrder: ProcessSortOrder = ProcessSortOrder.CURRENT_RAM_DESC,
    val visibilityFilter: AppVisibilityFilter = AppVisibilityFilter.USER_ONLY,
    val isPaused: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = false,
    val actionFeedback: String? = null,
    val apps: List<AppMemoryInfo> = emptyList(),
    val systemOverview: SystemMemoryOverview = SystemMemoryOverview(0, 0, 0, 0, 0, 0, 0, false, 0),
    val shizukuStatus: ShizukuStatus = ShizukuStatus()
)

/**
 * ViewModel para MonitorScreen y AppDetailScreen.
 * Filtra y ordena procesos en Dispatchers.Default mediante combine() reactivo,
 * además de gestionar el servicio en segundo plano para capturar picos reales al usar otras apps.
 */
class MonitorViewModel(
    private val repository: TaskMonitorRepository,
    private val shizukuManager: ShizukuManager,
    private val preferencesRepository: MonitorPreferencesRepository,
    private val backgroundController: BackgroundMonitorController
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(ProcessSortOrder.CURRENT_RAM_DESC)
    private val visibilityFilter = MutableStateFlow(AppVisibilityFilter.USER_ONLY)
    private val actionFeedback = MutableStateFlow<String?>(null)

    private data class FilterCriteria(
        val query: String,
        val sort: ProcessSortOrder,
        val filter: AppVisibilityFilter,
        val feedback: String?,
        val backgroundMonitoring: Boolean
    )

    private val criteriaFlow = combine(
        searchQuery,
        sortOrder,
        visibilityFilter,
        actionFeedback,
        preferencesRepository.preferencesFlow
    ) { q, s, f, fb, prefs ->
        FilterCriteria(q, s, f, fb, prefs.backgroundMonitoringEnabled)
    }

    val uiState: StateFlow<MonitorUiState> = combine(
        repository.runningApps,
        repository.systemOverview,
        repository.shizukuStatus,
        repository.isMonitoringActive,
        criteriaFlow
    ) { apps, overview, shizuku, isActive, criteria ->
        val q = criteria.query.trim().lowercase()
        val filtered = apps.filter { app ->
            val matchesCategory = when (criteria.filter) {
                AppVisibilityFilter.USER_ONLY -> !app.isSystemApp
                AppVisibilityFilter.SYSTEM_ONLY -> app.isSystemApp
                AppVisibilityFilter.ALL -> true
            }
            val matchesQuery = q.isEmpty() ||
                app.appName.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }

        val sorted = when (criteria.sort) {
            ProcessSortOrder.CURRENT_RAM_DESC -> filtered.sortedByDescending { it.currentPssKb }
            ProcessSortOrder.PEAK_RAM_DESC -> filtered.sortedByDescending { it.effectivePeakKb }
            ProcessSortOrder.CPU_DESC -> filtered.sortedByDescending { it.cpuUsagePercent }
            ProcessSortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            ProcessSortOrder.IMPORTANCE -> filtered.sortedBy { it.importance.ordinal }
        }

        MonitorUiState(
            searchQuery = criteria.query,
            sortOrder = criteria.sort,
            visibilityFilter = criteria.filter,
            isPaused = !isActive,
            backgroundMonitoringEnabled = criteria.backgroundMonitoring,
            actionFeedback = criteria.feedback,
            apps = sorted,
            systemOverview = overview,
            shizukuStatus = shizuku
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MonitorUiState()
        )

    val peakHistoryByPackage: StateFlow<Map<String, AppPeakRecord>> = combine(
        repository.peakHistory
    ) { arr ->
        arr[0].associateBy { it.packageName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOrderChange(order: ProcessSortOrder) {
        sortOrder.value = order
    }

    fun onVisibilityFilterChange(filter: AppVisibilityFilter) {
        visibilityFilter.value = filter
    }

    fun togglePause() {
        val currentlyActive = repository.isMonitoringActive.value
        repository.setMonitoringPaused(currentlyActive)
    }

    fun setBackgroundMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundMonitoringEnabled(enabled)
            if (enabled) {
                backgroundController.startBackgroundMonitoring()
                actionFeedback.value = "Monitoreo en segundo plano activado (notificación en vivo)."
            } else {
                backgroundController.stopBackgroundMonitoring()
                actionFeedback.value = "Monitoreo en segundo plano detenido."
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            repository.sampleOnce()
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun forceStopApp(packageName: String, appName: String) {
        viewModelScope.launch {
            val res = repository.forceStopApp(packageName)
            actionFeedback.value = if (res.isSuccess) {
                "Proceso detenido: $appName"
            } else {
                "Error al detener $appName: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun freezeOrUnfreezeApp(packageName: String, appName: String, freeze: Boolean) {
        viewModelScope.launch {
            val res = repository.freezeOrUnfreezeApp(packageName, freeze)
            actionFeedback.value = if (res.isSuccess) {
                if (freeze) "$appName congelada y detenida." else "$appName descongelada."
            } else {
                res.exceptionOrNull()?.message ?: "Error al cambiar estado de $appName"
            }
        }
    }

    fun softHibernateApp(packageName: String, appName: String, hibernate: Boolean) {
        viewModelScope.launch {
            val res = repository.softHibernateApp(packageName, hibernate)
            actionFeedback.value = if (res.isSuccess) {
                if (hibernate) {
                    "$appName en hibernación suave (ícono conservado)."
                } else {
                    "$appName despertada de hibernación suave."
                }
            } else {
                res.exceptionOrNull()?.message ?: "Error en hibernación suave de $appName"
            }
        }
    }

    fun clearFeedback() {
        actionFeedback.update { null }
    }

    fun observePackageSamples(packageName: String): Flow<List<MemoryTimeSample>> {
        return repository.observePackageSamples(packageName)
    }

    fun refreshPackageDetail(packageName: String) {
        viewModelScope.launch {
            repository.refreshPackageMemoryBreakdown(packageName)
        }
    }

    suspend fun loadPackagePermissions(packageName: String): List<Pair<String, Boolean>> {
        return repository.getDeclaredPermissions(packageName)
    }

    fun grantPermission(packageName: String, permission: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.grantRuntimePermission(packageName, permission)
            onDone(res.isSuccess, res.exceptionOrNull()?.message)
        }
    }

    fun revokePermission(packageName: String, permission: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.revokeRuntimePermission(packageName, permission)
            onDone(res.isSuccess, res.exceptionOrNull()?.message)
        }
    }

    class Factory(
        private val repository: TaskMonitorRepository,
        private val shizukuManager: ShizukuManager,
        private val preferencesRepository: MonitorPreferencesRepository,
        private val backgroundController: BackgroundMonitorController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MonitorViewModel(
                repository = repository,
                shizukuManager = shizukuManager,
                preferencesRepository = preferencesRepository,
                backgroundController = backgroundController
            ) as T
        }
    }
}
