package com.attendance.app.student.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.ble.advertiser.BleAdvertiserService
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StudentAttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {
    val state = repository.state
    val activeAttempt = state.map { it.activeStudentAttempt }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val isScanning = MutableStateFlow(false).asStateFlow()
    private val _scanStatus = MutableStateFlow("Ready")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()
    private val _todayStatus = MutableStateFlow<String?>(null)
    val todayStatus = _todayStatus.asStateFlow()
    private val _attendanceAction = MutableStateFlow<AttendanceAction?>(null)
    val attendanceAction = _attendanceAction.asStateFlow()
    private val _faceSaveError = MutableStateFlow<String?>(null)
    val faceSaveError = _faceSaveError.asStateFlow()
    private val _isSavingFace = MutableStateFlow(false)
    val isSavingFace = _isSavingFace.asStateFlow()
    init { refresh() }
    fun refresh() = repository.refresh()

    fun checkIn(context: Context, classId: String) {
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                val current = repository.activeStudentAttempt()
                require(current == null) { if (current?.classId == classId) "Attendance is already active for this class." else "You are already checked into another class." }
                repository.setPendingFaceAction(classId, false)
                _attendanceAction.value = AttendanceAction.RequireFaceVerification(classId)
            } catch (error: Exception) { _scanStatus.value = error.message ?: "Unable to check in." }
        }
    }

    fun onFaceVerificationSuccess(classId: String, context: Context, isCheckOut: Boolean = false,
        verifiedAt: Long = System.currentTimeMillis(), verifiedElapsedMs: Long = SystemClock.elapsedRealtime(),
        onSaved: () -> Unit = {}) {
        if (_isSavingFace.value) return
        _isSavingFace.value = true
        _faceSaveError.value = null
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                val pending = repository.pendingFaceAction()
                val checkout = isCheckOut || (pending?.classId == classId && pending.isCheckOut)
                if (checkout) {
                    repository.recordExitFace(classId, verifiedAt, verifiedElapsedMs)
                    runCatching { BleAdvertiserService.stop(context.applicationContext) }
                        .onFailure { repository.reportServiceProblem("Student", "Face checkout is saved. Bluetooth is finishing its shutdown.") }
                    _scanStatus.value = "Face checkout saved. Attendance will be reconciled after sync."
                } else {
                    repository.recordEntryFace(classId, verifiedAt, verifiedElapsedMs)
                    try { BleAdvertiserService.start(context.applicationContext) }
                    catch (_: Exception) { repository.reportServiceProblem("Student", "Bluetooth attendance could not start. Reopen this class to resume.") }
                    _scanStatus.value = "Attendance System Active. You are checked in."
                }
                _attendanceAction.value = AttendanceAction.Completed
                onSaved()
            } catch (error: Exception) {
                _faceSaveError.value = error.message ?: "Unable to save face verification. Try saving again."
                _scanStatus.value = _faceSaveError.value.orEmpty()
            } finally { _isSavingFace.value = false }
        }
    }

    /** Called only by a visible screen, so Android permits starting the service. */
    fun resumeAttendance(context: Context) {
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                repository.recoverAfterInterruption()
                if (repository.activeStudentAttempt() != null) BleAdvertiserService.start(context.applicationContext)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { repository.reportServiceProblem("Student", "Unable to resume attendance: ${error.message}") }
        }
    }
    fun initiateCheckOut(classId: String) {
        viewModelScope.launch {
            try {
                repository.awaitInitialization()
                require(repository.activeStudentAttempt()?.classId == classId) { "This class has no active attendance attempt." }
                repository.setPendingFaceAction(classId, true)
                _attendanceAction.value = AttendanceAction.RequireFaceVerification(classId, true)
            } catch (error: Exception) { _scanStatus.value = error.message ?: "Unable to check out." }
        }
    }
    fun fetchTodayStatus(classId: String) { refresh(); _todayStatus.value = state.value.studentResults.firstOrNull { it.classId == classId }?.status }
    fun resetAction() { _attendanceAction.value = null }
}

sealed class AttendanceAction {
    data class RequireFaceVerification(val classId: String, val isCheckOut: Boolean = false) : AttendanceAction()
    data object Completed : AttendanceAction()
}
