# MemScope — Administrador de Tareas Android (Kotlin + Shizuku)

Proyecto compuesto por:
1. **Entorno Web (`src/App.tsx`)**: Marcador mínimo `Hello World` para compilar y previsualizar en entornos web Vite.
2. **Aplicación Móvil Android (`android/`)**: Administrador de tareas avanzado estilo escritorio en **Kotlin + Jetpack Compose + Material Icons Extended + Shizuku API**, pensado para distribución directa en APK (Uptodown / terceros) y compilación sin PC mediante GitHub Actions.

## Capacidades Principales
- **Monitor en tiempo real (`:feature:monitor`)**: Consumo de RAM (`PSS` vía `/proc/<pid>/smaps_rollup` en kernels 4.14+ con respaldo selectivo en `IActivityManager`, `RSS`, `Private Dirty`, `Native/Dalvik`, `ZRAM`), uso de **CPU (%)** e **Hilos** por proceso, curva de historial reciente en `Canvas` y detención forzada.
- **Monitoreo real en segundo plano (`BackgroundMonitorService`)**: Foreground Service (`dataSync`) con notificación persistente de baja prioridad que registra picos reales mientras usas otras apps, con acciones de **Pausar/Reanudar** y **Detener**.
- **Picos e Historial RAM (`:feature:peaks`)**: Persistencia en **Room** de cuánto consumió cada app mientras corría, promedio activo, tiempo monitoreado, pico PSS y pico del kernel Linux (`VmHWM`).
- **Congelador e Hibernación Suave (`:feature:freezer`)**:
  - **Hibernación Suave (sin ocultar íconos)**: Restringe ejecución en segundo plano mediante `AppOps` (`RUN_ANY_IN_BACKGROUND`) + `Standby Bucket` (`RESTRICTED`/`RARE`) y detiene procesos activos conservando el ícono visible en el lanzador.
  - **Congelación completa**: Deshabilitación de paquetes (`DISABLED_USER`) vía `IPackageManager` + Shizuku con **lista blanca crítica (`CriticalAppProtector`)** que protege `SystemUI`, Shizuku, el Launcher activo y el Teclado activo.
- **Herramientas Shizuku (`:feature:shizuku_tools`)**: Diagnóstico Binder/Sui, UID (`0` Root / `2000` ADB), control del `UserService`, intervalos de muestreo y otorgamiento de permisos runtime compatible con **Android 8–15** (`IPackageManager` + `IPermissionManager` + respaldo `UserService`).

## Stack y Compilación (`android/`)
- **Gradle Wrapper**: `8.13` (`./gradlew`, `gradlew.bat`, `gradle-wrapper.jar` incluidos)
- **AGP**: `8.13.0` | **Kotlin**: `2.2.20` | **KSP**: `2.2.20-2.0.4`
- **SDK**: `minSdk = 26` (Android 8.0+), `compileSdk = 36`, `targetSdk = 35`
- **Arquitecturas**: 32 bits y 64 bits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`)

### Compilar APK Debug sin PC (GitHub Actions)
1. Sube el proyecto a GitHub.
2. Ve a la pestaña **Actions** y selecciona **APK Debug**.
3. Pulsa **Run workflow** (solo ejecución manual; no se activa con commits ni pull requests).
4. Descarga el artefacto `MemScope-APK-Debug`.
