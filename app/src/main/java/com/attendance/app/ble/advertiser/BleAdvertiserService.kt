package com.attendance.app.ble.advertiser

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
import androidx.core.app.NotificationCompat
import com.attendance.app.MainActivity
import com.attendance.app.R
import android.util.Log
import kotlinx.coroutines.*

class BleAdvertiserService : Service() {

    private lateinit var bleAdvertiserManager: BleAdvertiserManager
    private val CHANNEL_ID = "BleAdvertiserChannel"
    private val NOTIFICATION_ID = 101
    private var serviceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        bleAdvertiserManager = BleAdvertiserManager(this)
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP_SERVICE) {
            stopAdvertising()
            stopSelf()
            return START_NOT_STICKY
        }

        val bleClassId = intent?.getStringExtra(EXTRA_CLASS_ID) ?: return START_NOT_STICKY
        val studentId = intent?.getStringExtra(EXTRA_STUDENT_ID) ?: return START_NOT_STICKY
        val className = intent?.getStringExtra(EXTRA_CLASS_NAME) ?: "Class"

        val notification = createNotification("Broadcasting to $className...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        bleAdvertiserManager.startAdvertising(bleClassId, studentId) { errorCode ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(applicationContext, "BLE Advertising Failed: Error $errorCode", android.widget.Toast.LENGTH_LONG).show()
            }
            Log.e("BleAdvertiserService", "Failed to start advertising: $errorCode")
            stopSelf()
        }

        // Hard Kill Switch after exactly 10 seconds
        serviceJob?.cancel()
        serviceJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(10000)
            } finally {
                stopSelf() // Stops the service, which calls onDestroy(), which calls stopAdvertising()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        stopAdvertising()
    }

    private fun stopAdvertising() {
        if (bleAdvertiserManager.isAdvertising) {
            bleAdvertiserManager.stopAdvertising()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Attendance Advertiser Service",
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Attendance")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_BLE_ADVERTISER"
        const val EXTRA_CLASS_ID = "EXTRA_CLASS_ID"
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
        const val EXTRA_CLASS_NAME = "EXTRA_CLASS_NAME"

        fun start(context: Context, bleClassId: String, studentId: String, className: String) {
            val intent = Intent(context, BleAdvertiserService::class.java).apply {
                putExtra(EXTRA_CLASS_ID, bleClassId)
                putExtra(EXTRA_STUDENT_ID, studentId)
                putExtra(EXTRA_CLASS_NAME, className)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BleAdvertiserService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
