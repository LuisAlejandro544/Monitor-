package com.memscope.taskmonitor.core.shizuku;

import com.memscope.taskmonitor.core.shizuku.ProcessKernelMem;

/**
 * Interfaz AIDL del UserService de Shizuku (ejecutado en proceso separado con UID 0 o UID 2000).
 * El código de transacción 16777114 corresponde al método destroy exigido por Shizuku
 * para finalizar limpiamente el proceso del UserService al desvincularse.
 */
interface ITaskMonitorService {
    void destroy() = 16777114;

    ProcessKernelMem[] getKernelProcessMemory(in int[] pids);

    long[] readSystemProcMemInfo();

    long readTotalCpuJiffies();

    boolean setRuntimePermissionViaUserService(
        String packageName,
        String permissionName,
        int userId,
        boolean grant
    );
}
