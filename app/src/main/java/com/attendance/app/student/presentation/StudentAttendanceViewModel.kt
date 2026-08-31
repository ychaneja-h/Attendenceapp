package com.attendance.app.student.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.TokenManager
import com.attendance.app.ble.advertiser.BleAdvertiserService
import com.attendance.app.core.data.local.room.AttendanceDao
import com.attendance.app.core.data.local.room.AttendanceRecordEntity
import com.attendance.app.core.network.Constants
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class StudentAttendanceViewModel(
    private val attendanceDao: AttendanceDao,
    private val tokenManager: TokenManager,
    private val httpClient: HttpClient
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _scanStatus = MutableStateFlow<String>("Ready")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val _todayStatus = MutableStateFlow<String?>(null)
    val todayStatus: StateFlow<String?> = _todayStatus.asStateFlow()

    private val _attendanceAction = MutableStateFlow<AttendanceAction?>(null)
    val attendanceAction: StateFlow<AttendanceAction?> = _attendanceAction.asStateFlow()
    
    private var pulseJob: Job? = null
    
    private var pendingCheckOut = false
    
    private fun getSharedPrefs(context: Context) = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

    fun checkIn(context: Context, classId: String) {
        val prefs = getSharedPrefs(context)
        val activeClassId = prefs.getString("active_class_id", null)
        
        if (activeClassId != null && activeClassId != classId) {
            _scanStatus.value = "You are already active in another class."
            return
        }
        
        _attendanceAction.value = AttendanceAction.RequireFaceVerification(classId)
    }

    fun onFaceVerificationSuccess(classId: String, context: Context, isCheckOut: Boolean = false) {
        viewModelScope.launch {
            val studentId = tokenManager.getUserId() ?: return@launch
            
            val actualIsCheckOut = isCheckOut || pendingCheckOut
            pendingCheckOut = false
            
            if (!actualIsCheckOut) {
                // Lock to this class
                getSharedPrefs(context).edit().putString("active_class_id", classId).apply()
                
                val record = AttendanceRecordEntity(
                    id = UUID.randomUUID().toString(),
                    classId = classId,
                    studentId = studentId,
                    timestamp = System.currentTimeMillis(),
                    isPresent = true,
                    method = "BLE+FACE",
                    syncStatus = "PENDING"
                )
                attendanceDao.insertRecord(record)
                
                _scanStatus.value = "Attendance System Active. You are checked in."
                _attendanceAction.value = AttendanceAction.Completed
                
                startPulseAndPoll(context, classId, studentId)
            } else {
                val record = AttendanceRecordEntity(
                    id = UUID.randomUUID().toString(),
                    classId = classId,
                    studentId = studentId,
                    timestamp = System.currentTimeMillis(),
                    isPresent = true,
                    method = "FACE_CHECK_OUT",
                    syncStatus = "PENDING"
                )
                attendanceDao.insertRecord(record)
                
                pulseJob?.cancel()
                getSharedPrefs(context).edit().remove("active_class_id").apply()
                BleAdvertiserService.stop(context)
                
                _scanStatus.value = "Session Left"
                _todayStatus.value = null
                _attendanceAction.value = AttendanceAction.Completed
            }
        }
    }
    
    private fun startPulseAndPoll(context: Context, classId: String, studentId: String) {
        pulseJob?.cancel()
        pulseJob = viewModelScope.launch {
            while (isActive) {
                // Pulse BLE Advertiser for 10 seconds
                BleAdvertiserService.start(context, classId, studentId, "Class")
                
                // Fast poll for status
                try {
                    val token = tokenManager.getToken()
                    if (token != null) {
                        val response = httpClient.get("${Constants.BASE_URL}/attendance/class/$classId/student/today") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                        if (response.status.isSuccess()) {
                            val body: Map<String, String?> = response.body()
                            _todayStatus.value = body["status"]
                        } else {
                            _todayStatus.value = "Offline / Syncing..."
                        }
                    }
                } catch (e: Exception) {
                    _todayStatus.value = "Offline / Syncing..."
                }
                
                // Wait 5 minutes before next pulse
                delay(5 * 60 * 1000L)
            }
        }
    }
    
    fun initiateCheckOut(classId: String) {
        pendingCheckOut = true
        _attendanceAction.value = AttendanceAction.RequireFaceVerification(classId, isCheckOut = true)
    }

    fun fetchTodayStatus(classId: String) {
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken() ?: return@launch
                val response = httpClient.get("${Constants.BASE_URL}/attendance/class/$classId/student/today") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status.isSuccess()) {
                    val body: Map<String, String?> = response.body()
                    _todayStatus.value = body["status"]
                }
            } catch (e: Exception) {
                _todayStatus.value = "Error"
            }
        }
    }

    fun resetAction() {
        _attendanceAction.value = null
    }
}

sealed class AttendanceAction {
    data class RequireFaceVerification(val classId: String, val isCheckOut: Boolean = false) : AttendanceAction()
    object Completed : AttendanceAction()
}
