package com.memscope.taskmonitor.core.shizuku;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.permission.IPermissionManager;

import java.util.Collections;
import java.util.List;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Puente Java de llamadas Binder remotas a servicios privilegiados del sistema Android
 * utilizando ShizukuBinderWrapper y SystemServiceHelper.
 *
 * Compatibilidad multi-versión (Android 8 - Android 15):
 * - Mantiene la llamada directa IPackageManager.grantRuntimePermission solicitada.
 * - En Android 11+ (API 30+), si IPackageManager delega o falla por cambios de firma,
 *   intenta IPermissionManager ("permissionmgr") contemplando firmas de Android 11-13
 *   y Android 14-15 (persistentDeviceId = "default:0").
 */
public final class ShizukuSystemBridge {

    private static final String DEFAULT_PERSISTENT_DEVICE_ID = "default:0";

    private ShizukuSystemBridge() {
    }

    private static final class PackageManagerHolder {
        private static final IPackageManager PACKAGE_MANAGER = IPackageManager.Stub.asInterface(
                new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        );
    }

    private static final class PermissionManagerHolder {
        private static final IPermissionManager PERMISSION_MANAGER = initPermissionManager();

        private static IPermissionManager initPermissionManager() {
            IBinder binder = SystemServiceHelper.getSystemService("permissionmgr");
            if (binder == null) {
                return null;
            }
            return IPermissionManager.Stub.asInterface(new ShizukuBinderWrapper(binder));
        }
    }

    private static final class ActivityManagerHolder {
        private static final IActivityManager ACTIVITY_MANAGER = IActivityManager.Stub.asInterface(
                new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
        );
    }

    private static void ensureShizukuReady() {
        if (!Shizuku.pingBinder()) {
            throw new IllegalStateException("El servicio Binder de Shizuku no está activo.");
        }
    }

    /**
     * Otorga un permiso en tiempo de ejecución compatible con Android 8-15.
     * En Android 11+ soporta fallback a IPermissionManager y firmas con persistentDeviceId.
     */
    public static void grantRuntimePermission(String packageName, String permissionName, int userId) {
        ensureShizukuReady();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && PermissionManagerHolder.PERMISSION_MANAGER != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        PermissionManagerHolder.PERMISSION_MANAGER.grantRuntimePermission(
                                packageName,
                                permissionName,
                                DEFAULT_PERSISTENT_DEVICE_ID,
                                userId
                        );
                        return;
                    } catch (NoSuchMethodError | AbstractMethodError ignored) {
                        // En Android 14 pre-QPR o algunas ROMs mantiene la firma de 3 argumentos
                    }
                }
                PermissionManagerHolder.PERMISSION_MANAGER.grantRuntimePermission(
                        packageName,
                        permissionName,
                        userId
                );
                return;
            } catch (NoSuchMethodError | AbstractMethodError ignored) {
                // Respaldo a IPackageManager abajo
            } catch (RemoteException tr) {
                throw new RuntimeException(tr.getMessage(), tr);
            }
        }

        try {
            PackageManagerHolder.PACKAGE_MANAGER.grantRuntimePermission(packageName, permissionName, userId);
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Revoca un permiso en tiempo de ejecución compatible con Android 8-15.
     */
    public static void revokeRuntimePermission(String packageName, String permissionName, int userId) {
        ensureShizukuReady();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && PermissionManagerHolder.PERMISSION_MANAGER != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        PermissionManagerHolder.PERMISSION_MANAGER.revokeRuntimePermission(
                                packageName,
                                permissionName,
                                DEFAULT_PERSISTENT_DEVICE_ID,
                                userId,
                                "MemScope user action"
                        );
                        return;
                    } catch (NoSuchMethodError | AbstractMethodError ignored) {
                        // Respaldo a firma de 4 argumentos
                    }
                }
                PermissionManagerHolder.PERMISSION_MANAGER.revokeRuntimePermission(
                        packageName,
                        permissionName,
                        userId,
                        "MemScope user action"
                );
                return;
            } catch (NoSuchMethodError | AbstractMethodError ignored) {
                // Respaldo a IPackageManager abajo
            } catch (RemoteException tr) {
                throw new RuntimeException(tr.getMessage(), tr);
            }
        }

        try {
            PackageManagerHolder.PACKAGE_MANAGER.revokeRuntimePermission(packageName, permissionName, userId);
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Congela o descongela una aplicación cambiando su estado de habilitación a
     * COMPONENT_ENABLED_STATE_DISABLED_USER (congelada) o COMPONENT_ENABLED_STATE_ENABLED (activa).
     */
    public static void setApplicationFrozen(
            String packageName,
            boolean freeze,
            int userId,
            String callingPackage
    ) {
        ensureShizukuReady();
        int targetState = freeze
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        try {
            PackageManagerHolder.PACKAGE_MANAGER.setApplicationEnabledSetting(
                    packageName,
                    targetState,
                    0,
                    userId,
                    callingPackage
            );
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Consulta el estado real de habilitación del paquete a través de IPackageManager.
     */
    public static int getApplicationEnabledSetting(String packageName, int userId) {
        ensureShizukuReady();
        try {
            return PackageManagerHolder.PACKAGE_MANAGER.getApplicationEnabledSetting(packageName, userId);
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Lista todos los procesos en ejecución del sistema (no limitado a la propia app).
     */
    public static List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
        ensureShizukuReady();
        try {
            List<ActivityManager.RunningAppProcessInfo> list =
                    ActivityManagerHolder.ACTIVITY_MANAGER.getRunningAppProcesses();
            return list != null ? list : Collections.emptyList();
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Obtiene la matriz Debug.MemoryInfo (PSS, PrivateDirty, NativePss, DalvikPss)
     * para un conjunto de PIDs usando IActivityManager.
     */
    public static Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids) {
        ensureShizukuReady();
        if (pids == null || pids.length == 0) {
            return new Debug.MemoryInfo[0];
        }
        try {
            Debug.MemoryInfo[] result =
                    ActivityManagerHolder.ACTIVITY_MANAGER.getProcessMemoryInfo(pids);
            return result != null ? result : new Debug.MemoryInfo[0];
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }

    /**
     * Detiene por completo (Force Stop) todos los procesos y servicios de un paquete.
     */
    public static void forceStopPackage(String packageName, int userId) {
        ensureShizukuReady();
        try {
            ActivityManagerHolder.ACTIVITY_MANAGER.forceStopPackage(packageName, userId);
        } catch (RemoteException tr) {
            throw new RuntimeException(tr.getMessage(), tr);
        }
    }
}
