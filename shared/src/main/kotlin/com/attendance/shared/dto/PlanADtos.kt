package com.attendance.shared.dto

import kotlinx.serialization.Serializable

/** Immutable evidence, reconciled using teacher receptions or unambiguous trusted entry timing. */
@Serializable
enum class PlanAEventKind { SESSION_START, SESSION_STOP, CHECK_RECEIVED, ENTRY_FACE, BROADCAST_SENT, EXIT_FACE, ATTEMPT_CLOSED, OVERRIDE, DELETE_SESSION }

@Serializable
data class PlanATimeEvidence(
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootId: String,
    val anchorId: String? = null
)

@Serializable
data class PlanARosterStudent(val studentId: String, val name: String, val collegeId: String = "")

@Serializable
data class PlanAEvent(
    val id: String,
    val kind: PlanAEventKind,
    val classId: String,
    val actorId: String,
    val time: PlanATimeEvidence,
    val sessionId: String? = null,
    val attemptId: String? = null,
    val studentId: String? = null,
    /** Student broadcast sequence: zero is entry; only sequences >= 1 count as checks. */
    val sequence: Int? = null,
    val roster: List<PlanARosterStudent> = emptyList(),
    val status: String? = null,
    val reason: String? = null,
    /** Ordered by an actor's persisted revision for overrides; avoids upload-order conflicts. */
    val revision: Long = 0,
    /** Immutable manifest of preceding evidence at teacher stop / student attempt closure. */
    val expectedEventIds: List<String> = emptyList()
)

@Serializable
data class PlanASyncRequest(val events: List<PlanAEvent>)
@Serializable
data class PlanAEventAck(val id: String, val accepted: Boolean, val message: String? = null)
@Serializable
data class PlanASyncResponse(
    val acknowledgements: List<PlanAEventAck>,
    val sessions: List<PlanASessionSnapshot>,
    val deletedSessionIds: List<String> = emptyList(),
    val serverTimeMs: Long,
    val deletedAttemptIds: List<String> = emptyList()
)
@Serializable
data class PlanAStudentSnapshot(
    val studentId: String,
    val name: String,
    val collegeId: String = "",
    val status: String,
    val provisional: Boolean,
    val successfulChecks: Int,
    val totalChecks: Int,
    val entryFaceStatus: String = "UNDER_REVIEW",
    val exitFaceStatus: String = "UNDER_REVIEW",
    val reasons: List<String> = emptyList(),
    val overrideStatus: String? = null,
    val overrideReason: String? = null,
    val attemptIds: List<String> = emptyList()
)
@Serializable
data class PlanASessionSnapshot(
    val sessionId: String,
    val classId: String,
    val className: String,
    val teacherId: String,
    val startedAtMs: Long,
    val stoppedAtMs: Long? = null,
    val status: String,
    val totalChecks: Int,
    val teacherSyncComplete: Boolean,
    val students: List<PlanAStudentSnapshot>,
    val updatedAtMs: Long
)

/** Request started at elapsedRealtimeMs; discard a response taking over five seconds. */
@Serializable
data class PlanATimeAnchorRequest(val bootId: String, val elapsedRealtimeMs: Long)
@Serializable
data class PlanATimeAnchorConfirmRequest(val anchorId: String, val roundTripMs: Long)
@Serializable
data class PlanATimeAnchorResponse(
    val anchorId: String,
    val bootId: String,
    val elapsedRealtimeMs: Long,
    val serverTimeMs: Long,
    val uncertaintyMs: Long = 5_000,
    val maxAgeMs: Long = 30L * 24 * 60 * 60 * 1_000
)
