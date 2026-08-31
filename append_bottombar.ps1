
$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentHomeScreen.kt"
$newContent = @()
foreach ($line in $content) {
    if ($line -match "import com.attendance.app.core.ui.components.StudentBottomBar") { continue }
    $newContent += $line
}
$newContent += " "
$newContent += "@Composable"
$newContent += "fun StudentBottomBar(navController: NavController, selectedIndex: Int) {"
$newContent += "    NavigationBar {"
$newContent += "        NavigationBarItem("
$newContent += "            icon = { Icon(Icons.Default.Home, contentDescription = `"Home`") },"
$newContent += "            label = { Text(`"Home`") },"
$newContent += "            selected = selectedIndex == 0,"
$newContent += "            onClick = { navController.navigate(Screen.StudentHome) { popUpTo(0) } }"
$newContent += "        )"
$newContent += "        NavigationBarItem("
$newContent += "            icon = { Icon(Icons.Default.Person, contentDescription = `"Profile`") },"
$newContent += "            label = { Text(`"Profile`") },"
$newContent += "            selected = selectedIndex == 3,"
$newContent += "            onClick = { navController.navigate(Screen.StudentProfile) { popUpTo(0) } }"
$newContent += "        )"
$newContent += "    }"
$newContent += "}"
$newContent | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentHomeScreen.kt"

