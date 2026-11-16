package com.memscope.taskmonitor.feature.peaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memscope.taskmonitor.core.data.repository.TaskMonitorRepository
import com.memscope.taskmonitor.core.model.AppPeakRecord
import com.memscope.taskmonitor.core.model.AppVisibilityFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PeakSortMode(val labelEs: String) {
    PEAK_PSS("Pico PSS"),
    PEAK_HWM("Pico Kernel VmHWM"),
    AVERAGE_PSS("Promedio"),
    ACTIVE_TIME("Tiempo activa")
}

data class PeaksUiState(
    val searchQuery: String = "",
    val filter: AppVisibilityFilter = AppVisibilityFilter.USER_ONLY,
    val sortMode: PeakSortMode = PeakSortMode.PEAK_PSS,
    val records: List<AppPeakRecord> = emptyList(),
    val maxPeakOverallKb: Long = 1L
)

/**
 * ViewModel para la pantalla de Picos e Historial de RAM.
 * Procesa el ordenamiento y filtrado sobre Dispatchers.Default.
 */
class PeaksViewModel(
    private val repository: TaskMonitorRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(AppVisibilityFilter.USER_ONLY)
    private val sortMode = MutableStateFlow(PeakSortMode.PEAK_PSS)

    val uiState: StateFlow<PeaksUiState> = combine(
        repository.peakHistory,
        searchQuery,
        filter,
        sortMode
    ) { history, q, vis, sort ->
        val query = q.trim().lowercase()
        val filtered = history.filter { item ->
            val catOk = when (vis) {
                AppVisibilityFilter.USER_ONLY -> !item.isSystemApp
                AppVisibilityFilter.SYSTEM_ONLY -> item.isSystemApp
                AppVisibilityFilter.ALL -> true
            }
            val queryOk = query.isEmpty() ||
                item.appName.lowercase().contains(query) ||
                item.packageName.lowercase().contains(query)
            catOk && queryOk
        }

        val sorted = when (sort) {
            PeakSortMode.PEAK_PSS -> filtered.sortedByDescending { it.peakPssKb }
            PeakSortMode.PEAK_HWM -> filtered.sortedByDescending { it.peakRssHwmKb }
            PeakSortMode.AVERAGE_PSS -> filtered.sortedByDescending { it.averagePssKb }
            PeakSortMode.ACTIVE_TIME -> filtered.sortedByDescending { it.activeSecondsAccumulated }
        }

        val maxPeak = sorted.maxOfOrNull { maxOf(it.peakPssKb, it.peakRssHwmKb) }?.coerceAtLeast(1L) ?: 1L

        PeaksUiState(
            searchQuery = q,
            filter = vis,
            sortMode = sort,
            records = sorted,
            maxPeakOverallKb = maxPeak
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PeaksUiState()
        )

    fun onSearchChange(q: String) {
        searchQuery.value = q
    }

    fun onFilterChange(f: AppVisibilityFilter) {
        filter.value = f
    }

    fun onSortChange(mode: PeakSortMode) {
        sortMode.value = mode
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearPeakHistory()
        }
    }

    class Factory(
        private val repository: TaskMonitorRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PeaksViewModel(repository) as T
        }
    }
}
