# Reglas ProGuard / R8 para proteger Shizuku UserService, AIDL y puentes ocultos

-keep class com.memscope.taskmonitor.core.shizuku.TaskMonitorUserService { *; }
-keep class com.memscope.taskmonitor.core.shizuku.ITaskMonitorService** { *; }
-keep class com.memscope.taskmonitor.core.shizuku.ProcessKernelMem { *; }
-keep class com.memscope.taskmonitor.core.shizuku.ShizukuSystemBridge { *; }

-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }

# Los stubs de framework Android se resuelven desde BootClassLoader en ejecución
-dontwarn android.content.pm.IPackageManager**
-dontwarn android.permission.IPermissionManager**
-dontwarn android.app.IActivityManager**
-keep class android.content.pm.IPackageManager** { *; }
-keep class android.permission.IPermissionManager** { *; }
-keep class android.app.IActivityManager** { *; }
