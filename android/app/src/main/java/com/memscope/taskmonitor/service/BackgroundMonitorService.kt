package com.memscope.taskmonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.memscope.taskmonitor.MainActivity
import com.memscope.taskmonitor.R
import com.memscope.taskmonitor.TaskMonitorApp
import com.memscope.taskmonitor.core.ui.components.MemoryFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground Service de monitoreo en tiempo real mientras el usuario utiliza otras aplicaciones.
 *
 * Mantiene vivo el muestreo de RAM, CPU (%) y picos de consumo en segundo plano, mostrando
 * una notificación discreta (IMPORTANCE_LOW) con el estado de memoria del sistema, la app con
 * mayor consumo y botones directos para pausar/reanudar o detener el servicio.
 */
class BackgroundMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observationJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as TaskMonitorApp
        val repo = app.container.taskMonitorRepository

        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                val currentlyActive = repo.isMonitoringActive.value
                repo.setMonitoringPaused(currentlyActive)
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    app.container.preferencesRepository.setBackgroundMonitoringEnabled(false)
                }
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val initialNotification = buildNotification(
            title = "MemScope • Monitoreo activo",
            content = "Registrando consumo de RAM, CPU y picos en segundo plano...",
            isPaused = !repo.isMonitoringActive.value
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        startObservingMetrics()
        return START_STICKY
    }

    private fun startObservingMetrics() {
        if (observationJob?.isActive == true) return
        val repo = (application as TaskMonitorApp).container.taskMonitorRepository

        observationJob = serviceScope.launch {
            combine(
                repo.systemOverview,
                repo.runningApps,
                repo.isMonitoringActive,
                repo.shizukuStatus
            ) { overview, apps, isActive, shizuku ->
                val usedPct = (overview.usedPercent * 100).toInt()
                val title = if (overview.totalRamKb > 0L) {
                    "RAM: ${MemoryFormatter.formatKb(overview.usedRamKb)} / ${MemoryFormatter.formatKb(overview.totalRamKb)} ($usedPct%) • Libre: ${MemoryFormatter.formatKb(overview.availableRamKb)}"
                } else {
                    "MemScope • Monitor en segundo plano"
                }

                val topApp = apps.firstOrNull()
                val content = when {
                    !isActive -> "Muestreo pausado — toca Reanudar para seguir capturando picos."
                    !shizuku.isFullyOperational -> "Esperando activación/permiso de Shizuku."
                    topApp != null -> "Mayor consumo: ${topApp.appName} (${MemoryFormatter.formatKb(topApp.currentPssKb)} • CPU ${MemoryFormatter.formatCpu(topApp.cpuUsagePercent)})"
                    else -> "Procesos monitoreados: ${overview.activeProcessCount}"
                }

                Triple(title, content, !isActive)
            }.collect { (title, content, isPaused) ->
                val updated = buildNotification(title, content, isPaused)
                notificationManager.notify(NOTIFICATION_ID, updated)
            }
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        isPaused: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, BackgroundMonitorService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (isPaused) "Reanudar" else "Pausar",
                togglePendingIntent
            )
            .addAction(
                0,
                "Detener",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitor de RAM en segundo plano",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificación persistente para registrar picos de memoria y CPU mientras usas otras apps."
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        observationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "memscope_live_monitor"
        private const val NOTIFICATION_ID = 4001
        const val ACTION_TOGGLE_PAUSE = "com.memscope.taskmonitor.action.TOGGLE_PAUSE"
        const val ACTION_STOP = "com.memscope.taskmonitor.action.STOP_MONITOR"
    }
}
