
$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"
$newContent = @()
foreach ($line in $content) {
    if ($line -match "val scanStatus by attendanceViewModel") {
        $newContent += $line
        $newContent += "    val todayStatus by attendanceViewModel.todayStatus.collectAsState()"
        $newContent += ""
        $newContent += "    LaunchedEffect(classId) {"
        $newContent += "        attendanceViewModel.fetchTodayStatus(classId)"
        $newContent += "    }"
        continue
    }
    
    if ($line -match "Text\(`"Teacher:") {
        $newContent += $line
        $newContent += "                Spacer(modifier = Modifier.height(16.dp))"
        $newContent += "                if (todayStatus != null) {"
        $newContent += "                    Text(`"Today's Status: `$todayStatus`", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)"
        $newContent += "                } else {"
        $newContent += "                    Text(`"Fetching today's status...`", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)"
        $newContent += "                }"
        continue
    }
    
    $newContent += $line
}
$newContent | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"

