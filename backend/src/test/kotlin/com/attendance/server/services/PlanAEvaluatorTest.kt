package com.attendance.server.services

import com.attendance.shared.dto.*
import org.junit.Assert.*
import org.junit.Test

class PlanAEvaluatorTest {
    private val teacher = "teacher"
    private val student = "student"
    private val attempt = "00112233445566778899aabb"
    private val base = 1_000_000L
    private fun event(id: String, kind: PlanAEventKind, seconds: Long, actor: String = teacher,
                      sequence: Int? = null, expected: List<String> = emptyList(), status: String? = null, revision: Long = 0): PlanAEvent =
        PlanAEvent(id, kind, "class", actor, PlanATimeEvidence(base + seconds * 1000, seconds * 1000, actor + "-boot"),
            sessionId = if (actor == teacher) "session" else null,
            attemptId = if (kind in setOf(PlanAEventKind.SESSION_START, PlanAEventKind.SESSION_STOP, PlanAEventKind.OVERRIDE, PlanAEventKind.DELETE_SESSION)) null else attempt,
            studentId = if (kind == PlanAEventKind.CHECK_RECEIVED || kind == PlanAEventKind.OVERRIDE) student else null,
            sequence = sequence,
            roster = if (kind == PlanAEventKind.SESSION_START) listOf(PlanARosterStudent(student, "Student", "S1"), PlanARosterStudent("absent", "Absent student", "S2")) else emptyList(),
            status = status, revision = revision, expectedEventIds = expected)

    private fun complete(passed: Int = 4, total: Int = 5, entrySeconds: Long = 60, exitOffset: Long? = 60): List<PlanAEvent> {
        val start = event("start", PlanAEventKind.SESSION_START, 0)
        val entry = event("entry", PlanAEventKind.ENTRY_FACE, entrySeconds, student)
        val receptions = (1..passed).map { event("r$it", PlanAEventKind.CHECK_RECEIVED, it * 300L, sequence = it) }
        val broadcasts = (1..passed).map { event("b$it", PlanAEventKind.BROADCAST_SENT, it * 300L, student, sequence = it) }
        val stop = event("stop", PlanAEventKind.SESSION_STOP, total * 300L, expected = listOf(start.id) + receptions.map { it.id })
        val exit = exitOffset?.let { event("exit", PlanAEventKind.EXIT_FACE, total * 300L + it, student) }
        val own = listOf(entry) + broadcasts + listOfNotNull(exit)
        val close = event("close", PlanAEventKind.ATTEMPT_CLOSED, total * 300L + (exitOffset ?: 60), student, expected = own.map { it.id })
        return listOf(start) + own + receptions + stop + close
    }

    private fun snapshot(events: List<PlanAEvent>, resolveTimes: Boolean = true): PlanASessionSnapshot? {
        val times = if (resolveTimes) events.associate { it.id to PlanAResolvedTime(it.time.wallTimeMs, it.time.wallTimeMs) } else emptyMap()
        return PlanAEvaluator.evaluate("session", "Class", events, times, base + 9_000_000)
    }
    private fun result(events: List<PlanAEvent>, resolveTimes: Boolean = true) = snapshot(events, resolveTimes)!!.students.first { it.studentId == student }

