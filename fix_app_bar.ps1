
$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"
$newContent = @()
$skip = $false
foreach ($line in $content) {
    if ($line -match "topBar = \{") {
        $skip = $true
        $newContent += "        topBar = {"
        $newContent += "            TopAppBar("
        $newContent += "                title = { Text(classItem?.name ?: `"Class Details`") },"
        $newContent += "                navigationIcon = {"
        $newContent += "                    IconButton(onClick = { navController.popBackStack() }) {"
        $newContent += "                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = `"Back`")"
        $newContent += "                    }"
        $newContent += "                },"
        $newContent += "                actions = {"
        $newContent += "                    IconButton(onClick = { "
        $newContent += "                        val tokenManager = org.koin.java.KoinJavaComponent.getKoin().get<com.attendance.app.auth.data.TokenManager>()"
        $newContent += "                        val studentId = tokenManager.getUserId() ?: return@IconButton"
        $newContent += "                        downloader.downloadStudentReport(classId, studentId, java.time.YearMonth.now().toString()) "
        $newContent += "                    }) {"
        $newContent += "                        Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = `"Download Report`")"
        $newContent += "                    }"
        $newContent += "                }"
        $newContent += "            )"
        $newContent += "        },"
        continue
    }
    
    if ($skip) {
        if ($line -match "\} \/\/ end topBar" -or $line -match "\) \{ padding ->") {
            $skip = $false
            if ($line -match "\) \{ padding ->") {
                $newContent += $line
            }
        }
        continue
    }
    
    $newContent += $line
}
$newContent | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"

