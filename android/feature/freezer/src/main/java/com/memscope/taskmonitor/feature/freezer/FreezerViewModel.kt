package com.memscope.taskmonitor.feature.freezer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memscope.taskmonitor.core.data.repository.TaskMonitorRepository
import com.memscope.taskmonitor.core.model.InstalledAppFreezeInfo
import com.memscope.taskmonitor.core.model.ShizukuStatus
import com.memscope.taskmonitor.core.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FreezerFilter(val labelEs: String) {
    ALL_USER("Apps Usuario"),
    SOFT_HIBERNATED("Hibernadas"),
    FROZEN_ONLY("Congeladas"),
    ACTIVE_ONLY("Activas"),
    SYSTEM_APPS("Sistema")
}

data class FreezerUiState(
    val searchQuery: String = "",
    val filter: FreezerFilter = FreezerFilter.ALL_USER,
    val apps: List<InstalledAppFreezeInfo> = emptyList(),
    val frozenCount: Int = 0,
    val softHibernatedCount: Int = 0,
    val shizukuStatus: ShizukuStatus = ShizukuStatus(),
    val feedbackMessage: String? = null,
    val isLoading: Boolean = false
)

/**
 * ViewModel del módulo Congelador e Hibernación Suave.
 * Carga los paquetes instalados en Dispatchers.IO y aplica filtros/búsqueda en Dispatchers.Default.
 */
class FreezerViewModel(
    private val repository: TaskMonitorRepository,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(FreezerFilter.ALL_USER)
    private val feedbackMessage = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)

    init {
        refreshApps()
    }

    val uiState: StateFlow<FreezerUiState> = combine(
        repository.installedApps,
        repository.shizukuStatus,
        searchQuery,
        filter,
        feedbackMessage,
        isLoading
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allApps = values[0] as List<InstalledAppFreezeInfo>
        val shizuku = values[1] as ShizukuStatus
        val query = (values[2] as String).trim().lowercase()
        val activeFilter = values[3] as FreezerFilter
        val fb = values[4] as String?
        val loading = values[5] as Boolean

        val filtered = allApps.filter { app ->
            val matchesCat = when (activeFilter) {
                FreezerFilter.ALL_USER -> !app.isSystemApp
                FreezerFilter.SOFT_HIBERNATED -> app.isSoftHibernated
                FreezerFilter.FROZEN_ONLY -> app.isFrozen
                FreezerFilter.ACTIVE_ONLY -> !app.isFrozen && !app.isSoftHibernated && !app.isSystemApp
                FreezerFilter.SYSTEM_APPS -> app.isSystemApp
            }
            val matchesQuery = query.isEmpty() ||
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            matchesCat && matchesQuery
        }

        FreezerUiState(
            searchQuery = values[2] as String,
            filter = activeFilter,
            apps = filtered,
            frozenCount = allApps.count { it.isFrozen },
            softHibernatedCount = allApps.count { it.isSoftHibernated },
            shizukuStatus = shizuku,
            feedbackMessage = fb,
            isLoading = loading
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = FreezerUiState()
        )

    fun onSearchChange(q: String) {
        searchQuery.value = q
    }

    fun onFilterChange(f: FreezerFilter) {
        filter.value = f
    }

    fun refreshApps() {
        viewModelScope.launch {
            isLoading.value = true
            repository.refreshInstalledApps()
            isLoading.value = false
        }
    }

    fun toggleFreeze(app: InstalledAppFreezeInfo) {
        viewModelScope.launch {
            val targetFreeze = !app.isFrozen
            val res = repository.freezeOrUnfreezeApp(app.packageName, targetFreeze)
            feedbackMessage.value = if (res.isSuccess) {
                if (targetFreeze) {
                    "${app.appName} ha sido congelada."
                } else {
                    "${app.appName} ha sido descongelada."
                }
            } else {
                "Error en ${app.appName}: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun toggleSoftHibernation(app: InstalledAppFreezeInfo) {
        viewModelScope.launch {
            val targetHibernate = !app.isSoftHibernated
            val res = repository.softHibernateApp(app.packageName, targetHibernate)
            feedbackMessage.value = if (res.isSuccess) {
                if (targetHibernate) {
                    "${app.appName} en hibernación suave (ícono visible en lanzador)."
                } else {
                    "${app.appName} despertada (segundo plano restaurado)."
                }
            } else {
                "Error en ${app.appName}: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    class Factory(
        private val repository: TaskMonitorRepository,
        private val shizukuManager: ShizukuManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FreezerViewModel(repository, shizukuManager) as T
        }
    }
}
