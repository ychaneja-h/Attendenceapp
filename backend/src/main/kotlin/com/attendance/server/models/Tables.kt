package com.attendance.server.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = uuid("id").autoGenerate()
    val collegeId = varchar("college_id", 50)
    val name = varchar("name", 200)
    val email = varchar("email", 200).default("")
    val phone = varchar("phone", 15).uniqueIndex()
    val passwordHash = varchar("password_hash", 500)
    val role = varchar("role", 20) // STUDENT, TEACHER, HOD, ADMIN
    val registrationStatus = varchar("registration_status", 30) // PENDING_PHONE_VERIFY, PENDING_APPROVAL, ACTIVE, SUSPENDED
    val departmentCode = varchar("department_code", 50).nullable()
    val accountStatus = varchar("account_status", 20).default("ACTIVE")
    val department = varchar("department", 100).nullable()
    val section = varchar("section", 20).nullable()
    val batch = varchar("batch", 50).nullable()
    val photoUrl = varchar("photo_url", 500).nullable()
    val semester = integer("semester").nullable()
    val academicYear = varchar("academic_year", 20).nullable()
    val deviceId = varchar("device_id", 255).nullable()
    val faceEmbedding = text("face_embedding").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Devices : Table("devices") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val deviceFingerprint = varchar("device_fingerprint", 500)
    val deviceModel = varchar("device_model", 200).nullable()
    val osVersion = varchar("os_version", 50).nullable()
    val appVersion = varchar("app_version", 20).nullable()
    val status = varchar("status", 20) // TRUSTED, PENDING, REVOKED
    val approvedBy = uuid("approved_by").references(Users.id).nullable()
    val registeredAt = timestamp("registered_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val lastActiveAt = timestamp("last_active_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object OtpRecords : Table("otp_records") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id).nullable()
    val phone = varchar("phone", 15)
    val otpHash = varchar("otp_hash", 500)
    val purpose = varchar("purpose", 20) // REGISTRATION, LOGIN
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(3)
    val expiresAt = timestamp("expires_at")
    val verified = bool("verified").default(false)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Departments : Table("departments") {
    val id = uuid("id").autoGenerate()
    val name = varchar("name", 200).uniqueIndex()
    val code = varchar("code", 20).uniqueIndex()
    val hodUserId = uuid("hod_user_id").references(Users.id).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AcademicYears : Table("academic_years") {
    val id = uuid("id").autoGenerate()
    val label = varchar("label", 20) // e.g. 2026-27
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val isCurrent = bool("is_current").default(false)
    override val primaryKey = PrimaryKey(id)
}

object Classes : Table("classes") {
    val id = uuid("id").autoGenerate()
    val teacherId = uuid("teacher_id").references(Users.id)
    val name = varchar("name", 200)
    val section = varchar("section", 50)
    val semester = integer("semester")
    val departmentId = uuid("department_id").references(Departments.id)
    val academicYearId = uuid("academic_year_id").references(AcademicYears.id)
    val classCode = varchar("class_code", 10).uniqueIndex()
    val bleClassId = varchar("ble_class_id", 8) // 4-byte hex string
    val bleClassKey = binary("ble_class_key", 32) // AES-256 key
    val bleKeyVersion = integer("ble_key_version").default(1)
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ClassMembers : Table("class_members") {
    val id = uuid("id").autoGenerate()
    val classId = uuid("class_id").references(Classes.id)
    val studentId = uuid("student_id").references(Users.id)
    val status = varchar("status", 20) // ACTIVE, REMOVED, SUSPENDED
    val approvedBy = uuid("approved_by").references(Users.id).nullable()
    val removalReason = text("removal_reason").nullable()
    val joinedAt = timestamp("joined_at")
    val approvedAt = timestamp("approved_at").nullable()
    val removedAt = timestamp("removed_at").nullable()
    val removedBy = uuid("removed_by").references(Users.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object ClassJoinRequests : Table("class_join_requests") {
    val id = uuid("id").autoGenerate()
    val classId = uuid("class_id").references(Classes.id)
    val studentId = uuid("student_id").references(Users.id)
    val status = varchar("status", 20) // PENDING, APPROVED, REJECTED
    val reviewedBy = uuid("reviewed_by").references(Users.id).nullable()
    val rejectionReason = text("rejection_reason").nullable()
    val requestedAt = timestamp("requested_at")
    val reviewedAt = timestamp("reviewed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object FaceTemplates : Table("face_templates") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id).uniqueIndex()
    val templateVersion = integer("template_version").default(1)
    val enrolledAt = timestamp("enrolled_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object FaceReenrollmentRequests : Table("face_reenrollment_requests") {
    val id = uuid("id").autoGenerate()
    val studentId = uuid("student_id").references(Users.id)
    val status = varchar("status", 20) // PENDING, APPROVED, REJECTED
    val reason = text("reason").nullable()
    val requestedAt = timestamp("requested_at")
    val reviewedBy = uuid("reviewed_by").references(Users.id).nullable()
    val reviewedAt = timestamp("reviewed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AttendanceSessions : Table("attendance_sessions") {
    val id = uuid("id").autoGenerate()
    val classId = uuid("class_id").references(Classes.id)
    val teacherId = uuid("teacher_id").references(Users.id)
    val sessionStatus = varchar("session_status", 20) // ACTIVE, COMPLETED, CANCELLED
    val checkIntervalMinutes = integer("check_interval_minutes").default(10)
    val totalChecks = integer("total_checks").default(5)
    val minSuccessfulChecks = integer("min_successful_checks").default(3)
    val finalCheckRequired = bool("final_check_required").default(true)
    val sessionSecret = binary("session_secret", 32)
    val currentNonce = varchar("current_nonce", 64).nullable()
    val startedAt = timestamp("started_at")
    val scheduledEndAt = timestamp("scheduled_end_at").nullable()
    val actualEndAt = timestamp("actual_end_at").nullable()
    val reviewDeadline = timestamp("review_deadline")
    val isFinalized = bool("is_finalized").default(false)
    val finalizedAt = timestamp("finalized_at").nullable()
    val finalizedBy = varchar("finalized_by", 20).nullable() // TEACHER, SYSTEM_MIDNIGHT
    override val primaryKey = PrimaryKey(id)
}

object AttendanceChecks : Table("attendance_checks") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(AttendanceSessions.id)
    val checkNumber = integer("check_number")
    val bleToken = varchar("ble_token", 64)
    val scheduledAt = timestamp("scheduled_at")
    val windowStart = timestamp("window_start")
    val windowEnd = timestamp("window_end")
    val status = varchar("status", 20) // PENDING, ACTIVE, COMPLETED
    override val primaryKey = PrimaryKey(id)
}

object AttendanceRecords : Table("attendance_records") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(AttendanceSessions.id)
    val studentId = uuid("student_id").references(Users.id)
    val successfulChecks = integer("successful_checks")
    val lastDetectedAt = timestamp("last_detected_at")
    val faceVerified = bool("face_verified").default(true)
    val initialStatus = varchar("initial_status", 50).default("PENDING")
    val overrideStatus = varchar("override_status", 50).nullable()
    val finalStatus = varchar("final_status", 50).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
    val hasCheckoutScan = bool("has_checkout_scan").default(false)
    val reviewReason = varchar("review_reason", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object AttendanceCheckResults : Table("attendance_check_results") {
    val id = uuid("id").autoGenerate()
    val recordId = uuid("record_id").references(AttendanceRecords.id)
    val checkId = uuid("check_id").references(AttendanceChecks.id)
    val detected = bool("detected")
    val rssi = integer("rssi").nullable()
    val tokenValid = bool("token_valid").default(false)
    val wifiConfirmed = bool("wifi_confirmed").default(false)
    val detectedAt = timestamp("detected_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ReviewActions : Table("review_actions") {
    val id = uuid("id").autoGenerate()
    val sessionId = uuid("session_id").references(AttendanceSessions.id)
    val recordId = uuid("record_id").references(AttendanceRecords.id)
    val teacherId = uuid("teacher_id").references(Users.id)
    val action = varchar("action", 30) // MARK_PRESENT, MARK_ABSENT, APPROVE, REJECT
    val oldStatus = varchar("old_status", 20).nullable()
    val newStatus = varchar("new_status", 20)
    val reason = text("reason").nullable()
    val actedAt = timestamp("acted_at")
    override val primaryKey = PrimaryKey(id)
}

object SecurityEvents : Table("security_events") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id).nullable()
    val deviceId = uuid("device_id").references(Devices.id).nullable()
    val eventType = varchar("event_type", 50) // NEW_DEVICE, MULTI_ACCOUNT, SUSPICIOUS_LOGIN, FACE_MISMATCH
    val details = text("details").nullable()
    val resolution = varchar("resolution", 20).nullable() // PENDING, APPROVED, BLOCKED
    val resolvedBy = uuid("resolved_by").references(Users.id).nullable()
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_logs") {
    val id = uuid("id").autoGenerate()
    val actorId = uuid("actor_id").references(Users.id)
    val actorRole = varchar("actor_role", 20)
    val action = varchar("action", 100)
    val targetType = varchar("target_type", 50)
    val targetId = uuid("target_id").nullable()
    val oldState = text("old_state").nullable()
    val newState = text("new_state").nullable()
    val details = text("details").nullable()
    val timestamp = timestamp("timestamp")
    val deviceId = uuid("device_id").references(Devices.id).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Notifications : Table("notifications") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id)
    val type = varchar("type", 50)
    val title = varchar("title", 200)
    val body = text("body")
    val actionUri = varchar("action_uri", 500).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at")
    val readAt = timestamp("read_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SyncQueue : Table("sync_queue") {
    val id = uuid("id").autoGenerate()
    val entityType = varchar("entity_type", 50)
    val entityId = uuid("entity_id")
    val operation = varchar("operation", 20) // CREATE, UPDATE, DELETE
    val payloadJson = text("payload_json")
    val status = varchar("status", 20) // PENDING, IN_PROGRESS, SYNCED, FAILED
    val retryCount = integer("retry_count").default(0)
    val createdAt = timestamp("created_at")
    val lastAttemptedAt = timestamp("last_attempted_at").nullable()
    val errorMessage = text("error_message").nullable()
    override val primaryKey = PrimaryKey(id)
}



object ApprovalRequests : Table("approval_requests") {
    val id = uuid("id").autoGenerate()
    val studentId = uuid("student_id").references(Users.id)
    val requestType = varchar("request_type", 50)
    val requestedData = text("requested_data")
    val status = varchar("status", 20).default("PENDING")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}





