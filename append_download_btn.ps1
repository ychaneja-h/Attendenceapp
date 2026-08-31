
$content = Get-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherClassDetailScreen.kt"
$newContent = @()
foreach ($line in $content) {
    if ($line -match "import androidx.navigation.NavController") {
        $newContent += $line
        $newContent += "import com.attendance.app.core.network.ReportDownloader"
        continue
    }
    
    if ($line -match "val context = androidx.compose.ui.platform.LocalContext.current") {
        $newContent += $line
        $newContent += "    val downloader = remember { ReportDownloader(context) }"
        continue
    }
    
    if ($line -match "topBar = \{") {
        $newContent += "        topBar = {"
        $newContent += "            TopAppBar("
        $newContent += "                title = { Text(classItem?.name ?: `"Class Details`") },"
        $newContent += "                navigationIcon = {"
        $newContent += "                    IconButton(onClick = { navController.popBackStack() }) {"
        $newContent += "                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = `"Back`")"
        $newContent += "                    }"
        $newContent += "                },"
        $newContent += "                actions = {"
        $newContent += "                    IconButton(onClick = { downloader.downloadClassReport(classId, java.time.YearMonth.now().toString()) }) {"
        $newContent += "                        Icon(Icons.Default.Download, contentDescription = `"Download Report`")"
        $newContent += "                    }"
        $newContent += "                    IconButton(onClick = { classViewModel.deleteClass(classId) { navController.popBackStack() } }) {"
        $newContent += "                        Icon(Icons.Default.Delete, contentDescription = `"Delete Class`")"
        $newContent += "                    }"
        $newContent += "                }"
        $newContent += "            )"
        $newContent += "        },"
        continue
    }
    if ($line -match "IconButton\(onClick = \{ classViewModel.deleteClass") {
        # skip next few lines
        continue
    }
    if ($line -match "Icon\(Icons.Default.Delete") { continue }
    if ($line -match "\} \/\/ end actions") { continue } # pseudo regex
    
    if ($line -match "Text\(student.name") {
        $newContent += $line
        $newContent += "                                        IconButton(onClick = { downloader.downloadStudentReport(classId, student.id, java.time.YearMonth.now().toString()) }) {"
        $newContent += "                                            Icon(Icons.Default.Download, contentDescription = `"Download Student Report`")"
        $newContent += "                                        }"
        continue
    }
    
    $newContent += $line
}
$newContent | Set-Content "app/src/main/java/com/attendance/app/teacher/ui/TeacherClassDetailScreen.kt"

