package com.attendance.app.teacher.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.data.attendance.AttendanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TeacherReviewsViewModel(private val repository: AttendanceRepository) : ViewModel() {
    private val classId = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState = _uiState.asStateFlow()
    init {
        viewModelScope.launch {
            combine(repository.state, classId) { state, selected ->
                state.sessions.filter { it.classId == selected }.flatMap { session ->
                    session.students.filter { it.status == "UNDER_REVIEW" }.map {
                        ReviewItem(session.sessionId, it.studentId, it.name, it.reasons.joinToString("; "))
                    }
                }
            }.collect { _uiState.value = ReviewUiState.Success(it) }
        }
    }
    fun loadPendingReviews(classId: String) { this.classId.value = classId; repository.refresh() }
    fun overrideAttendance(recordId: String, studentId: String, newStatus: String, reason: String) {
        viewModelScope.launch {
            runCatching { repository.overrideStudent(recordId, studentId, newStatus, reason) }
                .onFailure { _uiState.value = ReviewUiState.Error(it.message ?: "Unable to save override.") }
        }
    }
}
sealed class ReviewUiState {
    data object Loading : ReviewUiState()
    data class Success(val items: List<ReviewItem>) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}
@kotlinx.serialization.Serializable
data class ReviewItem(val recordId: String, val studentId: String, val studentName: String, val issueType: String)
