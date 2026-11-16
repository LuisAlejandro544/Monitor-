# MemScope — Administrador de Tareas y Congelador con Shizuku

Aplicación móvil modular en **Kotlin + Jetpack Compose + Material Icons Extended + Shizuku API** diseñada como un Administrador de Tareas de escritorio para Android (distribución directa en APK / Uptodown).

## Arquitectura Modular

- **`:app`**: Punto de entrada (`TaskMonitorApp`, `MainActivity`), `HiddenApiBypass`, `ShizukuProvider`, `BackgroundMonitorService` (Foreground Service `dataSync` con notificación en vivo) y navegación multi-pantalla.
- **`:core:model`**: Modelos de dominio puros (`AppMemoryInfo` con RAM/CPU%/Hilos, `AppPeakRecord`, `SystemMemoryOverview`, `ShizukuStatus`).
- **`:core:hidden-api`**: Stubs `compileOnly` para `IPackageManager`, `IPermissionManager` (Android 11–15) e `IActivityManager`, integrados con `HiddenApiRefinePlugin` (`dev.rikka.tools.refine`).
- **`:core:shizuku`**:
  - `ShizukuSystemBridge.java`: Llamadas Binder remotas vía `ShizukuBinderWrapper` y `SystemServiceHelper` (soporte Android 8–15 en `IPackageManager` y `IPermissionManager`).
  - `TaskMonitorUserService.kt` + `ITaskMonitorService.aidl`: `UserService` privilegiado (con `destroy() = 16777114` e IDs explícitos `1..6` en todos los métodos AIDL) que lee `/proc/<pid>/smaps_rollup` (`Pss`, `Rss`, `Private_Dirty`, `SwapPss`), `/proc/<pid>/status` (`VmRSS`, pico `VmHWM`, `VmSwap`, `Threads`), `/proc/<pid>/stat` + `/proc/stat` (CPU %) y `/proc/meminfo`, además de ejecutar hibernación suave (`AppOps` + `Standby Bucket`).
  - `ShizukuManager.kt`: Gestión reactiva de permisos, Binder y ciclo de vida del `UserService` sobre `Dispatchers.IO`, incluyendo respaldo en `UserService` para Android 14/15 QPRs e hibernación suave.
- **`:core:data`**: Motor de muestreo con Coroutines (lectura prioritaria vía `/proc/<pid>/smaps_rollup` con respaldo selectivo en `IActivityManager`), **CriticalAppProtector** con captura por ciclo (`ProtectionContext` para Launcher y Teclado activos), persistencia optimizada por lotes en **Room** y preferencias en **DataStore**.
- **`:core:ui`**: Tema Material 3 estilo monitor de sistema, gráficas `Canvas`, barra de RAM, chips de protección/hibernación y carga asíncrona de íconos.
- **`:feature:monitor`**: Monitor en vivo (RAM, CPU %, hilos, interruptor de monitoreo en segundo plano) + `AppDetailScreen`.
- **`:feature:peaks`**: Ranking histórico de picos máximos (PSS y `VmHWM`), consumo promedio y tiempo activo.
- **`:feature:freezer`**: Congelador completo (`DISABLED_USER`) e **Hibernación Suave** (`AppOps RUN_ANY_IN_BACKGROUND` + `Standby Bucket RESTRICTED`, sin ocultar íconos del lanzador) con bloqueo automático de paquetes críticos.
- **`:feature:shizuku_tools`**: Diagnóstico Binder/UID (`0` Root vs `2000` ADB), control del `UserService`, activación de monitoreo en segundo plano, intervalos de muestreo y otorgador de permisos Android 8–15.

## Compilación Manual sin PC

- El flujo `.github/workflows/apk-debug.yml` (**APK Debug**) se ejecuta **únicamente de forma manual** (`workflow_dispatch`) desde la pestaña *Actions* de GitHub.
- Genera un APK Debug universal compatible con **32 bits y 64 bits** (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) con `minSdk = 26`, `targetSdk = 35` y `compileSdk = 36`.
