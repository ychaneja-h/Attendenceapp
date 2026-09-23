package com.attendance.app.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceBlePacketTest {
    private val nonce = "00112233445566778899aabb"

    @Test fun legacyAdvertisementHasExactInteroperableWireFormat() {
        val packet = AttendanceBlePacket.encode("abc", "hello", nonce, 0x1234)
        assertEquals(24, packet.size)
        // Published SHA-256 prefixes for "abc" and "hello", then nonce and unsigned big-endian sequence.
        val expected = "ba7816bf2cf24dba00112233445566778899aabb12340101"
        assertEquals(expected, packet.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }

    @Test fun maximumSequenceAndAttemptNonceSurviveDecoding() {
        val packet = AttendanceBlePacket.encode("class", "student", nonce, 65535)
        val decoded = AttendanceBlePacket.decode(packet)!!
        assertEquals(65535, decoded.sequence)
        assertEquals(nonce, decoded.attemptId)
        assertArrayEquals(packet.copyOfRange(0, 4), decoded.classHash)
        assertArrayEquals(packet.copyOfRange(4, 8), decoded.studentHash)
    }

    @Test fun corruptedVersionTypeAndFrameSizeAreRejected() {
        val packet = AttendanceBlePacket.encode("class", "student", nonce, 0)
        assertNotNull(AttendanceBlePacket.decode(packet))
        assertNull(AttendanceBlePacket.decode(packet.copyOf(23)))
        assertNull(AttendanceBlePacket.decode(packet.copyOf(25)))
        assertNull(AttendanceBlePacket.decode(packet.copyOf().also { it[22] = 2 }))
        assertNull(AttendanceBlePacket.decode(packet.copyOf().also { it[23] = 0 }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedAttemptCannotBecomeRandomIdentity() {
        AttendanceBlePacket.encode("class", "student", "invalid-nonce", 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exhaustedSequenceCannotWrapAndDuplicateAnEarlierCheck() {
        AttendanceBlePacket.encode("class", "student", nonce, 65536)
    }
}
