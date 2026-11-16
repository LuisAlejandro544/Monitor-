package com.memscope.taskmonitor.core.shizuku;

import com.memscope.taskmonitor.core.shizuku.ProcessKernelMem;

/**
 * Interfaz AIDL del UserService de Shizuku (ejecutado en proceso separado con UID 0 o UID 2000).
 * El código de transacción 16777114 corresponde al método destroy exigido por Shizuku
 * para finalizar limpiamente el proceso del UserService al desvincularse.
 *
 * Nota AIDL: Si un método define un ID explícito (destroy = 16777114), todos los demás métodos
 * de la interfaz deben declarar un ID explícito para que aidl compile correctamente.
 */
interface ITaskMonitorService {
    void destroy() = 16777114;

    ProcessKernelMem[] getKernelProcessMemory(in int[] pids) = 1;

    long[] readSystemProcMemInfo() = 2;

    long readTotalCpuJiffies() = 3;

    boolean setRuntimePermissionViaUserService(
        String packageName,
        String permissionName,
        int userId,
        boolean grant
    ) = 4;

    boolean setSoftHibernation(
        String packageName,
        int userId,
        boolean hibernate
    ) = 5;

    String[] getSoftHibernatedPackages(int userId) = 6;
}
