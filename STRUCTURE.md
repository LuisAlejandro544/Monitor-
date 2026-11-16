# STRUCTURE — Estructura Completa del Proyecto

```text
/
├── .github/workflows/
│   └── apk-debug.yml                 # Workflow manual APK Debug desde raíz del repo
├── src/                              # Web placeholder mínimo (Hello World en Vite/React)
├── README.md                         # Visión general y guía rápida
├── ROADMAP.md                        # Estado y plan evolutivo
├── STRUCTURE.md                      # Mapa detallado de carpetas y módulos
├── AI_CONTEXT.md                     # Contexto técnico y decisiones arquitectónicas
├── AGENTS.md                         # Reglas de desarrollo para agentes e IA
├── README_AI.md                      # Resumen operativo rápido para asistentes
└── android/                          # Proyecto Android Gradle (Kotlin + Compose + Shizuku)
    ├── .github/workflows/
    │   └── apk-debug.yml             # Workflow manual APK Debug si android/ es la raíz
    ├── gradlew / gradlew.bat         # Scripts ejecutables del Gradle Wrapper 8.13
    ├── gradle/
    │   ├── libs.versions.toml        # Version Catalog (AGP 8.13.0, Kotlin 2.2.20, Compose, Room, Shizuku)
    │   └── wrapper/
    │       ├── gradle-wrapper.jar    # Binario oficial del wrapper 8.13
    │       └── gradle-wrapper.properties
    ├── build.gradle.kts              # Plugins globales
    ├── settings.gradle.kts           # Declaración de los 10 módulos Gradle
    ├── gradle.properties             # Flags de memoria, KSP incremental y AndroidX
    ├── app/                          # :app — Application, MainActivity, NavHost, BackgroundMonitorService
    ├── core/
    │   ├── model/                    # :core:model — Modelos puros (AppMemoryInfo, ShizukuStatus, etc.)
    │   ├── hidden-api/               # :core:hidden-api — Stubs compileOnly (IPackageManager, IPermissionManager, IActivityManager)
    │   ├── shizuku/                  # :core:shizuku — AIDL, TaskMonitorUserService (/proc + smaps_rollup + AppOps), ShizukuSystemBridge, ShizukuManager
    │   ├── data/                     # :core:data — Room DB, DataStore, CriticalAppProtector (ProtectionContext), TaskMonitorRepository
    │   └── ui/                       # :core:ui — Tema Material 3, Canvas Sparkline, AppPackageIcon, badges
    └── feature/
        ├── monitor/                  # :feature:monitor — MonitorScreen y AppDetailScreen
        ├── peaks/                    # :feature:peaks — PeaksScreen (picos PSS/VmHWM y promedios)
        ├── freezer/                  # :feature:freezer — FreezerScreen (congelación segura e hibernación suave sin ocultar íconos)
        └── shizuku_tools/            # :feature:shizuku_tools — Diagnóstico Binder, UserService, intervalos y permisos
```
