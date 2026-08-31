package com.attendance.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class StartSessionRequest(
    val classId: String,
    val checkIntervalMinutes: Int = 10,
    val totalChecks: Int = 5,
    val minSuccessfulChecks: Int = 3,
    val finalCheckRequired: Boolean = true
)

@Serializable
data class SessionResponse(
    val id: String,
    val classId: String,
    val className: String,
    val section: String,
    val status: String,
    val checkIntervalMinutes: Int,
    val totalChecks: Int,
    val minSuccessfulChecks: Int,
    val finalCheckRequired: Boolean,
    val startedAt: Long,
    val presentCount: Int = 0,
    val underReviewCount: Int = 0,
    val notDetectedCount: Int = 0,
    val totalStudents: Int = 0
)

@Serializable
data class AttendanceRecordResponse(
    val id: String,
    val studentId: String,
    val studentName: String,
    val collegeId: String,
    val initialStatus: String,
    val finalStatus: String?,
    val successfulChecks: Int,
    val totalChecks: Int,
    val faceVerified: Boolean,
    val overrideStatus: String?
)

@Serializable
data class ManualOverrideRequest(
    val recordId: String,
    val newStatus: String,
    val reason: String? = null
)

@Serializable
data class AttendanceHistoryResponse(
    val date: String,
    val className: String,
    val section: String,
    val status: String,
    val checksPassed: Int,
    val totalChecks: Int
)

@Serializable
data class SyncRequest(
    val idempotencyKey: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val timestamp: Long
)

@Serializable
data class SyncResponse(
    val idempotencyKey: String,
    val accepted: Boolean,
    val message: String? = null
)
