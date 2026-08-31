
$content = Get-Content "app/src/main/java/com/attendance/app/student/presentation/StudentAttendanceViewModel.kt"
$newContent = @()
foreach ($line in $content) {
    if ($line -match "import kotlinx.coroutines.flow.MutableStateFlow") {
        $newContent += "import io.ktor.client.request.get"
        $newContent += "import io.ktor.client.request.header"
        $newContent += "import io.ktor.client.call.body"
        $newContent += "import io.ktor.http.HttpHeaders"
        $newContent += "import io.ktor.http.isSuccess"
        $newContent += "import com.attendance.app.core.network.Constants"
    }
    $newContent += $line
    
    if ($line -match "val scanStatus: StateFlow<String> = _scanStatus") {
        $newContent += "    private val _todayStatus = MutableStateFlow<String?>(null)"
        $newContent += "    val todayStatus: StateFlow<String?> = _todayStatus"
        $newContent += ""
        $newContent += "    fun fetchTodayStatus(classId: String) {"
        $newContent += "        viewModelScope.launch {"
        $newContent += "            try {"
        $newContent += "                val token = tokenManager.getToken() ?: return@launch"
        $newContent += "                val response = httpClient.get(\"`${Constants.BASE_URL}/attendance/class/`$classId/student/today\") {"
        $newContent += "                    header(HttpHeaders.Authorization, `"Bearer `$token`")"
        $newContent += "                }"
        $newContent += "                if (response.status.isSuccess()) {"
        $newContent += "                    val body: Map<String, String?> = response.body()"
        $newContent += "                    _todayStatus.value = body[`"status`"]"
        $newContent += "                }"
        $newContent += "            } catch (e: Exception) {"
        $newContent += "                _todayStatus.value = `"Error`""
        $newContent += "            }"
        $newContent += "        }"
        $newContent += "    }"
    }
}
$newContent | Set-Content "app/src/main/java/com/attendance/app/student/presentation/StudentAttendanceViewModel.kt"

