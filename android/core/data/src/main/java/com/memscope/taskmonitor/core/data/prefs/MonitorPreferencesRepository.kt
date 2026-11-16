package com.memscope.taskmonitor.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monitorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "memscope_preferences"
)

/**
 * Preferencias de usuario del monitor de tareas persistidas con DataStore.
 */
data class MonitorPreferences(
    val pollingIntervalMs: Long = 2000L,
    val showSystemAppsByDefault: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = false
)

/**
 * Repositorio ligero de preferencias para ajustar frecuencia de muestreo,
 * filtros y estado del Foreground Service de monitoreo continuo en segundo plano.
 */
class MonitorPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<MonitorPreferences> = context.monitorDataStore.data.map { prefs ->
        MonitorPreferences(
            pollingIntervalMs = prefs[KEY_POLLING_INTERVAL_MS] ?: 2000L,
            showSystemAppsByDefault = prefs[KEY_SHOW_SYSTEM_APPS] ?: false,
            backgroundMonitoringEnabled = prefs[KEY_BACKGROUND_MONITORING] ?: false
        )
    }

    suspend fun setPollingIntervalMs(intervalMs: Long) {
        context.monitorDataStore.edit { prefs ->
            prefs[KEY_POLLING_INTERVAL_MS] = intervalMs.coerceIn(1000L, 10_000L)
        }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.monitorDataStore.edit { prefs ->
            prefs[KEY_SHOW_SYSTEM_APPS] = show
        }
    }

    suspend fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        context.monitorDataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_MONITORING] = enabled
        }
    }

    companion object {
        private val KEY_POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
        private val KEY_SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        private val KEY_BACKGROUND_MONITORING = booleanPreferencesKey("background_monitoring_enabled")
    }
}