    @Test fun `three of five is review and four of five present`() {
        val review = result(complete(3))
        assertEquals("UNDER_REVIEW", review.status)
        assertTrue(review.reasons.any { it.contains("3 out of 5") && it.contains("60%") })
        assertEquals("PRESENT", result(complete(4)).status)
    }
    @Test fun `exactly 75 percent qualifies`() { assertEquals("PRESENT", result(complete(3, 4)).status) }
    @Test fun `live snapshot never reports received checks out of zero rounds`() {
        val events = complete().filterNot { it.kind in setOf(PlanAEventKind.SESSION_STOP, PlanAEventKind.ATTEMPT_CLOSED, PlanAEventKind.EXIT_FACE) }
        val live = snapshot(events)!!
        assertEquals("ACTIVE", live.status)
        assertTrue(live.totalChecks >= 4)
        assertEquals(4, live.students.first { it.studentId == student }.successfulChecks)
        assertTrue(live.students.first { it.studentId == student }.provisional)
    }
    @Test fun `entry five minute boundary inclusive and one millisecond later invalid`() {
        assertEquals("VALID", result(complete(entrySeconds = 300)).entryFaceStatus)
        val late = complete(entrySeconds = 300).map { if (it.id == "entry") it.copy(time = it.time.copy(wallTimeMs = it.time.wallTimeMs + 1, elapsedRealtimeMs = it.time.elapsedRealtimeMs + 1)) else it }
        assertEquals("OUTSIDE_WINDOW", result(late).entryFaceStatus)
        assertEquals("OUTSIDE_WINDOW", result(complete(entrySeconds = -1)).entryFaceStatus)
    }
    @Test fun `exit accepts exactly five minutes and rejects before stop or after deadline`() {
        assertEquals("VALID", result(complete(exitOffset = 300)).exitFaceStatus)
        assertEquals("OUTSIDE_WINDOW", result(complete(exitOffset = 301)).exitFaceStatus)
        assertEquals("OUTSIDE_WINDOW", result(complete(exitOffset = -1)).exitFaceStatus)
    }
    @Test fun `missing exit and failed ratio both remain explained`() {
        val result = result(complete(passed = 3, exitOffset = null))
        assertEquals("UNDER_REVIEW", result.status)
        assertEquals("MISSING", result.exitFaceStatus)
        assertTrue(result.reasons.any { it == "Face not scanned at leaving" })
        assertTrue(result.reasons.any { it.contains("3 out of 5") })
    }
    @Test fun `never attended student is absent with full roster name`() {
        val absent = snapshot(complete())!!.students.single { it.studentId == "absent" }
        assertEquals("ABSENT", absent.status)
        assertEquals("Absent student", absent.name)
        assertEquals(0, absent.successfulChecks)
    }
    @Test fun `arbitrary upload order and repeat uploads do not change results`() {
        val all = complete()
        assertEquals(snapshot(all), snapshot(all.reversed() + all + all))
    }
    @Test fun `manifest arriving before its evidence preserves provisional then updates late`() {
        val all = complete()
        val partial = all.filterNot { it.id == "b1" }
        assertTrue(result(partial).provisional)
        assertEquals("PRESENT", result(partial).status)
        assertFalse(result(all).provisional)
        assertEquals("PRESENT", result(all).status)
        val missingTeacherReceipt = all.filterNot { it.id == "r1" }
        assertFalse(snapshot(missingTeacherReceipt)!!.teacherSyncComplete)
        assertTrue(result(missingTeacherReceipt).provisional)
    }
    @Test fun `replayed packet in a later round is not a second successful check`() {
        val all = complete(passed = 3)
        val replay = event("replay", PlanAEventKind.CHECK_RECEIVED, 1200, sequence = 1)
        assertEquals(3, result(all + replay).successfulChecks)
    }
    @Test fun `student transmissions never count without teacher receptions`() {
        val all = complete().filterNot { it.kind == PlanAEventKind.CHECK_RECEIVED }.map { if (it.kind == PlanAEventKind.SESSION_STOP) it.copy(expectedEventIds = listOf("start")) else it }
        assertEquals(0, result(all).successfulChecks)
        assertEquals("UNDER_REVIEW", result(all).status)
    }
    @Test fun `late entry with a missed Bluetooth packet is reviewed instead of treated as never attending`() {
        val all = complete(entrySeconds = 360).filterNot { it.kind == PlanAEventKind.CHECK_RECEIVED }
            .map { if (it.kind == PlanAEventKind.SESSION_STOP) it.copy(expectedEventIds = listOf("start")) else it }
        val row = result(all)
        assertEquals("UNDER_REVIEW", row.status)
        assertFalse(row.provisional)
        assertEquals("OUTSIDE_WINDOW", row.entryFaceStatus)
        assertEquals(0, row.successfulChecks)
        assertTrue(row.reasons.any { it.contains("0 out of 5") })
    }
    @Test fun `a complete attempt without entry evidence becomes review instead of provisional forever`() {
        val all = complete().filterNot { it.id == "entry" }
            .map { if (it.kind == PlanAEventKind.ATTEMPT_CLOSED) it.copy(expectedEventIds = it.expectedEventIds - "entry") else it }
        val row = result(all)
        assertEquals("UNDER_REVIEW", row.status)
        assertFalse(row.provisional)
        assertEquals("MISSING", row.entryFaceStatus)
        assertTrue(row.reasons.contains("Face not scanned at entry"))
    }
    @Test fun `missing manifested entry remains provisional until its late upload`() {
        val all = complete()
        assertTrue(result(all.filterNot { it.id == "entry" }).provisional)
        assertFalse(result(all).provisional)
    }
    @Test fun `teacher first and student first converge after independent uploads`() {
        val all = complete()
        val teacherEvents = all.filter { it.actorId == teacher }
        val studentEvents = all.filter { it.actorId == student }
        assertTrue(result(teacherEvents).provisional)
        assertNull(snapshot(studentEvents))
        assertEquals(snapshot(all), snapshot(teacherEvents + studentEvents))
        assertEquals(snapshot(all), snapshot(studentEvents + teacherEvents))
    }
    @Test fun `entry sequence and multiple broadcasts in one round never inflate checks`() {
        val all = complete(passed = 1)
        val extra = listOf(
            event("entry-received", PlanAEventKind.CHECK_RECEIVED, 60, sequence = 0),
            event("second-received", PlanAEventKind.CHECK_RECEIVED, 320, sequence = 2),
            event("second-sent", PlanAEventKind.BROADCAST_SENT, 320, student, sequence = 2)
        )
        assertEquals(1, result(all + extra).successfulChecks)
    }
    @Test fun `manual decisions survive evaluation and delayed older decisions`() {
        val present = event("override-2", PlanAEventKind.OVERRIDE, 1800, status = "PRESENT", revision = 2)
        val absent = event("override-1", PlanAEventKind.OVERRIDE, 1900, status = "ABSENT", revision = 1)
        val outcome = result(complete(1, exitOffset = null) + present + absent)
        assertEquals("PRESENT", outcome.status)
        assertEquals("PRESENT", outcome.overrideStatus)
        val reverse = present.copy(id = "override-3", revision = 3, status = "ABSENT")
        assertEquals("ABSENT", result(complete() + present + reverse).status)
    }
    @Test fun `delete tombstone hides session even after late events`() {
        val deletion = event("delete", PlanAEventKind.DELETE_SESSION, 2000)
        assertNull(snapshot(listOf(deletion) + complete()))
    }
    @Test fun `first student sync after deletion includes only that students deleted attempts`() {
        val all = complete() + event("delete", PlanAEventKind.DELETE_SESSION, 2000)
        val times = all.associate { it.id to PlanAResolvedTime(it.time.wallTimeMs, it.time.wallTimeMs) }
        assertEquals(listOf(attempt), PlanAEvaluator.deletedAttemptIds(student, all, times, base + 9_000_000))
        assertTrue(PlanAEvaluator.deletedAttemptIds("absent", all, times, base + 9_000_000).isEmpty())
        assertEquals(listOf(attempt), PlanAEvaluator.deletedAttemptIds(student, all.reversed(), times, base + 9_000_000))
    }
    @Test fun `late entry evidence without receptions stays deleted after first student sync`() {
        val all = complete().filterNot { it.kind == PlanAEventKind.CHECK_RECEIVED } + event("delete", PlanAEventKind.DELETE_SESSION, 2000)
        val times = all.associate { it.id to PlanAResolvedTime(it.time.wallTimeMs, it.time.wallTimeMs) }
        assertNull(snapshot(all))
        assertEquals(listOf(attempt), PlanAEvaluator.deletedAttemptIds(student, all, times, base + 9_000_000))
    }
    @Test fun `uncertain clocks and teacher reboot cannot silently award present`() {
        assertEquals("UNDER_REVIEW", result(complete(), false).status)
        val rebooted = complete().map { if (it.id == "stop") it.copy(time = it.time.copy(bootId = "different-boot")) else it }
        assertEquals("UNDER_REVIEW", result(rebooted).status)
    }
    @Test fun `session shorter than one round requires explicit teacher review`() {
        val all = complete(passed = 0, total = 0, entrySeconds = 60).map {
            if (it.id == "stop") it.copy(time = it.time.copy(wallTimeMs = base + 120_000, elapsedRealtimeMs = 120_000)) else it
        } + event("initial", PlanAEventKind.CHECK_RECEIVED, 60, sequence = 0)
        assertEquals("UNDER_REVIEW", result(all).status)
        assertTrue(result(all).reasons.any { it.contains("No five-minute") })
    }
    @Test fun `entry after stopped short session does not qualify`() {
        val all = complete().map {
            when(it.id) {
                "stop" -> it.copy(time = it.time.copy(wallTimeMs = base + 120_000, elapsedRealtimeMs = 120_000))
                "entry" -> it.copy(time = it.time.copy(wallTimeMs = base + 180_000, elapsedRealtimeMs = 180_000))
                else -> it
            }
        }
        assertEquals("OUTSIDE_WINDOW", result(all).entryFaceStatus)
    }
}
