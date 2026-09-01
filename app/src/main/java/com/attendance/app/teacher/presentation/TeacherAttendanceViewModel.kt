package com.attendance.app.teacher.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.TokenManager
import com.attendance.app.ble.advertiser.BleAdvertiserManager
import com.attendance.app.ble.scanner.BleScannerService
import com.attendance.app.core.data.sync.SyncRecordRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TeacherAttendanceViewModel(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager,
    private val attendanceDao: com.attendance.app.core.data.local.room.AttendanceDao
) : ViewModel() {
    
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _activeClassId = MutableStateFlow<String?>(null)
    val activeClassIdFlow: StateFlow<String?> = _activeClassId.asStateFlow()
    val activeClassId: String? get() = _activeClassId.value

    var isStartingSession by mutableStateOf(false)
        private set
        
    var sessionError by mutableStateOf<String?>(null)
        private set
        
    private var scanReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    fun startAttendanceSession(context: Context, classId: String, className: String, interval: Int, minChecks: Int, onStarted: () -> Unit) {
        viewModelScope.launch {
            isStartingSession = true
            sessionError = null
            
            _activeClassId.value = classId
            BleScannerService.start(context, classId)
            
            if (!isReceiverRegistered) {
                scanReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == BleScannerService.ACTION_DEVICE_FOUND) {
                            val payload = intent.getByteArrayExtra(BleScannerService.EXTRA_PAYLOAD) ?: return
                            val studentId = BleAdvertiserManager.extractStudentId(payload) ?: return
                            Log.d("TeacherVM", "Found student broadcast: $studentId")
                            syncStudent(studentId, classId)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(scanReceiver, IntentFilter(BleScannerService.ACTION_DEVICE_FOUND), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(scanReceiver, IntentFilter(BleScannerService.ACTION_DEVICE_FOUND))
                }
                isReceiverRegistered = true
            }

            _isAdvertising.value = true // Keep using the same state name for UI compatibility
            isStartingSession = false
            onStarted()
        }
    }
    
    private fun syncStudent(studentId: String, classId: String) {
        viewModelScope.launch {
            try {
                val recordId = UUID.randomUUID().toString()
                attendanceDao.insertRecord(
                    com.attendance.app.core.data.local.room.AttendanceRecordEntity(
                        id = recordId,
                        classId = classId,
                        studentId = studentId,
                        timestamp = System.currentTimeMillis(),
                        isPresent = true,
                        method = "BLE",
                        syncStatus = "PENDING"
                    )
                )
            } catch (e: Exception) {
                Log.e("TeacherVM", "Failed to sync student $studentId", e)
            }
        }
    }

    fun stopAttendanceSession(context: Context) {
        val classIdToStop = _activeClassId.value
        BleScannerService.stop(context)
        if (isReceiverRegistered && scanReceiver != null) {
            try {
                context.unregisterReceiver(scanReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            isReceiverRegistered = false
        }
        
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.attendance.app.core.data.sync.AttendanceSyncWorker>().build()
        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        
        if (classIdToStop != null) {
            viewModelScope.launch {
                try {
                    val token = tokenManager.getToken()
                    if (token != null) {
                        httpClient.post("${com.attendance.app.core.network.Constants.BASE_URL}/attendance/stop") {

                            contentType(ContentType.Application.Json)
                            setBody(mapOf("classId" to classIdToStop))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TeacherVM", "Failed to stop session on server", e)
                }
            }
        }
        
        _isAdvertising.value = false
        _activeClassId.value = null
    }
}

