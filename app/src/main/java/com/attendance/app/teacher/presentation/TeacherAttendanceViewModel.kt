package com.attendance.app.teacher.presentation

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.ble.scanner.BleScannerService
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TeacherAttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {
    val state = repository.state
    val activeClassIdFlow = state.map { it.activeTeacherSession?.classId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeClassId: String? get() = state.value.activeTeacherSession?.classId
    val activeSessionStartTime = state.map { it.activeTeacherSession?.startedAtMs ?: 0L }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val isAdvertising = state.map { it.activeTeacherSession != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    var isStartingSession by mutableStateOf(false)
        private set
    var isStoppingSession by mutableStateOf(false)
        private set
    var sessionError by mutableStateOf<String?>(null)
        private set
    init { refresh() }
    fun refresh() = repository.refresh()
    fun startAttendanceSession(context: Context, classId: String, className: String, interval: Int = 5, minChecks: Int = 0, onStarted: () -> Unit) {
        viewModelScope.launch {
            isStartingSession = true
            sessionError = null
            try {
                repository.awaitInitialization()
                repository.startTeacherSession(classId, className)
                BleScannerService.start(context.applicationContext, classId)
                onStarted()
            } catch (error: Exception) { sessionError = error.message ?: "Unable to start attendance." }
            finally { isStartingSession = false }
        }
    }
    fun stopAttendanceSession(context: Context) {
        if (isStoppingSession) return
        isStoppingSession = true
        sessionError = null
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                repository.endTeacherSession()
                runCatching { BleScannerService.stop(context.applicationContext) }
                    .onFailure { repository.reportServiceProblem("Teacher", "The session is stopped. Bluetooth is finishing its shutdown.") }
            }
            catch (error: Exception) { sessionError = error.message ?: "Unable to stop attendance." }
            finally { isStoppingSession = false }
        }
    }
    fun resumeAttendance(context: Context) {
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                repository.recoverAfterInterruption()
                val active = repository.activeTeacherSession() ?: return@launch
                if (active.bootId == repository.bootId) BleScannerService.start(context.applicationContext, active.classId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { sessionError = "Unable to resume attendance: ${error.message}" }
        }
    }
}
