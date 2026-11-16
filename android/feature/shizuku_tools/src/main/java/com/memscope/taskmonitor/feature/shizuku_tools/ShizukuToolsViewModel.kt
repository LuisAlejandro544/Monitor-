package com.memscope.taskmonitor.feature.shizuku_tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memscope.taskmonitor.core.data.prefs.MonitorPreferences
import com.memscope.taskmonitor.core.data.prefs.MonitorPreferencesRepository
import com.memscope.taskmonitor.core.data.repository.TaskMonitorRepository
import com.memscope.taskmonitor.core.data.service.BackgroundMonitorController
import com.memscope.taskmonitor.core.model.ShizukuStatus
import com.memscope.taskmonitor.core.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShizukuToolsUiState(
    val status: ShizukuStatus = ShizukuStatus(),
    val preferences: MonitorPreferences = MonitorPreferences(),
    val feedbackMessage: String? = null
)

/**
 * ViewModel para el centro avanzado de diagnóstico Shizuku, UserService,
 * control del Foreground Service de monitoreo real y permisos runtime.
 */
class ShizukuToolsViewModel(
    private val shizukuManager: ShizukuManager,
    private val repository: TaskMonitorRepository,
    private val preferencesRepository: MonitorPreferencesRepository,
    private val backgroundController: BackgroundMonitorController
) : ViewModel() {

    private val feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ShizukuToolsUiState> = combine(
        shizukuManager.status,
        preferencesRepository.preferencesFlow,
        feedbackMessage
    ) { status, prefs, fb ->
        ShizukuToolsUiState(
            status = status,
            preferences = prefs,
            feedbackMessage = fb
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ShizukuToolsUiState()
    )

    fun refreshStatus() {
        shizukuManager.refreshState()
    }

    fun requestPermission() {
        val requested = shizukuManager.requestPermission()
        if (!requested) {
            feedbackMessage.value = "Asegúrate de que Shizuku/Sui esté iniciado y no hayas bloqueado el permiso."
        }
    }

    fun bindUserService() {
        shizukuManager.bindUserService()
        feedbackMessage.value = "Solicitando conexión al UserService..."
    }

    fun unbindUserService() {
        shizukuManager.unbindUserService()
        feedbackMessage.value = "UserService desconectado."
    }

    fun setPollingInterval(intervalMs: Long) {
        viewModelScope.launch {
            preferencesRepository.setPollingIntervalMs(intervalMs)
        }
    }

    fun setBackgroundMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundMonitoringEnabled(enabled)
            if (enabled) {
                backgroundController.startBackgroundMonitoring()
                feedbackMessage.value = "Monitoreo en segundo plano activado."
            } else {
                backgroundController.stopBackgroundMonitoring()
                feedbackMessage.value = "Monitoreo en segundo plano detenido."
            }
        }
    }

    fun grantRuntimePermission(packageName: String, permissionName: String) {
        if (packageName.isBlank() || permissionName.isBlank()) {
            feedbackMessage.value = "Ingresa el paquete y el nombre del permiso."
            return
        }
        viewModelScope.launch {
            val res = repository.grantRuntimePermission(packageName.trim(), permissionName.trim())
            feedbackMessage.value = if (res.isSuccess) {
                "Permiso otorgado a $packageName"
            } else {
                "Error otorgando permiso: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    class Factory(
        private val shizukuManager: ShizukuManager,
        private val repository: TaskMonitorRepository,
        private val preferencesRepository: MonitorPreferencesRepository,
        private val backgroundController: BackgroundMonitorController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShizukuToolsViewModel(
                shizukuManager = shizukuManager,
                repository = repository,
                preferencesRepository = preferencesRepository,
                backgroundController = backgroundController
            ) as T
        }
    }
}
