package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Stub de compilación para la interfaz oculta android.content.pm.IPackageManager.
 * Permite compilar llamadas directas vía ShizukuBinderWrapper como:
 * IPackageManager.Stub.asInterface(new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
 * En ejecución, el BootClassLoader de Android resuelve la clase real del sistema.
 */
public interface IPackageManager extends IInterface {

    void grantRuntimePermission(String packageName, String permissionName, int userId)
            throws RemoteException;

    void revokeRuntimePermission(String packageName, String permissionName, int userId)
            throws RemoteException;

    void setApplicationEnabledSetting(
            String packageName,
            int newState,
            int flags,
            int userId,
            String callingPackage
    ) throws RemoteException;

    int getApplicationEnabledSetting(String packageName, int userId)
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
