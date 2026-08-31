
$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"
$newContent = @()
foreach ($line in $content) {
    if ($line -match "import androidx.navigation.NavController") {
        $newContent += $line
        $newContent += "import com.attendance.app.core.network.ReportDownloader"
        continue
    }
    
    if ($line -match "val permissionLauncher = rememberLauncherForActivityResult") {
        $newContent += "    val downloader = remember { ReportDownloader(context) }"
        $newContent += $line
        continue
    }

    if ($line -match "topBar = \{ TopAppBar\(title = \{ Text\(classItem\?\.name \?\: `"Class Details`"\) \}\)") {
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
        $newContent += "                        Icon(androidx.compose.material.icons.filled.Download, contentDescription = `"Download Report`")"
        $newContent += "                    }"
        $newContent += "                }"
        $newContent += "            )"
        $newContent += "        },"
        continue
    }
    
    if ($line -match "navigationIcon = \{") { continue }
    if ($line -match "IconButton\(onClick = \{ navController.popBackStack\(\) \}\)") { continue }
    if ($line -match "Icon\(Icons.AutoMirrored.Filled.ArrowBack, `"Back`"\)") { continue }
    if ($line -match "\} \/\/ end navigationIcon") { continue } # pseudo
    
    $newContent += $line
}
$newContent | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"

