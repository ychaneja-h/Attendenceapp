package com.attendance.server.services

import com.attendance.shared.dto.*

/** Resolved time ranges are deliberately conservative: an uncertain boundary is reviewable. */
data class PlanAResolvedTime(val earliestMs: Long, val latestMs: Long)

object PlanAEvaluator {
    const val INTERVAL_MS = 300_000L

    /** A student's first synchronization may happen after its session has already been deleted. */
    fun deletedAttemptIds(actorId: String, evidence: List<PlanAEvent>, times: Map<String, PlanAResolvedTime>, nowMs: Long): List<String> {
        val deleted = evidence.filter { it.kind == PlanAEventKind.DELETE_SESSION }.mapNotNull { it.sessionId }.toSet()
        val receipts = evidence.filter { it.kind == PlanAEventKind.CHECK_RECEIVED && it.studentId == actorId && it.sessionId in deleted }
            .mapNotNull { it.attemptId }
        val retained = evidence.filterNot { it.kind == PlanAEventKind.DELETE_SESSION }
        val inferred = deleted.flatMap { sessionId ->
            evaluate(sessionId, "", retained, times, nowMs)?.students?.find { it.studentId == actorId }?.attemptIds.orEmpty()
        }
        return (receipts + inferred).distinct().sorted()
    }

    fun evaluate(
        sessionId: String,
        className: String,
        evidence: List<PlanAEvent>,
        times: Map<String, PlanAResolvedTime>,
        nowMs: Long
    ): PlanASessionSnapshot? {
        val events = evidence.distinctBy { it.id }
        if (events.any { it.kind == PlanAEventKind.DELETE_SESSION && it.sessionId == sessionId }) return null
        val start = events.singleOrNull { it.kind == PlanAEventKind.SESSION_START && it.sessionId == sessionId } ?: return null
        val ownEvents = events.filter { it.sessionId == sessionId && it.actorId == start.actorId }
        val stop = ownEvents.filter { it.kind == PlanAEventKind.SESSION_STOP }.minByOrNull { it.time.elapsedRealtimeMs }
        val sameBoot = stop != null && stop.time.bootId == start.time.bootId && stop.time.elapsedRealtimeMs >= start.time.elapsedRealtimeMs
        val latestReceiptElapsed = ownEvents.filter { it.kind == PlanAEventKind.CHECK_RECEIVED && it.time.bootId == start.time.bootId }
            .maxOfOrNull { it.time.elapsedRealtimeMs } ?: start.time.elapsedRealtimeMs
        val activeDuration = maxOf((latestReceiptElapsed - start.time.elapsedRealtimeMs).coerceAtLeast(0),
            times[start.id]?.let { (nowMs - it.latestMs).coerceAtLeast(0) } ?: 0)
        val duration = if (sameBoot) stop!!.time.elapsedRealtimeMs - start.time.elapsedRealtimeMs else if (stop == null) activeDuration else 0
        val totalChecks = (duration / INTERVAL_MS).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val presentIds = events.mapTo(hashSetOf()) { it.id }
        val teacherComplete = stop != null && start.id in stop.expectedEventIds && stop.expectedEventIds.all { it in presentIds }
        val receipts = ownEvents.filter { it.kind == PlanAEventKind.CHECK_RECEIVED }
        val roster = start.roster.distinctBy { it.studentId }
        val students = roster.map { student ->
            val studentReceipts = receipts.filter { it.studentId == student.studentId }
            val linkedAttempts = studentReceipts.mapNotNull { it.attemptId }.toMutableSet()
            // A missed entry packet still belongs to an unambiguous, trusted active session.
            // Association is separate from the stricter five-minute face acceptance rule.
            events.filter { it.kind == PlanAEventKind.ENTRY_FACE && it.actorId == student.studentId && it.classId == start.classId }.forEach { entry ->
                val candidateStarts = events.filter { candidate ->
                    candidate.kind == PlanAEventKind.SESSION_START && candidate.classId == start.classId &&
                        candidate.roster.any { it.studentId == student.studentId } &&
                        withinSession(entry, candidate, events, times, nowMs)
                }
                if (candidateStarts.size == 1 && candidateStarts.single().sessionId == sessionId) entry.attemptId?.let(linkedAttempts::add)
            }
            val attemptEvents = events.filter { it.actorId == student.studentId && it.attemptId in linkedAttempts && it.classId == start.classId }
            val entries = attemptEvents.filter { it.kind == PlanAEventKind.ENTRY_FACE }
            val exits = attemptEvents.filter { it.kind == PlanAEventKind.EXIT_FACE }
            val closures = attemptEvents.filter { it.kind == PlanAEventKind.ATTEMPT_CLOSED }
            val participating = studentReceipts.isNotEmpty() || entries.isNotEmpty()
            val studentComplete = linkedAttempts.isNotEmpty() && linkedAttempts.all { attempt ->
                val entryIds = entries.filter { it.attemptId == attempt }.map { it.id }
                closures.any { close -> close.attemptId == attempt && entryIds.all { it in close.expectedEventIds } && close.expectedEventIds.all { it in presentIds } }
            }
            val conflictingAttempt = linkedAttempts.any { attempt ->
                events.filter { it.kind == PlanAEventKind.CHECK_RECEIVED && it.attemptId == attempt }.mapNotNull { it.sessionId }.distinct().size > 1
            }
            val transmissions = attemptEvents.filter { it.kind == PlanAEventKind.BROADCAST_SENT }.map { it.attemptId to it.sequence }.toSet()
            val rounds = studentReceipts.filter { (it.sequence ?: 0) > 0 && it.time.bootId == start.time.bootId }
                .sortedBy { it.time.elapsedRealtimeMs }
                .distinctBy { it.attemptId to it.sequence }
                .filter { !studentComplete || (it.attemptId to it.sequence) in transmissions }
                .mapNotNull { receipt ->
                    val delta = receipt.time.elapsedRealtimeMs - start.time.elapsedRealtimeMs
                    if (delta < INTERVAL_MS || (stop != null && sameBoot && delta > duration)) null
                    else (delta / INTERVAL_MS).toInt()
                }.filter { it >= 1 && (stop == null || it <= totalChecks) }.toSet()
            // A transmitted student packet alone never creates a successful check.
            val checks = rounds.size
            val override = ownEvents.filter { it.kind == PlanAEventKind.OVERRIDE && it.studentId == student.studentId }
                .maxWithOrNull(compareBy<PlanAEvent> { it.revision }.thenBy { it.time.wallTimeMs }.thenBy { it.id })
            val reasons = mutableListOf<String>()
            var entryStatus = "UNDER_REVIEW"
            var exitStatus = "UNDER_REVIEW"
            val pending = participating && (!teacherComplete || !studentComplete)
            if (!participating) reasons += "No check-in or received Bluetooth checks"
            else if (pending) reasons += "Awaiting synchronization of complete teacher and student records"
            else {
                if (!sameBoot) reasons += "Session timing could not be verified after clock or device restart"
                if (conflictingAttempt) reasons += "The same student attempt was received in more than one session"
                if (linkedAttempts.size > 1) reasons += "Multiple attendance attempts were recorded for this session; teacher review required"
                entryStatus = faceStatus(entries, start, stop, times, true, reasons)
                exitStatus = if (stop == null) "UNDER_REVIEW" else faceStatus(exits, stop, null, times, false, reasons)
                if (sameBoot && totalChecks == 0) reasons += "No five-minute check rounds occurred; teacher review required"
                else if (sameBoot && checks.toLong() * 4 < totalChecks.toLong() * 3) {
                    val percentage = if (totalChecks == 0) 0 else checks * 100 / totalChecks
                    reasons += "$checks out of $totalChecks checks passed ($percentage%); minimum 75%"
                }
            }
            val computedStatus = when {
                !participating -> "ABSENT"
                pending -> "PRESENT"
                reasons.isNotEmpty() -> "UNDER_REVIEW"
                else -> "PRESENT"
            }
            PlanAStudentSnapshot(student.studentId, student.name, student.collegeId,
                override?.status ?: computedStatus, pending && override == null, checks, totalChecks,
                entryStatus, exitStatus, reasons, override?.status, override?.reason, linkedAttempts.sorted())
        }
        return PlanASessionSnapshot(sessionId, start.classId, className, start.actorId,
            times[start.id]?.let { it.earliestMs + (it.latestMs - it.earliestMs) / 2 } ?: start.time.wallTimeMs,
            stop?.let { times[it.id]?.let { t -> t.earliestMs + (t.latestMs - t.earliestMs) / 2 } ?: it.time.wallTimeMs },
            if (stop == null) "ACTIVE" else "COMPLETED", totalChecks, teacherComplete, students, nowMs)
    }

