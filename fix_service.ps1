
$content = Get-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt"
$newContent = @()
$skip = 0
foreach ($line in $content) {
    if ($line -match "fun getStudentSummary\(studentId: UUID, month: String\): StudentSummaryResponse \{") {
        $newContent += "    fun getStudentSummary(studentId: UUID, month: String): StudentSummaryResponse {"
        $skip = 16
        continue
    }
    if ($skip -gt 0) {
        $skip = $skip - 1
        continue
    }
    $newContent += $line
}
$newContent | Set-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt"

