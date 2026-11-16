# AI_CONTEXT — Contexto Técnico del Proyecto

## Propósito del Sistema
**MemScope** es una aplicación Android modular escrita en **Kotlin** y **Jetpack Compose** que funciona como un **Administrador de Tareas avanzado** apoyado en **Shizuku / Sui** (`dev.rikka.shizuku:api:13.1.5`). Está pensada para distribución externa como APK (Uptodown / terceros), sin restricciones de Play Store para `QUERY_ALL_PACKAGES`.

El entorno web en la raíz (`src/App.tsx`) debe permanecer estrictamente como un **Hello World** mínimo para mantener compilable la vista previa web.

## Decisiones Arquitectónicas Clave
1. **Modularidad estricta**:
   - `:core:model` no tiene dependencias internas.
   - `:core:hidden-api` contiene stubs Java de `IPackageManager`, `IPermissionManager` y `IActivityManager` consumidos únicamente como `compileOnly` desde `:core:shizuku` junto con `dev.rikka.tools.refine` (`4.4.0`) y `HiddenApiBypass` (`4.3`).
   - `:core:shizuku` ejecuta operaciones IPC pesadas en `Dispatchers.IO`. El `TaskMonitorUserService` corre en proceso aislado (UID `0` o `2000`) leyendo `/proc` directamente (`VmRSS`, `VmHWM`, `VmSwap`, `Threads`, `/proc/<pid>/stat`, `/proc/stat`, `/proc/meminfo`).
   - `:core:data` agrega procesos por paquete, calcula deltas de CPU % y picos de sesión, protege paquetes críticos (`CriticalAppProtector`) y persiste historial en **Room** (`2.8.4`) + preferencias en **DataStore** (`1.1.7`).
   - La comunicación hacia `BackgroundMonitorService` (`:app`) se desacopla mediante `BackgroundMonitorController` (`:core:data`).

2. **Compatibilidad Android**:
   - `minSdk = 26` (Android 8.0+): evita errores de `CopyOnWriteArrayList#removeIf` y problemas de API <26 en Shizuku v13.
   - `compileSdk = 36`, `targetSdk = 35`.
   - ABIs soportadas: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.

3. **Licencias**:
   - Todas las dependencias usan licencias permisivas (**Apache-2.0** o **MIT**). No introducir librerías GPL/LGPL/AGPL.
