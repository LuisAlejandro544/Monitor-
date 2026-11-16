# ROADMAP — MemScope (Task Manager + App Freezer con Shizuku)

## Fase 1 — Base Modular y Monitor en Tiempo Real (Completado)
- [x] Arquitectura multi-módulo Gradle (`:app`, `:core:*`, `:feature:*`) con catálogo centralizado `libs.versions.toml`.
- [x] Integración oficial Shizuku/Sui v13.1.5 (`ShizukuProvider`, listeners de Binder, solicitud de permisos).
- [x] `TaskMonitorUserService` vía AIDL (`destroy() = 16777114`) leyendo `/proc/meminfo` y `/proc/<pid>/status` (`VmRSS`, `VmHWM`, `VmSwap`).
- [x] Pantallas Jetpack Compose + Material Icons Extended: Monitor en vivo, Detalle de App, Picos e Historial (Room), Congelador y Herramientas Shizuku.
- [x] Soporte multi-arquitectura 32 y 64 bits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) y `gradlew` completo.

## Fase 2 — Telemetría Avanzada, Seguridad y Segundo Plano (Completado)
- [x] `BackgroundMonitorService` (`ForegroundService` `dataSync`) con notificación en vivo y controles de Pausar/Detener.
- [x] Medición real de **CPU (%)** e **Hilos (`Threads`)** por proceso desde `/proc/stat`, `/proc/<pid>/stat` y `/proc/<pid>/status`.
- [x] Lista blanca crítica (`CriticalAppProtector`) para proteger Launcher activo, Teclado activo, SystemUI y Shizuku contra congelación accidental.
- [x] Compatibilidad Android 11–15 en permisos (`IPermissionManager`, `IPackageManager`, `HiddenApiRefinePlugin` 4.4.0 y respaldo en `UserService`).
- [x] Workflow manual `APK Debug` (`workflow_dispatch`) sin disparos automáticos.

## Fase 3 — Próximas Mejoras Planificadas
- [ ] Lectura rápida de `/proc/<pid>/smaps_rollup` en kernels Linux 4.14+ para reducir llamadas a `IActivityManager.getProcessMemoryInfo`.
- [ ] Historial de cierres por falta de memoria (`lmkd` / `ApplicationExitInfo` en Android 11+).
- [ ] Hibernación suave sin ocultar íconos del lanzador (`AppOps` `RUN_ANY_IN_BACKGROUND` + `Standby Bucket RESTRICTED`).
- [ ] Selección múltiple en el Congelador y Tile de Ajustes Rápidos (Quick Settings Tile) para congelación en un toque.
- [ ] Exportación de reportes de picos de RAM en CSV/JSON y firma de APK Release con Keystore en GitHub Secrets para Uptodown.
