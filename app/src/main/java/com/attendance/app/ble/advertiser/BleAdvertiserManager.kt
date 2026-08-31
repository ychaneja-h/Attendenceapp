package com.attendance.app.ble.advertiser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

class BleAdvertiserManager(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    private var advertiseCallback: AdvertiseCallback? = null
    var isAdvertising = false
        private set

    @SuppressLint("MissingPermission")
    fun startAdvertising(bleClassId: String, studentId: String, onStartFailure: (Int) -> Unit) {
        if (advertiser == null) {
            Log.e("BleAdvertiser", "BLE Advertiser not available on this device")
            onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e("BleAdvertiser", "Bluetooth is not enabled")
            onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        // Build structured payload:
        // [4 bytes: classId hash][16 bytes: student UUID][4 bytes: timestamp step]
        val payload = buildPayload(bleClassId, studentId)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(0x00E0, payload)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("BleAdvertiser", "Advertising started successfully for student=$studentId in class=$bleClassId")
                isAdvertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("BleAdvertiser", "Advertising failed: $errorCode")
                isAdvertising = false
                onStartFailure(errorCode)
            }
        }

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e("BleAdvertiser", "Error starting advertiser", e)
            onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (advertiseCallback != null && advertiser != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.e("BleAdvertiser", "Error stopping advertiser", e)
            }
            advertiseCallback = null
        }
        isAdvertising = false
        Log.d("BleAdvertiser", "Advertising stopped")
    }

    companion object {
        
        /**
         * Build a structured BLE payload (24 bytes):
         * [4 bytes: SHA-256 truncated hash of classId]
         * [16 bytes: raw student UUID]
         * [4 bytes: epoch timestamp / 30 (step)]
         */
        fun buildPayload(classId: String, studentId: String): ByteArray {
            val buffer = ByteBuffer.allocate(24)

            // 4-byte class ID hash
            val classHash = hashToBytes(classId)
            buffer.put(classHash, 0, 4)

            // 16-byte raw student UUID
            val uuid = try { UUID.fromString(studentId) } catch (e: Exception) { UUID.randomUUID() }
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)

            // 4-byte timestamp step (epoch / 30s)
            val timestampStep = (System.currentTimeMillis() / 30000).toInt()
            buffer.putInt(timestampStep)

            return buffer.array()
        }

        /**
         * Check if a payload timestamp is within acceptable drift window (e.g., +/- 1 step of 30s = 60s total)
         */
        fun isTimestampValid(payload: ByteArray): Boolean {
            if (payload.size < 24) return false
            val buffer = ByteBuffer.wrap(payload)
            buffer.position(20) // Skip 4 (class) + 16 (student)
            val receivedStep = buffer.int
            val currentStep = (System.currentTimeMillis() / 30000).toInt()
            
            return Math.abs(currentStep - receivedStep) <= 1 // Allow 30 seconds drift
        }

        /**
         * Check if a payload class ID hash matches a known class ID.
         */
        fun matchesClassId(payload: ByteArray, classId: String): Boolean {
            if (payload.size < 4) return false
            val receivedHash = payload.copyOfRange(0, 4)
            val expectedHash = hashToBytes(classId).copyOfRange(0, 4)
            return receivedHash.contentEquals(expectedHash)
        }

        /**
         * Extract the Student UUID from the payload.
         */
        fun extractStudentId(payload: ByteArray): String? {
            if (payload.size < 20) return null
            val buffer = ByteBuffer.wrap(payload)
            buffer.position(4) // Skip class hash
            val mostSigBits = buffer.long
            val leastSigBits = buffer.long
            return UUID(mostSigBits, leastSigBits).toString()
        }

        fun hashToBytes(input: String): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
        }
    }
}
