using System;
using System.IO;
using System.Text.RegularExpressions;

string content = File.ReadAllText("backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt");

string evaluateFunction = @"
    fun evaluateSessionRecords(sessionId: UUID, forceLock: Boolean = false) {
        transaction {
            val session = AttendanceSessions.selectAll().where { AttendanceSessions.id eq sessionId }.firstOrNull() ?: return@transaction
            val startedAt = session[AttendanceSessions.startedAt]
            val endedAt = session[AttendanceSessions.endedAt] ?: java.time.Instant.now()
            
            val durationMinutes = java.time.Duration.between(startedAt, endedAt).toMinutes()
            val checkInterval = session[AttendanceSessions.checkIntervalMinutes]
            
            val totalPossibleChecks = if (checkInterval > 0) (durationMinutes / checkInterval).toInt() else 0
            val requiredChecks = kotlin.math.max(1, kotlin.math.ceil(totalPossibleChecks * 0.75).toInt())
            
            val records = AttendanceRecords.selectAll().where { AttendanceRecords.sessionId eq sessionId }
            
            for (record in records) {
                if (record[AttendanceRecords.overrideStatus] != null) continue
                
                val successfulChecks = record[AttendanceRecords.successfulChecks]
                var newStatus = ""ABSENT""
                var reason: String? = null
                
                if (successfulChecks >= requiredChecks) {
                    newStatus = ""PRESENT""
                } else if (successfulChecks > 0) {
                    newStatus = ""UNDER_REVIEW""
                    reason = ""Only  of  required checks""
                } else {
                    newStatus = ""ABSENT""
                    reason = ""No checks recorded""
                }
                
                if (forceLock && newStatus == ""UNDER_REVIEW"") {
                    newStatus = ""ABSENT""
                    reason = ""Auto-resolved at midnight (offline sync)""
                }
                
                AttendanceRecords.update({ AttendanceRecords.id eq record[AttendanceRecords.id] }) {
                    it[finalStatus] = newStatus
                    it[reviewReason] = reason
                    it[updatedAt] = java.time.Instant.now()
                }
            }
        }
    }
";

// Insert evaluateSessionRecords before getLiveSession
content = content.Replace("    fun getLiveSession(classId: String)", evaluateFunction + "\n    fun getLiveSession(classId: String)");

File.WriteAllText("backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt", content);
