$content = Get-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherHomeScreen.kt" -Raw
$content = $content -replace "(?m)^\s*if \(selectedClassId", "`n    }`n`n    if (selectedClassId"
Set-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherHomeScreen.kt" $content
