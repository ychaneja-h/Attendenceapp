
$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentHomeScreen.kt" -Raw
$content = $content -replace " ", "`r`n"
# No wait, it merged with spaces, so I lost all line breaks!

