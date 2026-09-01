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

class LiveSessionViewModel(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager
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

    fun loadSession(classId: String) {
        activeClassId = classId
        viewModelScope.launch {
            _uiState.value = LiveSessionUiState.Active(
                classId = classId,
                totalEnrolled = 0 // Will update on poll
            )
            pollLiveSession(classId)
        }
    }
    
    private suspend fun pollLiveSession(classId: String) {
        while (viewModelScope.isActive && activeClassId == classId) {
            try {
                val token = tokenManager.getToken()
                val response = httpClient.get("${Constants.BASE_URL}/attendance/live/$classId") {
                    if (token != null) {

                    }
                }
                
                if (response.status.isSuccess()) {
                    val data = response.body<LiveSessionResponse>()
                    _presentStudents.value = data.presentStudents.map {
                        StudentPresentInfo(
                            studentId = it.studentId,
                            name = it.name,
                            timeVerified = it.timeVerified,
                            isFaceVerified = it.isFaceVerified,
                            successfulChecks = it.successfulChecks
                        )
                    }
                    if (_uiState.value is LiveSessionUiState.Active) {
                        _uiState.value = LiveSessionUiState.Active(
                            classId = classId,
                            totalEnrolled = data.totalEnrolled
                        )
                    }
                    _lastSyncTime.value = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault()).format(java.util.Date())
                }
            } catch (e: Exception) {
                // Ignore poll errors, retry next loop
            }
            delay(3000) // Poll every 3 seconds for demo purposes
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
    val successfulChecks: Int
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

