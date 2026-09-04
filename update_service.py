import re

with open('backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt', 'r') as f:
    content = f.read()

# 1. Update stopSession(classId: String)
stopSessionClassIdOld = '''    fun stopSession(classId: String) {
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
    }'''

stopSessionClassIdNew = '''    fun stopSession(classId: String) {
        transaction {
            val cid = UUID.fromString(classId)
            val session = AttendanceSessions.selectAll()
                .where { (AttendanceSessions.classId eq cid) and (AttendanceSessions.sessionStatus eq "ACTIVE") }
                .firstOrNull() ?: return@transaction
                
            val sessionId = session[AttendanceSessions.id]
            
            AttendanceSessions.update({ AttendanceSessions.id eq sessionId }) {
                it[sessionStatus] = "COMPLETED"
                it[endedAt] = java.time.Instant.now()
            }
            
            evaluateSessionRecords(sessionId)
        }
    }'''
content = content.replace(stopSessionClassIdOld, stopSessionClassIdNew)

# 2. Update stopSession(sessionId: UUID)
stopSessionSessionIdOld = '''    fun stopSession(sessionId: UUID) {
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
                    reason = "Only  of  checks recorded"
                } else if (successfulChecks > 0 && successfulChecks < minChecks && !hasCheckoutScan) {
                    newStatus = "UNDER_REVIEW"
                    reason = "Only  of  checks, final face not scanned"
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
    }'''

stopSessionSessionIdNew = '''    fun stopSession(sessionId: UUID) {
        transaction {
            val session = AttendanceSessions.selectAll()
                .where { AttendanceSessions.id eq sessionId }
                .firstOrNull() ?: return@transaction
                
            AttendanceSessions.update({ AttendanceSessions.id eq sessionId }) {
                it[sessionStatus] = "COMPLETED"
                it[endedAt] = java.time.Instant.now()
            }
            
            evaluateSessionRecords(sessionId)
        }
    }'''
content = content.replace(stopSessionSessionIdOld, stopSessionSessionIdNew)

# 3. Update syncRecords
syncRecordsOld = '''    fun syncRecords(records: List<SyncRecordRequest>): List<SyncRecordResponse> {
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
    }'''

syncRecordsNew = '''    fun syncRecords(records: List<SyncRecordRequest>): List<SyncRecordResponse> {
        return transaction {
            val todayStart = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
            
            // Auto-lock past sessions
            val pastSessions = AttendanceSessions.selectAll().where { AttendanceSessions.startedAt less todayStart }
            val pastSessionIds = pastSessions.map { it[AttendanceSessions.id] }
            if (pastSessionIds.isNotEmpty()) {
                AttendanceRecords.update({ (AttendanceRecords.sessionId inList pastSessionIds) and (AttendanceRecords.finalStatus eq "UNDER_REVIEW") }) {
                    it[finalStatus] = "ABSENT"
                    it[reviewReason] = "Auto-resolved at midnight (offline sync)"
                }
                AttendanceSessions.update({ AttendanceSessions.id inList pastSessionIds }) {
                    it[sessionStatus] = "COMPLETED"
                }
            }

            val touchedSessionIds = mutableSetOf<UUID>()

            val responses = records.map { record ->
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
                touchedSessionIds.add(sessionId)
                val interval = session[AttendanceSessions.checkIntervalMinutes]
                
                // Update endedAt to reflect the latest scan time
                val recordTime = java.time.Instant.ofEpochMilli(record.timestamp)
                val currentEndedAt = session[AttendanceSessions.endedAt]
                if (currentEndedAt == null || recordTime.isAfter(currentEndedAt)) {
                    AttendanceSessions.update({ AttendanceSessions.id eq sessionId }) {
                        it[endedAt] = recordTime
                    }
                }

                val existingRecord = AttendanceRecords.selectAll()
                    .where { (AttendanceRecords.sessionId eq sessionId) and (AttendanceRecords.studentId eq sid) }
                    .firstOrNull()

                var currentChecks = 1
                var finalStatus = "UNDER_REVIEW"

                if (existingRecord == null) {
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
                    currentChecks = existingRecord[AttendanceRecords.successfulChecks] + 1
                    
                    AttendanceRecords.update({ AttendanceRecords.id eq existingRecord[AttendanceRecords.id] }) {
                        it[successfulChecks] = currentChecks
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
                    requiredChecks = 0,
                    nextCheckIntervalMinutes = interval
                )
            }
            
            // Re-evaluate all touched sessions dynamically based on their newly calculated duration
            for (sid in touchedSessionIds) {
                // If the session is from a past day, forceLock it to resolve UNDER_REVIEW to ABSENT
                val s = AttendanceSessions.selectAll().where { AttendanceSessions.id eq sid }.firstOrNull()
                val forceLock = s != null && s[AttendanceSessions.startedAt].isBefore(todayStart)
                evaluateSessionRecords(sid, forceLock)
            }
            
            responses
        }
    }'''

content = content.replace(syncRecordsOld, syncRecordsNew)

with open('backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt', 'w') as f:
    f.write(content)
print("Updated successfully")
