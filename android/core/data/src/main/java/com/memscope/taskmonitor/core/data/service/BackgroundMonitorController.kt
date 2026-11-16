package com.memscope.taskmonitor.core.data.service

/**
 * Contrato desacoplado para iniciar o detener el Foreground Service de monitoreo real
 * desde cualquier módulo feature sin crear dependencias hacia el módulo :app.
 */
interface BackgroundMonitorController {
    fun startBackgroundMonitoring()
    fun stopBackgroundMonitoring()
}
