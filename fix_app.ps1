
$appModule = Get-Content "app/src/main/java/com/attendance/app/core/di/AppModule.kt"
$appModule = $appModule -replace "viewModel \{ TeacherAttendanceViewModel\(get\(\), get\(\), get\(\)\) \}", "viewModel { TeacherAttendanceViewModel(get(), get()) }"
$appModule = $appModule -replace "viewModel \{ LiveSessionViewModel\(get\(\)\) \}", "viewModel { LiveSessionViewModel(get(), get()) }"
$appModule = $appModule -replace "viewModel \{ TeacherReviewsViewModel\(get\(\)\) \}", "viewModel { TeacherReviewsViewModel(get(), get()) }"
$appModule | Set-Content "app/src/main/java/com/attendance/app/core/di/AppModule.kt"

$navHost = Get-Content "app/src/main/java/com/attendance/app/core/ui/navigation/AppNavHost.kt"
$navHost = $navHost -replace "Screen\.HodHome", "Screen.HodDashboard"
$navHost | Set-Content "app/src/main/java/com/attendance/app/core/ui/navigation/AppNavHost.kt"

$studentClassDetail = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"
$studentClassDetail = $studentClassDetail -replace "androidx\.compose\.material\.icons\.Icons\.Default\.Download", "androidx.compose.material.icons.filled.Download"
$studentClassDetail = "import androidx.compose.material.icons.filled.Download`n" + $studentClassDetail
$studentClassDetail | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentClassDetailScreen.kt"

$studentHome = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentHomeScreen.kt"
$studentHome = $studentHome -replace "cls\.joinCode", "cls.id.substring(0, 5)"
$studentHome = $studentHome -replace "Screen\.StudentClassDetail\(cls\.id, cls\.name\)", "Screen.StudentClassDetail(cls.id)"
$studentHome = "import com.attendance.app.core.ui.components.StudentBottomBar`n" + $studentHome
$studentHome | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentHomeScreen.kt"

$studentProfile = Get-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt"
$studentProfile = "import com.attendance.app.core.ui.components.StudentBottomBar`n" + $studentProfile
$studentProfile | Set-Content "app/src/main/java/com/attendance/app/student/ui/StudentProfileScreen.kt"

