package com.attendance.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    STUDENT, TEACHER, HOD, ADMIN
}

@Serializable
enum class RegistrationStatus {
    PENDING_PHONE_VERIFY, PENDING_APPROVAL, ACTIVE, SUSPENDED
}

@Serializable
enum class DeviceStatus {
    TRUSTED, PENDING, REVOKED
}

@Serializable
enum class ClassMemberStatus {
    ACTIVE, REMOVED, SUSPENDED
}

@Serializable
enum class JoinRequestStatus {
    PENDING, APPROVED, REJECTED
}

@Serializable
enum class SessionStatus {
    ACTIVE, COMPLETED, CANCELLED
}

@Serializable
enum class AttendanceStatus {
    NOT_DETECTED, BLE_DETECTED, FACE_PENDING, CANDIDATE, PRESENT, UNDER_REVIEW, ABSENT
}

@Serializable
enum class FaceVerificationResult {
    SUCCESS, FAILED, SKIPPED
}

@Serializable
enum class OverrideStatus {
    NONE, MANUAL_PRESENT, MANUAL_ABSENT
}

@Serializable
enum class SecurityEventType {
    NEW_DEVICE, MULTI_ACCOUNT, SUSPICIOUS_LOGIN, FACE_MISMATCH
}

@Serializable
enum class SyncStatus {
    PENDING, IN_PROGRESS, SYNCED, FAILED
}

@Serializable
enum class OtpPurpose {
    REGISTRATION, LOGIN
}

@Serializable
enum class ReenrollmentStatus {
    PENDING, APPROVED, REJECTED
}
