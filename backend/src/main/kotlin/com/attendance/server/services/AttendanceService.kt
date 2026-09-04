package com.attendance.server.services

import com.attendance.server.models.AttendanceRecords
import com.attendance.server.models.AttendanceSessions
import com.attendance.server.models.Classes
import com.attendance.server.models.Users
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class AttendanceService {

    fun startSession(classId: String, teacherId: String, checkIntervalMinutes: Int, minSuccessfulChecks: Int): String {
        return transaction {
            val cid = UUID.fromString(classId)
            val tid = UUID.fromString(teacherId)
            
            // Check if already active
            val existing = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid)  }
                .firstOrNull()
                
            if (existing != null) {
                // Update settings if already active
                AttendanceSessions.update({ AttendanceSessions.id eq existing[AttendanceSessions.id] }) {
                    it[this.checkIntervalMinutes] = checkIntervalMinutes
                    it[this.minSuccessfulChecks] = minSuccessfulChecks
                }
                return@transaction existing[AttendanceSessions.id].toString()
            }
            
            val sessionId = AttendanceSessions.insert {
                it[this.classId] = cid
                it[this.teacherId] = tid
                it[sessionStatus] = "ACTIVE"
                it[this.checkIntervalMinutes] = checkIntervalMinutes
                it[this.minSuccessfulChecks] = minSuccessfulChecks
                it[startedAt] = java.time.Instant.now()
                it[reviewDeadline] = java.time.Instant.now().plus(java.time.Duration.ofDays(1))
                it[sessionSecret] = ByteArray(32)
            }[AttendanceSessions.id]
            
            sessionId.toString()
        }
    }

    fun stopSession(classId: String) {
        transaction {
            val cid = UUID.fromString(classId)
            val session = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid)  }
                .firstOrNull() ?: return@transaction
                
            val sessionId = session[AttendanceSessions.id]
            
            AttendanceSessions.update({ AttendanceSessions.id eq sessionId }) {
                it[sessionStatus] = "COMPLETED"
            }
            
            // Note: The logic in syncRecords already sets the correct final status dynamically.
            // We just need to ensure anyone who was UNDER_REVIEW is locked as UNDER_REVIEW until manual review,
            // which is their current state. Any zero-check is ABSENT, which is default.
        }
    }

    fun stopSession(sessionId: UUID) {
        transaction {
            val session = AttendanceSessions.selectAll()
                .where { AttendanceSessions.id eq sessionId }
                .firstOrNull() ?: return@transaction
                
            val minChecks = session[AttendanceSessions.minSuccessfulChecks]
            
            AttendanceSessions.update({ AttendanceSessions.id eq sessionId }) {
                it[sessionStatus] = "COMPLETED"
            }
            
            val records = AttendanceRecords.selectAll().where { AttendanceRecords.sessionId eq sessionId }
            for (record in records) {
                val successfulChecks = record[AttendanceRecords.successfulChecks]
                val hasCheckoutScan = record[AttendanceRecords.hasCheckoutScan]
                
                var newStatus = "ABSENT"
                var reason: String? = null
                
                if (successfulChecks >= minChecks && hasCheckoutScan) {
                    newStatus = "PRESENT"
                    reason = null
                } else if (successfulChecks >= minChecks && !hasCheckoutScan) {
                    newStatus = "UNDER_REVIEW"
                    reason = "Final face not scanned"
                } else if (successfulChecks > 0 && successfulChecks < minChecks && hasCheckoutScan) {
                    newStatus = "UNDER_REVIEW"
                    reason = "Only $successfulChecks of $minChecks checks recorded"
                } else if (successfulChecks > 0 && successfulChecks < minChecks && !hasCheckoutScan) {
                    newStatus = "UNDER_REVIEW"
                    reason = "Only $successfulChecks of $minChecks checks, final face not scanned"
                } else if (successfulChecks == 0) {
                    newStatus = "ABSENT"
                    reason = "No checks recorded"
                }
                
                AttendanceRecords.update({ AttendanceRecords.id eq record[AttendanceRecords.id] }) {
                    it[finalStatus] = newStatus
                    it[reviewReason] = reason
                }
            }
        }
    }

    fun syncRecords(records: List<SyncRecordRequest>): List<SyncRecordResponse> {
        return transaction {
            val todayStart = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            
            // Auto-lock past sessions
            val pastSessions = AttendanceSessions.selectAll().where { AttendanceSessions.startedAt less todayStart }
            val pastSessionIds = pastSessions.map { it[AttendanceSessions.id] }
            if (pastSessionIds.isNotEmpty()) {
                AttendanceRecords.update({ (AttendanceRecords.sessionId inList pastSessionIds) and (AttendanceRecords.finalStatus eq "UNDER_REVIEW") }) {
                    it[finalStatus] = "ABSENT"
                }
                AttendanceSessions.update({ AttendanceSessions.id inList pastSessionIds }) {
                    it[sessionStatus] = "COMPLETED"
                }
            }

            records.map { record ->
                val cid = UUID.fromString(record.classId)
                val sid = UUID.fromString(record.studentId)
                
                var session = AttendanceSessions.selectAll()
                    .where { (AttendanceSessions.classId eq cid) and (AttendanceSessions.startedAt greaterEq todayStart) }
                    .firstOrNull()
                    
                if (session == null) {
                    val teacherId = Classes.select { Classes.id eq cid }.firstOrNull()?.get(Classes.teacherId) ?: return@map SyncRecordResponse(record.id, "NOT_FOUND", 0, 0, 0)
                    val sessionId = AttendanceSessions.insert {
                        it[this.classId] = cid
                        it[this.teacherId] = teacherId
                        it[sessionStatus] = "ACTIVE"
                        it[checkIntervalMinutes] = 10
                        it[totalChecks] = 5
                        it[minSuccessfulChecks] = 3
                        it[finalCheckRequired] = true
                        it[sessionSecret] = ByteArray(32) { 0 }
                        it[startedAt] = java.time.Instant.ofEpochMilli(record.timestamp)
                        it[reviewDeadline] = java.time.Instant.now().plus(java.time.Duration.ofDays(1))
                    }[AttendanceSessions.id]
                    session = AttendanceSessions.selectAll().where { AttendanceSessions.id eq sessionId }.first()
                }
                    
                val sessionId = session[AttendanceSessions.id]
                val interval = session[AttendanceSessions.checkIntervalMinutes]
                val minChecks = session[AttendanceSessions.minSuccessfulChecks]

                val existingRecord = AttendanceRecords.selectAll()
                    .where { (AttendanceRecords.sessionId eq sessionId) and (AttendanceRecords.studentId eq sid) }
                    .firstOrNull()

                var currentChecks = 1
                var finalStatus = if (record.isPresent) "PRESENT" else "ABSENT"

                if (existingRecord == null) {
                    // First scan
                    if (currentChecks >= minChecks) {
                        finalStatus = "PRESENT"
                    } else {
                        finalStatus = "UNDER_REVIEW"
                    }
                    
                    AttendanceRecords.insert {
                        it[this.sessionId] = sessionId
                        it[this.studentId] = sid
                        it[initialStatus] = "PRESENT"
                        it[this.finalStatus] = finalStatus
                        it[faceVerified] = record.method.contains("FACE")
                        it[successfulChecks] = currentChecks
                        it[createdAt] = java.time.Instant.now()
                        it[updatedAt] = java.time.Instant.now()
                        if (record.method == "FACE_CHECK_OUT") {
                            it[hasCheckoutScan] = true
                        }
                    }
                } else {
                    // Subsequent scans
                    // Don't auto-override if a teacher manually changed it
                    val teacherOverridden = existingRecord[AttendanceRecords.overrideStatus] != null
                    
                    currentChecks = existingRecord[AttendanceRecords.successfulChecks] + 1
                    val newFinalStatus = if (teacherOverridden) {
                        existingRecord[AttendanceRecords.finalStatus] ?: "ABSENT"
                    } else if (currentChecks >= minChecks) {
                        "PRESENT"
                    } else {
                        "UNDER_REVIEW"
                    }
                    finalStatus = newFinalStatus

                    AttendanceRecords.update({ AttendanceRecords.id eq existingRecord[AttendanceRecords.id] }) {
                        it[successfulChecks] = currentChecks
                        it[this.finalStatus] = finalStatus
                        it[lastDetectedAt] = java.time.Instant.now()
                        it[updatedAt] = java.time.Instant.now()
                        if (record.method == "FACE_CHECK_OUT") {
                            it[hasCheckoutScan] = true
                        }
                    }
                }
                
                SyncRecordResponse(
                    id = record.id,
                    status = finalStatus,
                    successfulChecks = currentChecks,
                    requiredChecks = minChecks,
                    nextCheckIntervalMinutes = interval
                )
            }
        }
    }
    
    fun evaluateSessionRecords(sessionId: UUID, forceLock: Boolean = false) {
        transaction {
            val session = AttendanceSessions.selectAll().where { AttendanceSessions.id eq sessionId }.firstOrNull() ?: return@transaction
            val startedAt = session[AttendanceSessions.startedAt]
            val actualEndAt = session[AttendanceSessions.actualEndAt] ?: java.time.Instant.now()
            
            val durationMinutes = java.time.Duration.between(startedAt, actualEndAt).toMinutes()
            val checkInterval = session[AttendanceSessions.checkIntervalMinutes]
            
            val totalPossibleChecks = if (checkInterval > 0) (durationMinutes / checkInterval).toInt() else 0
            val requiredChecks = kotlin.math.max(1, kotlin.math.ceil(totalPossibleChecks * 0.75).toInt())
            
            val records = AttendanceRecords.selectAll().where { AttendanceRecords.sessionId eq sessionId }
            
            for (record in records) {
                if (record[AttendanceRecords.overrideStatus] != null) continue
                
                val successfulChecks = record[AttendanceRecords.successfulChecks]
                var newStatus = "ABSENT"
                var reason: String? = null
                
                if (successfulChecks >= requiredChecks) {
                    newStatus = "PRESENT"
                } else if (successfulChecks > 0) {
                    newStatus = "UNDER_REVIEW"
                    reason = "Only $successfulChecks of $requiredChecks required checks"
                } else {
                    newStatus = "ABSENT"
                    reason = "No checks recorded"
                }
                
                if (forceLock && newStatus == "UNDER_REVIEW") {
                    newStatus = "ABSENT"
                    reason = "Auto-resolved at midnight (offline sync)"
                }
                
                AttendanceRecords.update({ AttendanceRecords.id eq record[AttendanceRecords.id] }) {
                    it[finalStatus] = newStatus
                    it[reviewReason] = reason
                    it[updatedAt] = java.time.Instant.now()
                }
            }
        }
    }
    
    fun getLiveSession(classId: String): LiveSessionResponse {
        return transaction {
            val cid = UUID.fromString(classId)
            val session = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid)  }
                .firstOrNull()
                
            val totalEnrolled = com.attendance.server.models.ClassMembers.selectAll()
                .where { com.attendance.server.models.ClassMembers.classId eq cid }
                .count().toInt()

            if (session == null) {
                return@transaction LiveSessionResponse(totalEnrolled, emptyList())
            }
            
            val sid = session[AttendanceSessions.id]
            
            val presentStudents = AttendanceRecords.innerJoin(Users, onColumn = { AttendanceRecords.studentId }, otherColumn = { Users.id })
                .select(Users.id, Users.name, AttendanceRecords.createdAt, AttendanceRecords.faceVerified, AttendanceRecords.successfulChecks)
                .where { (AttendanceRecords.sessionId eq sid) and (AttendanceRecords.initialStatus eq "PRESENT") }
                .map { row ->
                    LiveStudentResponse(
                        studentId = row[Users.id].toString(),
                        name = row[Users.name],
                        timeVerified = row[AttendanceRecords.createdAt].toEpochMilli(),
                        isFaceVerified = row[AttendanceRecords.faceVerified],
                        successfulChecks = row[AttendanceRecords.successfulChecks]
                    )
                }
                
            LiveSessionResponse(
                totalEnrolled = totalEnrolled,
                presentStudents = presentStudents
            )
        }
    }

    fun overrideStatus(classId: String, studentId: String, status: String, reason: String) {
        transaction {
            val cid = UUID.fromString(classId)
            val sid = UUID.fromString(studentId)
            
            val session = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid)  }
                .firstOrNull() ?: return@transaction
                
            val sessionId = session[AttendanceSessions.id]
            
            val existing = AttendanceRecords.selectAll()
                .where { (AttendanceRecords.sessionId eq sessionId) and (AttendanceRecords.studentId eq sid) }
                .firstOrNull()
                
            if (existing != null) {
                AttendanceRecords.update({ AttendanceRecords.id eq existing[AttendanceRecords.id] }) {
                    it[finalStatus] = status
                    it[overrideStatus] = reason
                    it[updatedAt] = java.time.Instant.now()
                }
            } else {
                AttendanceRecords.insert {
                    it[this.sessionId] = sessionId
                    it[this.studentId] = sid
                    it[initialStatus] = status
                    it[finalStatus] = status
                    it[overrideStatus] = reason
                    it[faceVerified] = false
                    it[successfulChecks] = 0
                    it[createdAt] = java.time.Instant.now()
                    it[updatedAt] = java.time.Instant.now()
                }
            }
        }
    }

    fun getReviews(classId: String): List<ReviewResponse> {
        return transaction {
            val cid = UUID.fromString(classId)
            val session = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid)  }
                .firstOrNull() ?: return@transaction emptyList()
                
            val sid = session[AttendanceSessions.id]
            
            AttendanceRecords.innerJoin(Users, onColumn = { AttendanceRecords.studentId }, otherColumn = { Users.id })
                .select(Users.id, Users.name, AttendanceRecords.id, AttendanceRecords.finalStatus)
                .where { (AttendanceRecords.sessionId eq sid) and (AttendanceRecords.finalStatus neq "PRESENT") and (AttendanceRecords.overrideStatus.isNull()) }
                .map { row ->
                    ReviewResponse(
                        recordId = row[AttendanceRecords.id].toString(),
                        studentId = row[Users.id].toString(),
                        studentName = row[Users.name],
                        issueType = "Status: ${row[AttendanceRecords.finalStatus]}"
                    )
                }
        }
    }
    
    fun getStudentMonthReportCsv(classId: UUID, studentId: UUID, month: String): String {
        return transaction {
            val (yearStr, monthStr) = month.split("-")
            val year = yearStr.toInt()
            val monthInt = monthStr.toInt()
            val startDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)
            val endDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).plusMonths(1).toInstant(java.time.ZoneOffset.UTC)
            
            val records = AttendanceRecords.innerJoin(AttendanceSessions)
                .select(AttendanceSessions.startedAt, AttendanceRecords.finalStatus)
                .where {
                    (AttendanceSessions.classId eq classId) and
                    (AttendanceRecords.studentId eq studentId) and
                    (AttendanceSessions.startedAt greaterEq startDate) and
                    (AttendanceSessions.startedAt less endDate)
                }
                .orderBy(AttendanceSessions.startedAt to SortOrder.ASC)

            val csvBuilder = StringBuilder()
            csvBuilder.append("Date,Status\n")
            
            records.forEach { row ->
                val date = java.time.LocalDateTime.ofInstant(row[AttendanceSessions.startedAt], java.time.ZoneId.of("UTC")).toLocalDate().toString()
                val status = row[AttendanceRecords.finalStatus] ?: "UNKNOWN"
                csvBuilder.append("$date,$status\n")
            }
            
            csvBuilder.toString()
        }
    }

    fun getClassMonthReportCsv(classId: UUID, month: String): String {
        return transaction {
            val (yearStr, monthStr) = month.split("-")
            val year = yearStr.toInt()
            val monthInt = monthStr.toInt()
            val startDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)
            val endDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).plusMonths(1).toInstant(java.time.ZoneOffset.UTC)

            val sessions = AttendanceSessions
                .select(AttendanceSessions.id, AttendanceSessions.startedAt)
                .where {
                    (AttendanceSessions.classId eq classId) and
                    (AttendanceSessions.startedAt greaterEq startDate) and
                    (AttendanceSessions.startedAt less endDate)
                }
                .orderBy(AttendanceSessions.startedAt to SortOrder.ASC)
                .toList()

            val sessionIds = sessions.map { it[AttendanceSessions.id] }
            val dates = sessions.map { java.time.LocalDateTime.ofInstant(it[AttendanceSessions.startedAt], java.time.ZoneId.of("UTC")).toLocalDate().toString() }

            val students = com.attendance.server.models.ClassMembers.innerJoin(Users)
                .select(Users.id, Users.name)
                .where { com.attendance.server.models.ClassMembers.classId eq classId }
                .toList()
                
            val recordsMap = if (sessionIds.isEmpty()) {
                emptyMap()
            } else {
                AttendanceRecords
                    .select(AttendanceRecords.sessionId, AttendanceRecords.studentId, AttendanceRecords.finalStatus)
                    .where { AttendanceRecords.sessionId inList sessionIds }
                    .toList()
                    .groupBy { it[AttendanceRecords.studentId] to it[AttendanceRecords.sessionId] }
            }
            
            val csvBuilder = StringBuilder()
            csvBuilder.append("StudentName")
            dates.forEach { date ->
                csvBuilder.append(",$date")
            }
            csvBuilder.append("\n")
            
            students.forEach { studentRow ->
                val studentId = studentRow[Users.id]
                val studentName = studentRow[Users.name]
                csvBuilder.append(studentName)
                
                sessions.forEach { sessionRow ->
                    val sessionId = sessionRow[AttendanceSessions.id]
                    val record = recordsMap[studentId to sessionId]?.firstOrNull()
                    val status = record?.get(AttendanceRecords.finalStatus) ?: "ABSENT"
                    csvBuilder.append(",$status")
                }
                csvBuilder.append("\n")
            }
            
            csvBuilder.toString()
        }
    }
    
    fun getStudentTodayStatus(classId: UUID, studentId: UUID): String? {
        return transaction {
            val todayStart = java.time.LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            val todayEnd = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS)
                        val record = AttendanceRecords.innerJoin(AttendanceSessions)
                .select(listOf(AttendanceRecords.finalStatus, AttendanceRecords.successfulChecks, AttendanceSessions.minSuccessfulChecks))
                .where {
                    (AttendanceRecords.studentId eq studentId) and
                    (AttendanceSessions.classId eq classId) and
                    (AttendanceSessions.startedAt greaterEq todayStart) and
                    (AttendanceSessions.startedAt less todayEnd)
                }
                .orderBy(AttendanceSessions.startedAt, org.jetbrains.exposed.sql.SortOrder.DESC)
                .firstOrNull()
            
            if (record != null) {
                val status = record[AttendanceRecords.finalStatus]
                val checks = record[AttendanceRecords.successfulChecks]
                val minChecks = record[AttendanceSessions.minSuccessfulChecks]
                "$status (Checks: $checks/$minChecks)"
            } else {
                null
            }
        }
    }

    fun getStudentSummary(studentId: UUID, month: String): StudentSummaryResponse {
        return transaction {
            val (yearStr, monthStr) = month.split("-")
            val year = yearStr.toInt()
            val monthInt = monthStr.toInt()
            val startDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)
            val endDate = java.time.LocalDateTime.of(year, monthInt, 1, 0, 0).plusMonths(1).toInstant(java.time.ZoneOffset.UTC)

            val records = AttendanceRecords.innerJoin(AttendanceSessions)
                .select(AttendanceRecords.finalStatus)
                .where {
                    (AttendanceRecords.studentId eq studentId) and
                    (AttendanceSessions.startedAt greaterEq startDate) and
                    (AttendanceSessions.startedAt less endDate)
                }
                .toList()
                
            var present = 0
            var absent = 0
            records.forEach {
                if (it[AttendanceRecords.finalStatus] == "PRESENT") {
                    present++
                } else if (it[AttendanceRecords.finalStatus] == "ABSENT") {
                    absent++
                }
            }
            StudentSummaryResponse(present, absent)
        }
    }
}

@Serializable
data class StudentSummaryResponse(
    val totalPresent: Int,
    val totalAbsent: Int
)

@Serializable
data class ReviewResponse(
    val recordId: String,
    val studentId: String,
    val studentName: String,
    val issueType: String
)

@Serializable
data class SyncRecordRequest(
    val id: String,
    val classId: String,
    val studentId: String,
    val timestamp: Long,
    val isPresent: Boolean,
    val method: String
)

@Serializable
data class SyncRecordResponse(
    val id: String,
    val status: String,
    val successfulChecks: Int,
    val requiredChecks: Int,
    val nextCheckIntervalMinutes: Int
)

@Serializable
data class LiveSessionResponse(
    val totalEnrolled: Int,
    val presentStudents: List<LiveStudentResponse>
)

@Serializable
data class LiveStudentResponse(
    val studentId: String,
    val name: String,
    val timeVerified: Long,
    val isFaceVerified: Boolean,
    val successfulChecks: Int
)










