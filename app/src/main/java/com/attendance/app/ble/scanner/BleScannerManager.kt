package com.attendance.app.ble.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.attendance.app.ble.advertiser.BleAdvertiserManager

class BleScannerManager(
    private val context: Context,
    private val onDeviceFound: (ByteArray, Int) -> Unit // (Raw payload bytes, RSSI)
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    var isScanning = false
        private set

    private var targetClassId: String? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let {
                val payloadData = it.scanRecord?.getManufacturerSpecificData(0x00E0)

                if (payloadData != null && payloadData.size >= 24) {
                    // If targetClassId is set, verify it
                    if (targetClassId != null && !BleAdvertiserManager.matchesClassId(payloadData, targetClassId!!)) {
                        return // Reject payloads not matching our class
                    }

                    // Validate timestamp
                    if (!BleAdvertiserManager.isTimestampValid(payloadData)) {
                        Log.d("BleScanner", "Rejected payload due to invalid timestamp")
                        return // Reject old payloads
                    }

                    Log.d("BleScanner", "Found valid student payload (${payloadData.size} bytes) RSSI: ${it.rssi}")
                    onDeviceFound(payloadData, it.rssi)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BleScanner", "Scan failed: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(classId: String? = null) {
        targetClassId = classId
        if (scanner == null) {
            Log.e("BleScanner", "BLE Scanner not available")
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e("BleScanner", "Bluetooth is not enabled")
            return
        }

        val scanFilters: List<ScanFilter> = emptyList()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(scanFilters, scanSettings, scanCallback)
            isScanning = true
            Log.d("BleScanner", "Started scanning for student beacons...")
        } catch (e: Exception) {
            Log.e("BleScanner", "Error starting scanner", e)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning && scanner != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e("BleScanner", "Error stopping scanner", e)
            }
            isScanning = false
            Log.d("BleScanner", "Stopped scanning.")
        }
    }

    companion object {
        const val APP_SERVICE_UUID = "0000A1B2-0000-1000-8000-00805F9B34FB"
    }
}
