
$content = Get-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt"
$newContent = @()
foreach ($line in $content) {
    $newContent += $line
    if ($line -match "fun getStudentSummary") {
        $newContent += "    fun getStudentTodayStatus(classId: UUID, studentId: UUID): String? {"
        $newContent += "        return transaction {"
        $newContent += "            val todayStart = java.time.LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)"
        $newContent += "            val todayEnd = todayStart.plus(1, java.time.temporal.ChronoUnit.DAYS)"
        $newContent += "            val record = AttendanceRecords.innerJoin(AttendanceSessions)"
        $newContent += "                .select(AttendanceRecords.finalStatus)"
        $newContent += "                .where {"
        $newContent += "                    (AttendanceRecords.studentId eq studentId) and"
        $newContent += "                    (AttendanceSessions.classId eq classId) and"
        $newContent += "                    (AttendanceSessions.startedAt greaterEq todayStart) and"
        $newContent += "                    (AttendanceSessions.startedAt less todayEnd)"
        $newContent += "                }"
        $newContent += "                .orderBy(AttendanceSessions.startedAt, org.jetbrains.exposed.sql.SortOrder.DESC)"
        $newContent += "                .firstOrNull()"
        $newContent += "            record?[AttendanceRecords.finalStatus]"
        $newContent += "        }"
        $newContent += "    }"
        $newContent += ""
    }
}
$newContent | Set-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt"

