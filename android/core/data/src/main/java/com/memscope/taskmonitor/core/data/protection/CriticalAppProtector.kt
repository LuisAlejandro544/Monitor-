package com.memscope.taskmonitor.core.data.protection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Resultado de evaluación de seguridad sobre un paquete antes de intentar congelarlo.
 */
data class ProtectionCheckResult(
    val isProtected: Boolean,
    val reason: String? = null
)

/**
 * Lista blanca crítica y detector dinámico de componentes esenciales del dispositivo.
 * Evita que el usuario congele por accidente paquetes vitales (SystemUI, Launcher activo,
 * Teclado activo, Shizuku o la propia app), previniendo bloqueos del teléfono (soft-brick).
 */
class CriticalAppProtector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun evaluate(packageName: String): ProtectionCheckResult {
        if (packageName == context.packageName) {
            return ProtectionCheckResult(true, "Aplicación actual (MemScope)")
        }

        STATIC_PROTECTED_PACKAGES[packageName]?.let { reason ->
            return ProtectionCheckResult(true, reason)
        }

        val activeLauncher = resolveDefaultLauncherPackage()
        if (activeLauncher != null && activeLauncher == packageName) {
            return ProtectionCheckResult(true, "Lanzador de inicio activo")
        }

        val activeKeyboard = resolveDefaultInputMethodPackage()
        if (activeKeyboard != null && activeKeyboard == packageName) {
            return ProtectionCheckResult(true, "Teclado del sistema activo")
        }

        return ProtectionCheckResult(false, null)
    }

    private fun resolveDefaultLauncherPackage(): String? {
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved?.activityInfo?.packageName
        }.getOrNull()
    }

    private fun resolveDefaultInputMethodPackage(): String? {
        return runCatching {
            val imeComponent = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            if (imeComponent.isNullOrBlank()) return null
            ComponentName.unflattenFromString(imeComponent)?.packageName
        }.getOrNull()
    }

    companion object {
        private val STATIC_PROTECTED_PACKAGES = mapOf(
            "android" to "Núcleo del sistema Android",
            "com.android.systemui" to "Interfaz del sistema (SystemUI)",
            "com.android.settings" to "Ajustes del sistema",
            "com.android.phone" to "Servicios de telefonía",
            "com.android.providers.telephony" to "Proveedor de telefonía y SMS",
            "com.android.providers.settings" to "Base de configuración del sistema",
            "com.android.networkstack" to "Pila de conectividad de red",
            "com.android.bluetooth" to "Servicio Bluetooth del sistema",
            "com.android.nfc" to "Servicio NFC del sistema",
            "com.android.permissioncontroller" to "Controlador de permisos",
            "com.google.android.permissioncontroller" to "Controlador de permisos",
            "com.google.android.gms" to "Servicios esenciales del sistema",
            "com.google.android.gsf" to "Marco de servicios",
            "moe.shizuku.privileged.api" to "Servidor Shizuku",
            "rikka.shizuku" to "Cliente Shizuku"
        )
    }
}
