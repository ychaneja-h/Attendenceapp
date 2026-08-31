$content = Get-Content "backend/src/main/kotlin/com/attendance/server/models/Tables.kt" -Raw
$content = $content -replace "    val academicYear = varchar\(`"academic_year`", 20\)\.nullable\(\)\r?\n    val createdAt = timestamp\(`"created_at`"\)", "    val academicYear = varchar(`"academic_year`", 20).nullable()`n    val deviceId = varchar(`"device_id`", 255).nullable()`n    val faceEmbedding = text(`"face_embedding`").nullable()`n    val createdAt = timestamp(`"created_at`")"
Set-Content "backend/src/main/kotlin/com/attendance/server/models/Tables.kt" $content
