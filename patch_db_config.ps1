$content = Get-Content "backend/src/main/kotlin/com/attendance/server/config/DatabaseConfig.kt" -Raw
$content = $content -replace "SyncQueue", "SyncQueue,`n                ApprovalRequests"
Set-Content "backend/src/main/kotlin/com/attendance/server/config/DatabaseConfig.kt" $content
