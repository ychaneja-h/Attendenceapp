package com.attendance.app.ble.advertiser

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.attendance.app.ble.AttendanceBlePacket
import com.attendance.app.ble.AttendanceBroadcastSchedule
import com.attendance.app.ble.AttendanceServiceSupport
import com.attendance.app.ble.AttendanceWakeupTimer
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Persists for the attendance attempt, but powers advertising only in ten-second bursts. */
class BleAdvertiserService : Service(), KoinComponent {
    private val repository: AttendanceRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var advertiser: BleAdvertiserManager
    private lateinit var wakeupTimer: AttendanceWakeupTimer
    private var pulseJob: Job? = null
    private var removingTask = false

    override fun onCreate() {
        super.onCreate()
        advertiser = BleAdvertiserManager(this)
        wakeupTimer = AttendanceWakeupTimer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = AttendanceServiceSupport.notification(this, CHANNEL, "Attendance active · face scan required to leave")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else startForeground(NOTIFICATION_ID, notification)
        } catch (error: RuntimeException) {
            repository.reportServiceProblem("student", "Attendance service could not start. Open the app and check Bluetooth permissions.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (removingTask) return START_NOT_STICKY
        if (pulseJob?.isActive == true) return START_STICKY
        pulseJob = scope.launch {
            try {
                runAttendance()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("AttendanceAdvertiser", "Attendance broadcasting interrupted", error)
                repository.reportServiceProblem("student", "Broadcasting interrupted. Reopen attendance to resume; face checkout is still required.")
            } finally {
                wakeupTimer.cancel()
                advertiser.stopAdvertising()
                if (!removingTask) stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun runAttendance() {
        repository.awaitInitialization()
        val initial = repository.activeStudentAttempt() ?: return
        while (scope.isActive) {
            val attempt = repository.activeStudentAttempt() ?: return
            if (attempt.attemptId != initial.attemptId) return
            val elapsed = SystemClock.elapsedRealtime() - attempt.startedElapsedMs
            if (attempt.bootId != repository.bootId || elapsed < 0) {
                repository.interruptStudentAttempt("Face not scanned at leaving; device restarted during attendance")
                return
            }
            // Skip missed slots after sleep/restart. Never fabricate checks or send catch-up bursts.
            val slot = AttendanceBroadcastSchedule.next(attempt.startedElapsedMs,
                SystemClock.elapsedRealtime(), attempt.lastBroadcastSequence)
            if (slot == null) {
                repository.interruptStudentAttempt("Face not scanned at leaving; attendance check sequence exhausted")
                return
            }
            val sequence = slot.sequence
            val waitMs = slot.dueElapsedMs - SystemClock.elapsedRealtime()
            if (waitMs > 0) {
                wakeupTimer.await(slot.dueElapsedMs)
                continue // Reload durable state and recompute elapsed time after device sleep.
            }
            if (slot.missedSlots > 0) {
                repository.reportServiceProblem("student", "Some broadcast times were missed while the device was asleep or interrupted. Sent checks remain pending reconciliation.")
            }
            val payload = AttendanceBlePacket.encode(attempt.classId, attempt.studentId, attempt.attemptId, sequence)
            val wakeLock = AttendanceServiceSupport.wakeLock(this, "attendance-burst", 20_000L)
            var success = false
            try {
                val started = CompletableDeferred<Boolean>()
                advertiser.startAdvertising(payload,
                    onStarted = { started.complete(true) },
                    onFailure = { code ->
                        repository.reportServiceProblem("student", "Broadcast failed (error $code). Check Bluetooth and permissions; retrying automatically.")
                        started.complete(false)
                    })
                success = withTimeoutOrNull(5_000L) { started.await() } == true
                if (success) {
                    // Advertising acceptance is a SENT attempt, never a teacher-confirmed check.
                    repository.recordStudentBroadcast(attempt.attemptId, sequence)
                    repository.reportServiceProblem("student", null)
                    delay(BleAdvertiserManager.BURST_MS)
                } else if (!started.isCompleted) {
                    repository.reportServiceProblem("student", "Bluetooth did not start the broadcast. Retrying automatically.")
                }
            } finally {
                advertiser.stopAdvertising()
                if (wakeLock.isHeld) wakeLock.release()
                wakeupTimer.releaseWakeLock()
            }
            if (!success) wakeupTimer.await(SystemClock.elapsedRealtime() + RETRY_MS)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Android does not promise this callback for force-stop. Durable unresolved entry remains evidence.
        removingTask = true
        pulseJob?.cancel()
        wakeupTimer.cancel()
        advertiser.stopAdvertising()
        scope.launch {
            try {
                repository.interruptStudentAttempt("Face not scanned at leaving; app removed from recent apps")
            } catch (error: Exception) {
                // Leave the durable entry unresolved when storage fails; never turn this into checkout.
                Log.e("AttendanceAdvertiser", "Unable to save app-removal interruption", error)
                repository.reportServiceProblem("student", "Face checkout is missing. Reopen attendance to review the interrupted session.")
            } finally {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        wakeupTimer.cancel()
        advertiser.stopAdvertising()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "BleAdvertiserChannel"
        private const val NOTIFICATION_ID = 101
        private const val RETRY_MS = 30_000L
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BleAdvertiserService::class.java))
        }
        @Suppress("UNUSED_PARAMETER")
        fun start(context: Context, bleClassId: String, studentId: String, className: String) = start(context)
        fun stop(context: Context) { context.stopService(Intent(context, BleAdvertiserService::class.java)) }
    }
}
