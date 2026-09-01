package com.attendance.app.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.TokenManager
import com.attendance.app.core.network.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import kotlinx.coroutines.flow.combine
import com.attendance.app.core.data.local.room.AttendanceDao
import com.attendance.app.core.data.local.room.StudentDao

class LiveSessionViewModel(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager,
    private val attendanceDao: AttendanceDao,
    private val studentDao: StudentDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveSessionUiState>(LiveSessionUiState.Loading)
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private val _presentStudents = MutableStateFlow<List<StudentPresentInfo>>(emptyList())
    val presentStudents: StateFlow<List<StudentPresentInfo>> = _presentStudents.asStateFlow()

    private val _recheckIntervalMinutes = MutableStateFlow(10)
    val recheckIntervalMinutes: StateFlow<Int> = _recheckIntervalMinutes.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()

    private var activeClassId: String? = null
    
    fun startSession(classId: String, interval: Int, minChecks: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken()
                val response = httpClient.post("${Constants.BASE_URL}/attendance/start") {
                    @kotlinx.serialization.Serializable
                    data class StartSessionRequest(
                        val classId: String,
                        val checkIntervalMinutes: Int,
                        val minSuccessfulChecks: Int
                    )
                    
                    if (token != null) {

                    }
                    contentType(ContentType.Application.Json)
                    setBody(StartSessionRequest(
                        classId = classId,
                        checkIntervalMinutes = interval,
                        minSuccessfulChecks = minChecks
                    ))
                }
                if (response.status.isSuccess()) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

        fun loadSession(classId: String, startTime: Long) {
        activeClassId = classId
        viewModelScope.launch {
            _uiState.value = LiveSessionUiState.Active(
                classId = classId,
                totalEnrolled = 0 // Will update from local roster
            )
            observeOfflineSession(classId, startTime)
        }
    }
    
    private suspend fun observeOfflineSession(classId: String, startTime: Long) {
        combine(
            attendanceDao.getRecordsForClassSince(classId, startTime),
            studentDao.getStudentsForClass(classId)
        ) { records, students ->
            val totalEnrolled = students.size
            if (_uiState.value is LiveSessionUiState.Active) {
                _uiState.value = LiveSessionUiState.Active(
                    classId = classId,
                    totalEnrolled = totalEnrolled
                )
            }
            
            val studentMap = students.associateBy { it.id }
            val groupedRecords = records.groupBy { it.studentId }
            
            groupedRecords.map { entry ->
                val studentId = entry.key
                val studentRecords = entry.value
                val sortedRecords = studentRecords.sortedBy { it.timestamp }
                var currentChecks = 1
                var lastCountedTime = sortedRecords.first().timestamp
                val intervalMs = Math.max(0, (_recheckIntervalMinutes.value - 1) * 60 * 1000L)
                
                for (i in 1 until sortedRecords.size) {
                    val recordTime = sortedRecords[i].timestamp
                    if (recordTime - lastCountedTime >= intervalMs) {
                        currentChecks++
                        lastCountedTime = recordTime
                    }
                }
                
                                val studentName = studentMap[studentId]?.studentName ?: "Unknown Student"
                val manualRecord = studentRecords.find { it.method.startsWith("MANUAL_") }
                val manualStatus = manualRecord?.method?.removePrefix("MANUAL_")
                
                StudentPresentInfo(
                    studentId = studentId,
                    name = studentName,
                    timeVerified = sortedRecords.first().timestamp,
                    isFaceVerified = true,
                    successfulChecks = currentChecks,
                    manualStatus = manualStatus
                )
            }.sortedByDescending { it.timeVerified }
        }.collect { mappedList ->
            if (activeClassId == classId) {
                _presentStudents.value = mappedList
                _lastSyncTime.value = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date())
            }
        }
    }

    fun setRecheckInterval(minutes: Int) {
        if (minutes in 1..60) {
            _recheckIntervalMinutes.value = minutes
        }
    }

    fun endSession(context: android.content.Context, className: String = "Class") {
        activeClassId = null // Stop polling
        _uiState.value = LiveSessionUiState.Ended
        
        val data = androidx.work.Data.Builder()
            .putString("class_name", className)
            .build()
            
        val reminderWork = androidx.work.OneTimeWorkRequestBuilder<com.attendance.app.core.data.sync.FinalizeSessionWorker>()
            .setInitialDelay(30, java.util.concurrent.TimeUnit.MINUTES)
            .setInputData(data)
            .build()
            
        androidx.work.WorkManager.getInstance(context).enqueue(reminderWork)
    }
}

sealed class LiveSessionUiState {
    object Loading : LiveSessionUiState()
    data class Active(val classId: String, val totalEnrolled: Int) : LiveSessionUiState()
    object Ended : LiveSessionUiState()
}

data class StudentPresentInfo(
    val studentId: String,
    val name: String,
    val timeVerified: Long,
    val isFaceVerified: Boolean,
    val successfulChecks: Int,
    val manualStatus: String? = null
)

@Serializable
data class LiveSessionResponse(
    val totalEnrolled: Int,
    val presentStudents: List<LiveStudentResponse>
)

@Serializable
data class LiveStudentResponse(
    val studentId: String,
    val name: String,
    val timeVerified: Long,
    val isFaceVerified: Boolean,
    val successfulChecks: Int
)








