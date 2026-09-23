package com.attendance.app.teacher.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LiveSessionViewModel(private val repository: AttendanceRepository) : ViewModel() {
    val state = repository.state
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init { refresh() }
    fun refresh() = repository.refresh()
    fun loadSession(classId: String, startTime: Long = 0L) = refresh()
    fun overrideStudent(sessionId: String, studentId: String, status: String, reason: String = "") {
        viewModelScope.launch { runCatching { repository.overrideStudent(sessionId, studentId, status, reason) }.onFailure { _error.value = it.message } }
    }
}
