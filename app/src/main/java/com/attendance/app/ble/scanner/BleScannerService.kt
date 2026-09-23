package com.attendance.app.ble.scanner

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.attendance.app.ble.AttendanceBlePacket
import com.attendance.app.ble.AttendanceServiceSupport
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Receives and saves checks without depending on an Activity or ViewModel being alive. */
class BleScannerService : Service(), KoinComponent {
    private val repository: AttendanceRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var scanner: BleScannerManager
    private var scanJob: Job? = null
    private var runningSessionId: String? = null
    private var endingSession = false
    private val seen = LinkedHashMap<String, Boolean>()
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state != BluetoothAdapter.STATE_ON) {
                scanner.stopScanning()
                repository.reportServiceProblem("teacher", "Bluetooth is off. Checks cannot be received until Bluetooth is enabled.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        scanner = BleScannerManager(this, ::saveReception) { problem ->
            repository.reportServiceProblem("teacher", problem)
        }
        ContextCompat.registerReceiver(this, bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val end = Intent(this, BleScannerService::class.java).setAction(ACTION_STOP_SERVICE)
            val notification = AttendanceServiceSupport.notification(this, CHANNEL, "Attendance active · receiving student checks", end)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val legacyLocationType = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or legacyLocationType)
            } else startForeground(NOTIFICATION_ID, notification)
        } catch (error: RuntimeException) {
            repository.reportServiceProblem("teacher", "Attendance service could not start. Open the app and check Bluetooth permissions.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (endingSession) return START_NOT_STICKY
        if (intent?.action == ACTION_STOP_SERVICE) {
            endingSession = true
            scanner.stopScanning()
            scanJob?.cancel()
            scope.launch {
                try { repository.endTeacherSession() }
                catch (error: Exception) {
                    repository.reportServiceProblem("teacher", "Could not save session ending. Reopen attendance to stop it.")
                } finally { stopSelf() }
            }
            return START_NOT_STICKY
        }
        if (scanJob?.isActive == true) return START_STICKY
        scanJob = scope.launch {
            try {
                repository.awaitInitialization()
                val session = repository.activeTeacherSession() ?: return@launch
                runningSessionId = session.sessionId
                while (isActive) {
                    val active = repository.activeTeacherSession() ?: break
                    if (active.sessionId != runningSessionId) break
                    if (!scanner.isScanning) {
                        scanner.startScanning(active.classId)
                        if (scanner.isScanning) repository.reportServiceProblem("teacher", null)
                    }
                    // A failure cannot create a tight start/stop loop or exceed Android scan-start limits.
                    delay(RETRY_MS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("AttendanceScanner", "Attendance scanning interrupted", error)
                repository.reportServiceProblem("teacher", "Scanning interrupted. Reopen attendance to resume receiving checks.")
            } finally {
                scanner.stopScanning()
                if (!endingSession) stopSelf()
            }
        }
        return START_STICKY
    }

    private fun saveReception(payload: ByteArray, rssi: Int) {
        val check = AttendanceBlePacket.decode(payload) ?: return
        val expectedSession = runningSessionId ?: return
        val studentKey = check.studentHash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val key = "$expectedSession:$studentKey:${check.attemptId}:${check.sequence}"
        if (seen.containsKey(key)) return
        seen[key] = true
        if (seen.size > 4096) seen.remove(seen.keys.first())
        scope.launch {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                wakeLock = AttendanceServiceSupport.wakeLock(this@BleScannerService, "save-attendance-check", 10_000L)
                if (repository.activeTeacherSession()?.sessionId != expectedSession) return@launch
                repository.recordTeacherReception(check.studentHash, check.attemptId, check.sequence, rssi, expectedSessionId = expectedSession)
            } catch (cancelled: CancellationException) {
                seen.remove(key)
                throw cancelled
            } catch (error: Exception) {
                seen.remove(key) // Allow another packet from this same burst to retry the durable write.
                Log.e("AttendanceScanner", "Could not save received attendance check", error)
                repository.reportServiceProblem("teacher", "A received check could not be saved. Keep attendance open and check device storage.")
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
            }
        }
    }

    override fun onDestroy() {
        scanner.stopScanning()
        scope.cancel()
        unregisterReceiver(bluetoothReceiver)
        seen.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_BLE_SCANNER"
        private const val CHANNEL = "BleScannerChannel"
        private const val NOTIFICATION_ID = 102
        private const val RETRY_MS = 30_000L
        @Suppress("UNUSED_PARAMETER")
        fun start(context: Context, classId: String? = null) {
            ContextCompat.startForegroundService(context, Intent(context, BleScannerService::class.java))
        }
        fun stop(context: Context) { context.stopService(Intent(context, BleScannerService::class.java)) }
    }
}
