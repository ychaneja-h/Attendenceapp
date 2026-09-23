package com.attendance.app.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.attendance.app.MainActivity
import com.attendance.app.R

internal object AttendanceServiceSupport {
    fun notification(service: Service, channel: String, text: String, stopIntent: Intent? = null): Notification {
        val manager = service.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channel, "Active attendance", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(service, channel)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Smart Attendance")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (stopIntent != null) {
            val stop = PendingIntent.getService(service, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "End session", stop)
        }
        return builder.build()
    }

    /** Keep work awake only while handling a burst or saving a scan; never lock CPU for the class. */
    fun wakeLock(context: Context, role: String, timeoutMs: Long): PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "attendance:$role").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
}
