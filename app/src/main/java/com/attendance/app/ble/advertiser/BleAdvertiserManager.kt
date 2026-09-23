package com.attendance.app.ble.advertiser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import com.attendance.app.ble.AttendanceBlePacket
import com.attendance.app.ble.AttendanceBroadcastSchedule

/** One non-connectable ten-second burst. Never opens a GATT connection or scans. */
class BleAdvertiserManager(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var callback: AdvertiseCallback? = null
    private var activeAdvertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    var isAdvertising = false
        private set

    @SuppressLint("MissingPermission")
    fun startAdvertising(payload: ByteArray, onStarted: () -> Unit, onFailure: (Int) -> Unit) {
        stopAdvertising()
        try {
            val adapter = bluetoothManager.adapter
            val advertiser = adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
            if (advertiser == null) {
                onFailure(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                return
            }
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(BURST_MS.toInt())
                .build()
            val data = AdvertiseData.Builder().setIncludeDeviceName(false)
                .addManufacturerData(AttendanceBlePacket.MANUFACTURER_ID, payload).build()
            val newCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    if (callback !== this) return
                    isAdvertising = true
                    onStarted()
                }

                override fun onStartFailure(errorCode: Int) {
                    if (callback !== this) return
                    callback = null
                    activeAdvertiser = null
                    isAdvertising = false
                    onFailure(errorCode)
                }
            }
            callback = newCallback
            activeAdvertiser = advertiser
            advertiser.startAdvertising(settings, data, newCallback)
        } catch (error: RuntimeException) {
            Log.e("AttendanceAdvertiser", "Unable to start attendance burst", error)
            stopAdvertising()
            onFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val previous = callback
        callback = null
        if (previous != null) {
            try {
                activeAdvertiser?.stopAdvertising(previous)
            } catch (error: RuntimeException) {
                Log.w("AttendanceAdvertiser", "Unable to stop attendance burst", error)
            }
        }
        activeAdvertiser = null
        isAdvertising = false
    }

    companion object {
        const val BURST_MS = 10_000L
        const val INTERVAL_MS = AttendanceBroadcastSchedule.INTERVAL_MS
    }
}
