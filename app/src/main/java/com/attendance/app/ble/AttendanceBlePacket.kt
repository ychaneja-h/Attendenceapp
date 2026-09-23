package com.attendance.app.ble

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Fits a legacy advertisement: flags (3) + manufacturer field (4 + 24) = 31 bytes. */
object AttendanceBlePacket {
    const val MANUFACTURER_ID = 0x00E0
    const val SIZE = 24
    const val VERSION: Byte = 1
    const val CHECK_TYPE: Byte = 1

    data class Check(val classHash: ByteArray, val studentHash: ByteArray, val attemptId: String, val sequence: Int)

    fun hash(value: String): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).copyOf(4)

    fun encode(classId: String, studentId: String, attemptId: String, sequence: Int): ByteArray {
        require(attemptId.matches(Regex("[0-9a-fA-F]{24}"))) { "Attendance attempt must be a 96-bit hexadecimal nonce" }
        require(sequence in 0..65535) { "Attendance check sequence exhausted" }
        val nonce = ByteArray(12) { attemptId.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return ByteBuffer.allocate(SIZE).put(hash(classId)).put(hash(studentId)).put(nonce)
            .putShort(sequence.toShort()).put(VERSION).put(CHECK_TYPE).array()
    }

    fun decode(payload: ByteArray): Check? {
        if (payload.size != SIZE || payload[22] != VERSION || payload[23] != CHECK_TYPE) return null
        val nonce = payload.copyOfRange(8, 20).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sequence = ((payload[20].toInt() and 0xff) shl 8) or (payload[21].toInt() and 0xff)
        return Check(payload.copyOfRange(0, 4), payload.copyOfRange(4, 8), nonce, sequence)
    }
}
