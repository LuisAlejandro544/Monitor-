package com.memscope.taskmonitor.core.model

/**
 * Nivel de privilegio activo obtenido a través del servidor Shizuku o Sui.
 */
enum class ShizukuPrivilegeLevel(val labelEs: String) {
    ROOT("Root (UID 0)"),
    ADB_SHELL("ADB Shell (UID 2000)"),
    NONE("Sin privilegios")
}

/**
 * Estado completo del ciclo de vida de Shizuku/Sui y del UserService privilegiado.
 * Permite que todas las pantallas reaccionen de forma reactiva mediante StateFlow.
 */
data class ShizukuStatus(
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val isPreV11Unsupported: Boolean = false,
    val uid: Int = -1,
    val serverVersion: Int = -1,
    val isSuiDetected: Boolean = false,
    val isUserServiceConnected: Boolean = false,
    val lastErrorMessage: String? = null
) {
    val privilegeLevel: ShizukuPrivilegeLevel
        get() = when {
            !isBinderAlive || !isPermissionGranted -> ShizukuPrivilegeLevel.NONE
            uid == 0 -> ShizukuPrivilegeLevel.ROOT
            uid == 2000 -> ShizukuPrivilegeLevel.ADB_SHELL
            else -> ShizukuPrivilegeLevel.NONE
        }

    val isFullyOperational: Boolean
        get() = isBinderAlive && isPermissionGranted && !isPreV11Unsupported
}

/**
 * Representa una aplicación instalada gestionable desde el módulo Congelador (App Freezer).
 */
data class InstalledAppFreezeInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isFrozen: Boolean,
    val isSoftHibernated: Boolean = false,
    val enabledSetting: Int,
    val isRunningNow: Boolean,
    val currentRamKb: Long,
    val isCriticalProtected: Boolean = false,
    val protectionReason: String? = null
)

/**
 * Opciones de ordenamiento para las vistas de procesos y picos.
 */
enum class ProcessSortOrder(val labelEs: String) {
    CURRENT_RAM_DESC("RAM actual"),
    PEAK_RAM_DESC("Pico máximo"),
    CPU_DESC("CPU %"),
    NAME_ASC("Nombre A-Z"),
    IMPORTANCE("Prioridad")
}

/**
 * Filtro de categoría de aplicaciones.
 */
enum class AppVisibilityFilter(val labelEs: String) {
    USER_ONLY("Usuario"),
    ALL("Todas"),
    SYSTEM_ONLY("Sistema")
}
