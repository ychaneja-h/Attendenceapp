package com.attendance.app.ble.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.attendance.app.ble.AttendanceBlePacket

/** The teacher only scans; a manufacturer/class filter also permits screen-off scanning. */
class BleScannerManager(
    private val context: Context,
    private val onDeviceFound: (ByteArray, Int) -> Unit,
    private val onFailure: (String) -> Unit = {}
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var targetClassId: String? = null
    var isScanning = false
        private set

    @SuppressLint("MissingPermission")
    fun startScanning(classId: String) {
        if (isScanning && classId == targetClassId) return
        stopScanning()
        targetClassId = classId
        try {
            if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                onFailure("Allow location access all the time in app settings to receive Bluetooth checks while the screen is off.")
                return
            }
            val adapter = bluetoothManager.adapter
            val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            if (scanner == null) {
                onFailure("Bluetooth scanning unavailable. Keep Bluetooth and its permissions enabled.")
                return
            }
            val expectedHash = AttendanceBlePacket.hash(classId)
            val filter = ScanFilter.Builder().setManufacturerData(
                AttendanceBlePacket.MANUFACTURER_ID, expectedHash, ByteArray(4) { 0xff.toByte() }
            ).build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build()
            val newCallback = object : ScanCallback() {
                private fun accept(result: ScanResult) {
                    if (callback !== this) return
                    val payload = result.scanRecord?.getManufacturerSpecificData(AttendanceBlePacket.MANUFACTURER_ID) ?: return
                    val check = AttendanceBlePacket.decode(payload) ?: return
                    if (!check.classHash.contentEquals(expectedHash)) return
                    // Offline phones may have different clocks; the teacher records reception time.
                    onDeviceFound(payload, result.rssi)
                }

                override fun onScanResult(callbackType: Int, result: ScanResult) = accept(result)
                override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::accept)
                override fun onScanFailed(errorCode: Int) {
                    if (callback !== this) return
                    isScanning = false
                    onFailure("Bluetooth scan interrupted (error $errorCode). Retrying automatically.")
                }
            }
            activeScanner = scanner
            callback = newCallback
            isScanning = true
            scanner.startScan(listOf(filter), settings, newCallback)
        } catch (error: RuntimeException) {
            stopScanning()
            Log.e("AttendanceScanner", "Unable to start attendance scan", error)
            onFailure("Bluetooth scan could not start. Check Bluetooth and permissions.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val previous = callback
        callback = null
        if (previous != null) {
            try {
                activeScanner?.stopScan(previous)
            } catch (error: RuntimeException) {
                Log.w("AttendanceScanner", "Unable to stop attendance scan", error)
            }
        }
        activeScanner = null
        isScanning = false
    }
}
