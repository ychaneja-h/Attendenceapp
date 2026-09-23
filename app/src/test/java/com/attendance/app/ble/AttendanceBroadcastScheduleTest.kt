package com.attendance.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceBroadcastScheduleTest {
    private val start = 42_000L
    private val interval = AttendanceBroadcastSchedule.INTERVAL_MS

    @Test fun firstCheckIsImmediateAndNextCheckKeepsFiveMinuteCadence() {
        assertEquals(AttendanceBroadcastSchedule.Slot(0, start, 0),
            AttendanceBroadcastSchedule.next(start, start, -1))
        assertEquals(AttendanceBroadcastSchedule.Slot(1, start + interval, 0),
            AttendanceBroadcastSchedule.next(start, start + 10_000L, 0))
    }

    @Test fun processRestartDoesNotRepeatTheLastDurablySentCheck() {
        assertEquals(AttendanceBroadcastSchedule.Slot(4, start + 4 * interval, 0),
            AttendanceBroadcastSchedule.next(start, start + 3 * interval + 50_000L, 3))
    }

    @Test fun wakingAfterSeveralMissedSlotsSendsOnlyTheCurrentSlot() {
        assertEquals(AttendanceBroadcastSchedule.Slot(6, start + 6 * interval, 4),
            AttendanceBroadcastSchedule.next(start, start + 6 * interval + 5_000L, 1))
    }

    @Test fun aFailedCheckRetriesItsIdentityUntilTheNextSlot() {
        assertEquals(0, AttendanceBroadcastSchedule.next(start, start + interval - 1, -1)!!.sequence)
        assertEquals(1, AttendanceBroadcastSchedule.next(start, start + interval, -1)!!.sequence)
    }

    @Test fun hugeElapsedTimeCannotWrapIntoAnOldSequence() {
        assertNull(AttendanceBroadcastSchedule.next(start, Long.MAX_VALUE, -1))
        assertNull(AttendanceBroadcastSchedule.next(start, start, AttendanceBroadcastSchedule.MAX_SEQUENCE))
    }

    @Test fun elapsedClockResetAndCorruptDurableSequenceRequireRecovery() {
        assertNull(AttendanceBroadcastSchedule.next(start, start - 1, -1))
        assertNull(AttendanceBroadcastSchedule.next(start, start, -2))
    }
}
