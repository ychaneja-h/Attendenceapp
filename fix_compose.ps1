$content = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt" -Raw
$content = $content -replace "androidx\.compose\.ui\.draw\.clip\(androidx\.compose\.foundation\.shape\.CircleShape\)", "clip(androidx.compose.foundation.shape.CircleShape)"
$content = $content -replace "androidx\.compose\.foundation\.background\(MaterialTheme\.colorScheme\.surfaceVariant\)", "background(MaterialTheme.colorScheme.surfaceVariant)"
$content = $content -replace "androidx\.activity\.result\.PickVisualMediaRequest\(androidx\.activity\.result\.contract\.ActivityResultContracts\.PickVisualMedia\.ImageOnly\)", "androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)"
$content = $content -replace "androidx\.compose\.foundation\.background\(MaterialTheme\.colorScheme\.primary, androidx\.compose\.foundation\.shape\.CircleShape\)", "background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)"
$content = $content -replace "androidx\.compose\.material\.icons\.Icons\.Default\.androidx\.compose\.material\.icons\.filled\.Edit", "androidx.compose.material.icons.Icons.Default.Edit"
$content = "import androidx.compose.ui.draw.clip`nimport androidx.compose.foundation.background`nimport androidx.compose.material.icons.filled.Edit`n" + $content
Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt" $content
