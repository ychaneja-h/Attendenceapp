package com.attendance.app.teacher.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TeacherHistoryViewModel(private val repository: AttendanceRepository) : ViewModel() {
    val state = repository.state
    val sessions = state.map { it.sessions }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init { refresh() }
    fun refresh() = repository.refresh()
    fun deleteSession(sessionId: String) {
        viewModelScope.launch { runCatching { repository.deleteSession(sessionId) }.onFailure { _error.value = it.message } }
    }
}
