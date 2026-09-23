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
    
    fun canReadReport(actorId: String, classId: String, studentId: String? = null): Boolean = transaction {
        val actor = UUID.fromString(actorId)
        val cid = UUID.fromString(classId)
        val owner = Classes.selectAll().where { Classes.id eq cid }.firstOrNull()?.get(Classes.teacherId)
        owner == actor || (studentId == actorId && com.attendance.server.models.ClassMembers.selectAll()
            .where { (com.attendance.server.models.ClassMembers.classId eq cid) and (com.attendance.server.models.ClassMembers.studentId eq actor) }.any())
    }

    fun getStudentMonthReportCsv(classId: UUID, studentId: UUID, month: String): String = transaction {
        val bounds = monthBounds(month)
        val legacy = AttendanceRecords.innerJoin(AttendanceSessions)
            .select(AttendanceSessions.id, AttendanceSessions.startedAt, AttendanceRecords.finalStatus)
            .where { (AttendanceSessions.classId eq classId) and (AttendanceRecords.studentId eq studentId) and
                (AttendanceSessions.startedAt greaterEq bounds.first) and (AttendanceSessions.startedAt less bounds.second) }
            .map { listOf(it[AttendanceSessions.id].toString(), it[AttendanceSessions.startedAt].toString(), it[AttendanceRecords.finalStatus] ?: "ABSENT", "false", "", "", "Legacy attendance") }
        val owner = Classes.selectAll().where { Classes.id eq classId }.single()[Classes.teacherId].toString()
        val current = PlanAAttendanceService().sessions(owner, classId.toString())
            .filter { it.startedAtMs >= bounds.first.toEpochMilli() && it.startedAtMs < bounds.second.toEpochMilli() }
            .mapNotNull { session -> session.students.firstOrNull { it.studentId == studentId.toString() }?.let { row ->
                listOf(session.sessionId, java.time.Instant.ofEpochMilli(session.startedAtMs).toString(), row.status,
                    row.provisional.toString(), row.successfulChecks.toString(), row.totalChecks.toString(),
                    listOfNotNull(row.overrideReason).plus(row.reasons).joinToString("; "))
            } }
        buildString {
            append("SessionId,StartedAtUtc,Status,Provisional,ChecksPassed,TotalChecks,Reasons\n")
            (legacy + current).sortedBy { it[1] }.forEach { append(it.joinToString(",", transform = ::csvCell)).append('\n') }
        }
    }

    fun getClassMonthReportCsv(classId: UUID, month: String): String = transaction {
        val bounds = monthBounds(month)
        val legacy = AttendanceSessions.selectAll()
            .where { (AttendanceSessions.classId eq classId) and (AttendanceSessions.startedAt greaterEq bounds.first) and (AttendanceSessions.startedAt less bounds.second) }
            .map { it[AttendanceSessions.id] to it[AttendanceSessions.startedAt] }
        val owner = Classes.selectAll().where { Classes.id eq classId }.single()[Classes.teacherId].toString()
        val current = PlanAAttendanceService().sessions(owner, classId.toString())
            .filter { it.startedAtMs >= bounds.first.toEpochMilli() && it.startedAtMs < bounds.second.toEpochMilli() }
        val roster = com.attendance.server.models.ClassMembers.innerJoin(Users)
            .select(Users.id, Users.name, Users.collegeId)
            .where { com.attendance.server.models.ClassMembers.classId eq classId }
            .associate { it[Users.id].toString() to (it[Users.name] to it[Users.collegeId]) }.toMutableMap()
        current.flatMap { it.students }.forEach { roster[it.studentId] = it.name to it.collegeId }
        val legacyIds = legacy.map { it.first }
        val oldRows = if (legacyIds.isEmpty()) emptyMap() else AttendanceRecords.selectAll()
            .where { AttendanceRecords.sessionId inList legacyIds }
            .associate { (it[AttendanceRecords.studentId].toString() to it[AttendanceRecords.sessionId].toString()) to (it[AttendanceRecords.finalStatus] ?: "ABSENT") }
        val newRows = current.flatMap { session -> session.students.map { (it.studentId to session.sessionId) to
            (it.status + if (it.provisional) " (provisional)" else "") } }.toMap()
        val currentSessionIds = current.map { it.sessionId }.toSet()
        val sessions = (legacy.map { it.first.toString() to it.second } + current.map { it.sessionId to java.time.Instant.ofEpochMilli(it.startedAtMs) }).sortedBy { it.second }
        buildString {
            append("StudentId,StudentName,CollegeId")
            sessions.forEach { append(',').append(csvCell("${it.second} [${it.first}]")) }
            append('\n')
            roster.entries.sortedBy { it.value.first }.forEach { (studentId, name) ->
                append(listOf(studentId, name.first, name.second).joinToString(",", transform = ::csvCell))
                sessions.forEach { append(',').append(csvCell(newRows[studentId to it.first] ?: oldRows[studentId to it.first]
                    ?: if (it.first in currentSessionIds) "NOT_ENROLLED" else "ABSENT")) }
                append('\n')
            }
        }
    }

    private fun monthBounds(month: String): Pair<java.time.Instant, java.time.Instant> {
        val parsed = java.time.YearMonth.parse(month)
        return parsed.atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC) to parsed.plusMonths(1).atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
    }

    private fun csvCell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    fun getStudentTodayStatus(classId: UUID, studentId: UUID, includeChecks: Boolean = true): String? {
        return transaction {
            val todayStart = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
            val todayEnd = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS)
            val current = PlanAAttendanceService().sessions(studentId.toString(), classId.toString())
                .firstOrNull { it.startedAtMs >= todayStart.toEpochMilli() && it.startedAtMs < todayEnd.toEpochMilli() &&
                    it.students.any { row -> row.studentId == studentId.toString() } }
            val legacySession = AttendanceSessions.selectAll()
                .where {
                    (AttendanceSessions.classId eq classId) and
                    (AttendanceSessions.startedAt greaterEq todayStart) and
                    (AttendanceSessions.startedAt less todayEnd)
                }
                .orderBy(AttendanceSessions.startedAt, org.jetbrains.exposed.sql.SortOrder.DESC)
                .firstOrNull()
            if (current != null && (legacySession == null || current.startedAtMs >= legacySession[AttendanceSessions.startedAt].toEpochMilli())) {
                val row = current.students.single { it.studentId == studentId.toString() }
                return@transaction if (includeChecks) "${row.status} (Checks: ${row.successfulChecks}/${row.totalChecks}${if (row.provisional) ", provisional" else ""})" else row.status
            }
            if (legacySession == null) return@transaction null
            val record = AttendanceRecords.selectAll().where {
                (AttendanceRecords.studentId eq studentId) and (AttendanceRecords.sessionId eq legacySession[AttendanceSessions.id])
            }.firstOrNull()
            val status = record?.get(AttendanceRecords.finalStatus) ?: "ABSENT"
            val checks = record?.get(AttendanceRecords.successfulChecks) ?: 0
            val minChecks = legacySession[AttendanceSessions.minSuccessfulChecks]
            if (includeChecks) "$status (Checks: $checks/$minChecks)" else status
        }
    }

    fun getStudentSummary(studentId: UUID, month: String): StudentSummaryResponse {
        return transaction {
            val (startDate, endDate) = monthBounds(month)

            val records = AttendanceRecords.innerJoin(AttendanceSessions)
                .select(AttendanceRecords.finalStatus)
                .where {
                    (AttendanceRecords.studentId eq studentId) and
                    (AttendanceSessions.startedAt greaterEq startDate) and
                    (AttendanceSessions.startedAt less endDate)
                }
                .map { it[AttendanceRecords.finalStatus] }
            val current = PlanAAttendanceService().sessions(studentId.toString(), null)
                .filter { it.startedAtMs >= startDate.toEpochMilli() && it.startedAtMs < endDate.toEpochMilli() }
                .flatMap { it.students }.filter { it.studentId == studentId.toString() }.map { it.status }
            val statuses = records + current
            StudentSummaryResponse(statuses.count { it == "PRESENT" }, statuses.count { it == "ABSENT" })
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
