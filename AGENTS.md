# AGENTS — Reglas y Protocolo para Asistentes de Código

Al modificar o ampliar este repositorio, cualquier agente o desarrollador debe respetar estas reglas:

1. **Separación Web vs Android**:
   - No alterar `src/App.tsx` más allá del `Hello World` mínimo salvo petición explícita del usuario.
   - Toda la lógica móvil reside bajo `android/`.

2. **Reglas Shizuku y Android**:
   - No usar propiedades `persist.sys.*`.
   - No usar el método obsoleto `Shizuku#newProcess`; usar `ITaskMonitorService` (`UserService`) o llamadas Binder remotas vía `ShizukuBinderWrapper`.
   - Mantener `destroy() = 16777114` en `ITaskMonitorService.aidl` terminando con `exitProcess(0)` y asignar IDs de transacción explícitos a todos los métodos de la interfaz AIDL.
   - Mantener la compatibilidad multi-versión Android 8–15 en `ShizukuSystemBridge` (`IPackageManager` + `IPermissionManager` + respaldo `UserService`) y `compileOnly(project(":core:hidden-api"))` con `dev.rikka.tools.refine`.
   - Nunca permitir congelar ni aplicar hibernación suave a paquetes críticos protegidos por `CriticalAppProtector` (SystemUI, Shizuku, Launcher activo, Teclado activo, app propia).

3. **Rendimiento y Concurrencia**:
   - Todo acceso a `/proc`, `PackageManager`, `IActivityManager`, Room y DataStore debe ejecutarse en `Dispatchers.IO` o `Dispatchers.Default`, nunca en el hilo principal (`Main`).
   - Cargar íconos de aplicaciones de forma asíncrona fuera del hilo de UI.

4. **UI y Diseño**:
   - Conservar las pantallas separadas (`Monitor`, `Picos RAM`, `Congelador`, `Shizuku` y `AppDetailScreen`) usando `NavigationBar` y diálogos de confirmación para acciones destructivas.
   - No rediseñar pantallas existentes ni cambiar íconos sin solicitud explícita.
   - Explicar con comentarios claros en español el propósito de cada archivo y lógica relevante.

5. **Compilación y Workflows**:
   - Mantener soporte explícito de 32 y 64 bits (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`).
   - Los workflows de compilación (`APK Debug`) deben permanecer manuales (`workflow_dispatch`).
   - No añadir dependencias con licencias copyleft (GPL/AGPL).
