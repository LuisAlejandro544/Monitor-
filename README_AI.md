# README_AI — Guía Rápida de Orientación para IA

Lee primero `AI_CONTEXT.md`, `STRUCTURE.md` y `AGENTS.md` antes de editar código.

## Resumen Operativo en 30 Segundos
- **App Android**: `android/` (proyecto Gradle multi-módulo Kotlin + Jetpack Compose).
- **Web placeholder**: `src/App.tsx` (debe seguir siendo solo `Hello World`, compilable con `npm run build` y `npm run lint`).
- **Punto de entrada Android**: `android/app/src/main/java/com/memscope/taskmonitor/TaskMonitorApp.kt` y `MainActivity.kt`.
- **Puente Shizuku**:
  - `android/core/shizuku/src/main/java/com/memscope/taskmonitor/core/shizuku/ShizukuSystemBridge.java`
  - `android/core/shizuku/src/main/java/com/memscope/taskmonitor/core/shizuku/TaskMonitorUserService.kt`
  - `android/core/shizuku/src/main/java/com/memscope/taskmonitor/core/shizuku/ShizukuManager.kt`
- **Motor de Datos y Protección**:
  - `android/core/data/src/main/java/com/memscope/taskmonitor/core/data/repository/TaskMonitorRepository.kt`
  - `android/core/data/src/main/java/com/memscope/taskmonitor/core/data/protection/CriticalAppProtector.kt`
- **Servicio en Segundo Plano**:
  - `android/app/src/main/java/com/memscope/taskmonitor/service/BackgroundMonitorService.kt`
- **Versiones Gradle**: Centralizadas en `android/gradle/libs.versions.toml` (Gradle `8.13`, AGP `8.13.0`, Kotlin `2.2.20`, KSP `2.2.20-2.0.4`, Compose BOM `2025.12.01`, Room `2.8.4`, Shizuku `13.1.5`, Refine `4.4.0`).

## Regla de Oro
Si agregas un nuevo archivo Kotlin/Compose o una nueva librería, actualiza `android/gradle/libs.versions.toml` con dependencias permisivas (Apache-2.0/MIT), verifica `compileSdk = 36` / `minSdk = 26`, y documenta el propósito del archivo en español.
