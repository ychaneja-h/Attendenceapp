package com.attendance.app.core.data.attendance

import com.attendance.shared.dto.PlanARosterStudent
import kotlinx.serialization.Serializable

@Serializable
data class LocalTeacherSession(
    val sessionId: String,
    val classId: String,
    val className: String,
    val actorId: String,
    val startedAtMs: Long,
    val startedElapsedMs: Long,
    val bootId: String,
    val roster: List<PlanARosterStudent>
)

@Serializable
data class LocalStudentAttempt(
    val attemptId: String,
    val classId: String,
    val studentId: String,
    val startedAtMs: Long,
    val startedElapsedMs: Long,
    val bootId: String,
    val lastBroadcastSequence: Int = -1,
    val lastBroadcastAtMs: Long? = null,
    val broadcastCount: Int = 0
)

data class AttendanceStudentRow(
    val studentId: String,
    val name: String,
    val status: String,
    val successfulChecks: Int,
    val totalChecks: Int,
    val reasons: List<String> = emptyList(),
    val provisional: Boolean = true,
    val entryFaceStatus: String = "PENDING_SYNC",
    val exitFaceStatus: String = "PENDING_SYNC",
    val manualStatus: String? = null,
    val attemptIds: List<String> = emptyList(),
    val firstReceivedAtMs: Long? = null,
    val lastReceivedAtMs: Long? = null
)

data class SessionHistoryItem(
    val sessionId: String,
    val classId: String,
    val className: String,
    val startedAtMs: Long,
    val stoppedAtMs: Long?,
    val totalChecks: Int,
    val students: List<AttendanceStudentRow>,
    val syncPending: Boolean,
    val status: String,
    val legacy: Boolean = false
)

data class StudentClassResult(
    val classId: String,
    val sessionId: String,
    val startedAtMs: Long,
    val status: String,
    val provisional: Boolean,
    val successfulChecks: Int,
    val totalChecks: Int,
    val reasons: List<String> = emptyList(),
    val entryFaceStatus: String = "PENDING_SYNC",
    val exitFaceStatus: String = "PENDING_SYNC"
)

data class AttendanceState(
    val activeTeacherSession: LocalTeacherSession? = null,
    val activeStudentAttempt: LocalStudentAttempt? = null,
    val sessions: List<SessionHistoryItem> = emptyList(),
    val studentResults: List<StudentClassResult> = emptyList(),
    val syncMessage: String? = null,
    val serviceMessage: String? = null
)

@Serializable
data class PendingFaceAction(val classId: String, val isCheckOut: Boolean)
