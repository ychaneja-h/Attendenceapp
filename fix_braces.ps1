$content = Get-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherHomeScreen.kt" -Raw
$content = $content -replace "(?m)^\s+if \(selectedClassId != null && selectedClassName != null\) \{", "            }`n        }`n    }`n    if (selectedClassId != null && selectedClassName != null) {"
Set-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherHomeScreen.kt" $content
