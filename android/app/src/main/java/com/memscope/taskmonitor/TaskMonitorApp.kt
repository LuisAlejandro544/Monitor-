package com.memscope.taskmonitor

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.memscope.taskmonitor.core.data.db.TaskMonitorDatabase
import com.memscope.taskmonitor.core.data.prefs.MonitorPreferencesRepository
import com.memscope.taskmonitor.core.data.repository.TaskMonitorRepository
import com.memscope.taskmonitor.core.data.service.BackgroundMonitorController
import com.memscope.taskmonitor.core.shizuku.ShizukuManager
import com.memscope.taskmonitor.service.BackgroundMonitorService
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Contenedor de dependencias modular para evitar sobrecarga de reflexión en arranque
 * y mantener desacoplados los módulos core y feature.
 */
class AppContainer(private val context: Context) {
    val shizukuManager = ShizukuManager(
        applicationId = BuildConfig.APPLICATION_ID,
        versionCode = BuildConfig.VERSION_CODE,
        isDebugBuild = BuildConfig.DEBUG
    )

    private val database = TaskMonitorDatabase.getInstance(context)
    val preferencesRepository = MonitorPreferencesRepository(context)

    val taskMonitorRepository = TaskMonitorRepository(
        context = context,
        shizukuManager = shizukuManager,
        historyDao = database.memoryHistoryDao(),
        preferencesRepository = preferencesRepository
    )

    val backgroundMonitorController = object : BackgroundMonitorController {
        override fun startBackgroundMonitoring() {
            val intent = Intent(context, BackgroundMonitorService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        override fun stopBackgroundMonitoring() {
            val intent = Intent(context, BackgroundMonitorService::class.java)
            context.stopService(intent)
        }
    }
}

/**
 * Clase Application de MemScope.
 * Desbloquea el acceso a interfaces ocultas del framework (HiddenApiBypass) en Android 9+
 * e inicializa el puente de Shizuku/Sui desde el inicio de la aplicación.
 */
class TaskMonitorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.shizukuManager.initialize()
    }
}
