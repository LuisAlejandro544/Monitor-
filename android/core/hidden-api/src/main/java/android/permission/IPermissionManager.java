package android.permission;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Stub de compilación para android.permission.IPermissionManager (Android 11 - 15).
 * En Android 11+ (API 30+), gran parte de la gestión de permisos runtime se movió
 * al servicio del sistema "permissionmgr". En Android 14/15 QPR algunas variantes
 * añaden parámetros como persistentDeviceId / deviceId, por lo que ShizukuSystemBridge
 * utiliza este stub junto con manejo defensivo y fallback al UserService.
 */
public interface IPermissionManager extends IInterface {

    void grantRuntimePermission(String packageName, String permissionName, int userId)
            throws RemoteException;

    void grantRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId)
            throws RemoteException;

    void revokeRuntimePermission(String packageName, String permissionName, int userId, String reason)
            throws RemoteException;

    void revokeRuntimePermission(String packageName, String permissionName, String persistentDeviceId, int userId, String reason)
            throws RemoteException;

    abstract class Stub extends Binder implements IPermissionManager {
        public static IPermissionManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