    private fun withinSession(entry: PlanAEvent, start: PlanAEvent, events: List<PlanAEvent>, times: Map<String, PlanAResolvedTime>, nowMs: Long): Boolean {
        val entryTime = times[entry.id] ?: return false
        val startTime = times[start.id] ?: return false
        val stop = events.filter { it.kind == PlanAEventKind.SESSION_STOP && it.sessionId == start.sessionId && it.actorId == start.actorId }
            .minByOrNull { it.time.elapsedRealtimeMs }
        val end = if (stop == null) nowMs else times[stop.id]?.earliestMs ?: return false
        return entryTime.earliestMs >= startTime.latestMs && entryTime.latestMs <= end
    }

    private fun inWindow(scan: PlanAResolvedTime?, boundary: PlanAResolvedTime?): Boolean? {
        if (scan == null || boundary == null) return null
        if (scan.earliestMs >= boundary.latestMs && scan.latestMs <= boundary.earliestMs + INTERVAL_MS) return true
        if (scan.latestMs < boundary.earliestMs || scan.earliestMs > boundary.latestMs + INTERVAL_MS) return false
        return null
    }

    private fun faceStatus(scans: List<PlanAEvent>, boundary: PlanAEvent, stop: PlanAEvent?, times: Map<String, PlanAResolvedTime>, entry: Boolean, reasons: MutableList<String>): String {
        val label = if (entry) "Entry" else "Exit"
        if (scans.isEmpty()) {
            reasons += if (entry) "Face not scanned at entry" else "Face not scanned at leaving"
            return "MISSING"
        }
        val results = scans.map { scan ->
            val window = inWindow(times[scan.id], times[boundary.id])
            val scanTime = times[scan.id]
            val stopTime = stop?.let { times[it.id] }
            if (entry && stop != null && (scanTime == null || stopTime == null)) null
            else if (entry && stopTime != null && scanTime != null && scanTime.earliestMs >= stopTime.latestMs) false
            else if (entry && stopTime != null && scanTime != null && scanTime.latestMs >= stopTime.earliestMs) null
            else window
        }
        if (results.any { it == true }) return "VALID"
        if (results.any { it == null }) {
            reasons += "$label face timing could not be verified"
            return "UNDER_REVIEW"
        }
        reasons += if (entry) "Face not scanned within five minutes after session start while active" else "Face not scanned within five minutes after session ending"
        return "OUTSIDE_WINDOW"
    }
}
