package android.app;

import android.os.Binder;
import android.os.Debug;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

/**
 * Stub de compilación para la interfaz oculta android.app.IActivityManager.
 * Habilita la consulta de procesos globales, consumo detallado de memoria (Debug.MemoryInfo)
 * y detención forzada de paquetes con privilegios de Shizuku (ADB Shell / Root).
 */
public interface IActivityManager extends IInterface {

    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses()
            throws RemoteException;

    Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids)
            throws RemoteException;

    void forceStopPackage(String packageName, int userId)
            throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
