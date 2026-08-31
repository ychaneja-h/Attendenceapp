$content = Get-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt" -Raw
$content = $content -replace "it\[sessionSecret\] = java.util.UUID.randomUUID\(\).toString\(\)", "it[sessionSecret] = ByteArray(32)"
Set-Content "backend/src/main/kotlin/com/attendance/server/services/AttendanceService.kt" $content
