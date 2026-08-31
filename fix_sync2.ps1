$content = Get-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt" -Raw
$content = $content -replace "and \(AttendanceSessions\.sessionStatus eq `"ACTIVE`"\)", ""
Set-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt" $content
