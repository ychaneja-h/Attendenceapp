$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt" -Raw
$content = $content -replace "import androidx\.compose\.foundation\.verticalScroll", "import androidx.compose.foundation.verticalScroll`nimport androidx.activity.compose.rememberLauncherForActivityResult`nimport androidx.activity.result.contract.ActivityResultContracts"
Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt" $content
