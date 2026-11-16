package com.memscope.taskmonitor.core.shizuku

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Debug
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.memscope.taskmonitor.core.model.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/**
 * Gestor central del ciclo de vida de Shizuku/Sui y del UserService.
 *
 * Responsabilidades:
 * 1. Registrar listeners de Binder (addBinderReceivedListenerSticky, addBinderDeadListener)
 *    y resultado de permisos (addRequestPermissionResultListener).
 * 2. Exponer un StateFlow<ShizukuStatus> reactivo para toda la UI.
 * 3. Vincular y desvincular el TaskMonitorUserService de forma segura en segundo plano.
 * 4. Despachar todas las llamadas IPC pesadas en Dispatchers.IO sin bloquear el hilo principal.
 */
class ShizukuManager(
    private val applicationId: String,
    private val versionCode: Int,
    private val isDebugBuild: Boolean
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    private var userService: ITaskMonitorService? = null

    @Volatile
    private var isInitialized = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(applicationId, TaskMonitorUserService::class.java.name)
        )
            .daemon(false)
            .tag(USER_SERVICE_TAG)
            .processNameSuffix("monitor_service")
            .debuggable(isDebugBuild)
            .version(versionCode)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                userService = ITaskMonitorService.Stub.asInterface(service)
                _status.update { it.copy(isUserServiceConnected = true, lastErrorMessage = null) }
                Log.d(TAG, "UserService conectado correctamente.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            _status.update { it.copy(isUserServiceConnected = false) }
            Log.d(TAG, "UserService desconectado.")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState()
        if (_status.value.isFullyOperational) {
            bindUserService()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        _status.update {
            it.copy(
                isBinderAlive = false,
                isUserServiceConnected = false,
                uid = -1
            )
        }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                refreshState()
                if (granted) {
                    bindUserService()
                }
            }
        }

    /**
     * Inicializa Sui/Shizuku y suscribe los listeners recomendados por la documentación oficial.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        val suiAvailable = runCatching { Sui.init(applicationId) }.getOrDefault(false)
        _status.update { it.copy(isSuiDetected = suiAvailable) }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        refreshState()
    }

    /**
     * Actualiza el estado completo consultando el Binder de Shizuku.
     */
    fun refreshState() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            _status.update {
                it.copy(
                    isBinderAlive = false,
                    isPermissionGranted = false,
                    isUserServiceConnected = false,
                    uid = -1
                )
            }
            return
        }

        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(false)
        val permissionGranted = if (!preV11) {
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
        } else {
            false
        }
        val rationale = if (!preV11 && !permissionGranted) {
            runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)
        } else {
            false
        }
        val uid = if (permissionGranted) {
            runCatching { Shizuku.getUid() }.getOrDefault(-1)
        } else {
            -1
        }
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)

        _status.update {
            it.copy(
                isBinderAlive = true,
                isPreV11Unsupported = preV11,
                isPermissionGranted = permissionGranted,
                shouldShowRationale = rationale,
                uid = uid,
                serverVersion = version,
                isSuiDetected = runCatching { Sui.isSui() }.getOrDefault(it.isSuiDetected),
                isUserServiceConnected = userService?.asBinder()?.pingBinder() == true
            )
        }
    }

    /**
     * Solicita el permiso de Shizuku siguiendo exactamente el flujo oficial.
     */
    fun requestPermission(): Boolean {
        refreshState()
        val current = _status.value
        if (!current.isBinderAlive || current.isPreV11Unsupported) return false
        if (current.isPermissionGranted) return true
        if (current.shouldShowRationale) return false

        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.getOrElse { error ->
            _status.update { it.copy(lastErrorMessage = error.message) }
            false
        }
    }

    fun bindUserService() {
        scope.launch {
            if (!Shizuku.pingBinder()) return@launch
            if (Shizuku.isPreV11()) return@launch
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return@launch
            if (userService?.asBinder()?.pingBinder() == true) return@launch

            runCatching {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            }.onFailure { err ->
                Log.e(TAG, "Error vinculando UserService", err)
                _status.update { it.copy(lastErrorMessage = err.message) }
            }
        }
    }

    fun unbindUserService() {
        runCatching {
            if (Shizuku.pingBinder()) {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            }
        }
        userService = null
        _status.update { it.copy(isUserServiceConnected = false) }
    }

    fun release() {
        unbindUserService()
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
        isInitialized = false
    }

    suspend fun getRunningAppProcesses(): Result<List<ActivityManager.RunningAppProcessInfo>> =
        withContext(Dispatchers.IO) {
            runCatching { ShizukuSystemBridge.getRunningAppProcesses() }
        }

    suspend fun getProcessMemoryInfo(pids: IntArray): Result<Array<Debug.MemoryInfo>> =
        withContext(Dispatchers.IO) {
            runCatching { ShizukuSystemBridge.getProcessMemoryInfo(pids) }
        }

    suspend fun getKernelProcessMemory(pids: IntArray): Map<Int, ProcessKernelMem> =
        withContext(Dispatchers.IO) {
            val svc = userService
            if (svc == null || !svc.asBinder().pingBinder() || pids.isEmpty()) {
                return@withContext emptyMap()
            }
            runCatching {
                svc.getKernelProcessMemory(pids).associateBy { it.pid }
            }.getOrDefault(emptyMap())
        }

    suspend fun readSystemProcMemInfo(): LongArray? =
        withContext(Dispatchers.IO) {
            val svc = userService
            if (svc == null || !svc.asBinder().pingBinder()) return@withContext null
            runCatching { svc.readSystemProcMemInfo() }.getOrNull()
        }

    suspend fun readTotalCpuJiffies(): Long =
        withContext(Dispatchers.IO) {
            val svc = userService
            if (svc == null || !svc.asBinder().pingBinder()) return@withContext 0L
            runCatching { svc.readTotalCpuJiffies() }.getOrDefault(0L)
        }

    suspend fun setApplicationFrozen(packageName: String, freeze: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = Process.myUserHandle().hashCode()
                ShizukuSystemBridge.setApplicationFrozen(packageName, freeze, userId, applicationId)
            }
        }

    suspend fun forceStopPackage(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = Process.myUserHandle().hashCode()
                ShizukuSystemBridge.forceStopPackage(packageName, userId)
            }
        }

    suspend fun grantRuntimePermission(packageName: String, permissionName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val userId = Process.myUserHandle().hashCode()
            val directResult = runCatching {
                ShizukuSystemBridge.grantRuntimePermission(packageName, permissionName, userId)
            }
            if (directResult.isSuccess) return@withContext directResult

            // Respaldo mediante UserService (Android 14/15 QPRs con cambios de firma AIDL)
            val svc = userService
            if (svc != null && svc.asBinder().pingBinder()) {
                val ok = runCatching {
                    svc.setRuntimePermissionViaUserService(packageName, permissionName, userId, true)
                }.getOrDefault(false)
                if (ok) return@withContext Result.success(Unit)
            }
            directResult
        }

    suspend fun revokeRuntimePermission(packageName: String, permissionName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val userId = Process.myUserHandle().hashCode()
            val directResult = runCatching {
                ShizukuSystemBridge.revokeRuntimePermission(packageName, permissionName, userId)
            }
            if (directResult.isSuccess) return@withContext directResult

            val svc = userService
            if (svc != null && svc.asBinder().pingBinder()) {
                val ok = runCatching {
                    svc.setRuntimePermissionViaUserService(packageName, permissionName, userId, false)
                }.getOrDefault(false)
                if (ok) return@withContext Result.success(Unit)
            }
            directResult
        }

    companion object {
        private const val TAG = "ShizukuManager"
        private const val USER_SERVICE_TAG = "memscope_task_monitor_service"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}
