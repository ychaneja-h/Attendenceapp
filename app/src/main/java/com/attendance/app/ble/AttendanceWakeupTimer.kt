package com.attendance.app.ble

import android.app.AlarmManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A wake-up alarm supplements the uptime-based coroutine timer while this foreground service lives.
 * Ordinary screen-off sleep can wake for a check; Doze/OEM restrictions may still defer delivery.
 * The sticky service reloads durable slots after process death. This timer cannot bypass force-stop.
 */
internal class AttendanceWakeupTimer(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: AlarmManager.OnAlarmListener? = null
    private var fallback: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    suspend fun await(dueElapsedMs: Long) = suspendCancellableCoroutine<Unit> { continuation ->
        cancel()
        val wake = Runnable {
            clearCallbacks()
            if (continuation.isActive) {
                // Bridge alarm delivery to the repository read and short broadcast that follow it.
                wakeLock = try {
                    AttendanceServiceSupport.wakeLock(context, "scheduled-check", 20_000L)
                } catch (error: RuntimeException) {
                    Log.w("AttendanceTimer", "Unable to keep the scheduled check awake", error)
                    null
                }
                continuation.resume(Unit)
            }
        }
        val alarm = AlarmManager.OnAlarmListener { wake.run() }
        listener = alarm
        fallback = wake
        continuation.invokeOnCancellation { cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try {
            // Listener alarms are tied to this running service. Some Android versions require
            // exact-alarm access; use an inexact wake-up there instead of requesting extra access.
            try {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, dueElapsedMs,
                    "attendance-check", alarm, handler)
            } catch (_: SecurityException) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, dueElapsedMs,
                    "attendance-check", alarm, handler)
            }
        } catch (error: RuntimeException) {
            Log.w("AttendanceTimer", "Wake-up alarm unavailable; using the running service timer", error)
        }
        handler.postDelayed(wake, (dueElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))
    }

    fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    fun cancel() {
        clearCallbacks()
        releaseWakeLock()
    }

    private fun clearCallbacks() {
        listener?.let {
            try { alarmManager.cancel(it) }
            catch (error: RuntimeException) {
                Log.w("AttendanceTimer", "Unable to cancel a completed check alarm", error)
            }
        }
        listener = null
        fallback?.let(handler::removeCallbacks)
        fallback = null
    }
}
