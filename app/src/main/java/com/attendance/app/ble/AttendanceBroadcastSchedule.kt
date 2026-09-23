package com.attendance.app.ble

/** Slot numbers come from elapsed time, never a wall clock or a count of timer callbacks. */
internal object AttendanceBroadcastSchedule {
    const val INTERVAL_MS = 5 * 60_000L
    const val MAX_SEQUENCE = 65535

    data class Slot(val sequence: Int, val dueElapsedMs: Long, val missedSlots: Int)

    fun next(startedElapsedMs: Long, nowElapsedMs: Long, lastSentSequence: Int): Slot? {
        if (startedElapsedMs < 0 || nowElapsedMs < startedElapsedMs || lastSentSequence !in -1..MAX_SEQUENCE) return null
        val elapsedSequence = (nowElapsedMs - startedElapsedMs) / INTERVAL_MS
        val sequence = maxOf(lastSentSequence.toLong() + 1, elapsedSequence)
        if (sequence > MAX_SEQUENCE || startedElapsedMs > Long.MAX_VALUE - sequence * INTERVAL_MS) return null
        return Slot(sequence.toInt(), startedElapsedMs + sequence * INTERVAL_MS,
            (sequence - lastSentSequence - 1).toInt())
    }
}
