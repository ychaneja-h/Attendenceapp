package com.attendance.app.ble.scanner

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.attendance.app.MainActivity
import com.attendance.app.R
import java.util.concurrent.ConcurrentHashMap

class BleScannerService : Service() {

    private lateinit var bleScannerManager: BleScannerManager
    private val CHANNEL_ID = "BleScannerChannel"
    private val NOTIFICATION_ID = 102

    // Debounce: track last broadcast time per payload hash
    private val lastBroadcastTime = ConcurrentHashMap<String, Long>()
    private val DEBOUNCE_MS = 3000L // 3 seconds between broadcasts for same payload

    override fun onCreate() {
        super.onCreate()
        bleScannerManager = BleScannerManager(this) { payloadBytes, rssi ->
            // Debounce: only broadcast once per unique payload every 3 seconds
            val payloadKey = payloadBytes.take(8).joinToString("") { "%02x".format(it) }
            val now = System.currentTimeMillis()
            val lastTime = lastBroadcastTime[payloadKey] ?: 0L

            if (now - lastTime >= DEBOUNCE_MS) {
                lastBroadcastTime[payloadKey] = now

                // Secure broadcast: restrict to our app package only
                val intent = Intent(ACTION_DEVICE_FOUND).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_PAYLOAD, payloadBytes)
                    putExtra(EXTRA_RSSI, rssi)
                }
                sendBroadcast(intent)
                Log.d("BleScannerService", "Broadcast scan result: key=$payloadKey rssi=$rssi")
            }
        }
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP_SERVICE) {
            stopScanning()
            stopSelf()
            return START_NOT_STICKY
        }

        val classId = intent?.getStringExtra(EXTRA_CLASS_ID)
        val notification = createNotification("Scanning for students...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        bleScannerManager.startScanning(classId)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        lastBroadcastTime.clear()
    }

    private fun stopScanning() {
        if (bleScannerManager.isScanning) {
            bleScannerManager.stopScanning()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Attendance Scanner Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BleScannerService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Attendance")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_BLE_SCANNER"
        const val ACTION_DEVICE_FOUND = "com.attendance.app.DEVICE_FOUND"
        const val EXTRA_PAYLOAD = "EXTRA_PAYLOAD"
        const val EXTRA_RSSI = "EXTRA_RSSI"
        const val EXTRA_CLASS_ID = "EXTRA_CLASS_ID"

        fun start(context: Context, classId: String? = null) {
            val intent = Intent(context, BleScannerService::class.java).apply {
                putExtra(EXTRA_CLASS_ID, classId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BleScannerService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
