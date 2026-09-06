package com.attendance.app.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.TokenManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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

import com.attendance.app.core.data.local.room.AttendanceDao

class TeacherReviewsViewModel(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager,
    private val attendanceDao: AttendanceDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var currentClassId: String? = null

    fun loadPendingReviews(classId: String) {
        currentClassId = classId
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = httpClient.get("${com.attendance.app.core.network.Constants.BASE_URL}/attendance/reviews/$classId") {

                    }
                    if (response.status.isSuccess()) {
                        val items: List<ReviewItem> = response.body()
                        _uiState.value = ReviewUiState.Success(items)
                    } else {
                        _uiState.value = ReviewUiState.Error("Failed to fetch reviews")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ReviewUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

        fun overrideAttendance(recordId: String, studentId: String, newStatus: String, reason: String, classId: String? = null) {
        val cid = classId ?: currentClassId ?: return
        viewModelScope.launch {
            try {
                attendanceDao.insertRecord(
                    com.attendance.app.core.data.local.room.AttendanceRecordEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        classId = cid,
                        studentId = studentId,
                        timestamp = System.currentTimeMillis(),
                        isPresent = newStatus == "PRESENT",
                        method = "MANUAL_$newStatus",
                        syncStatus = "PENDING"
                    )
                )
                // Remove from local list
                val currentState = _uiState.value
                if (currentState is ReviewUiState.Success) {
                    val updatedList = currentState.items.filterNot { it.recordId == recordId || it.studentId == studentId }
                    _uiState.value = ReviewUiState.Success(updatedList)
                }
            } catch (e: Exception) {
                // Ignore error for now
            }
        }
    }
}

sealed class ReviewUiState {
    object Loading : ReviewUiState()
    data class Success(val items: List<ReviewItem>) : ReviewUiState()
    data class Error(val message: String) : ReviewUiState()
}

@kotlinx.serialization.Serializable
data class ReviewItem(
    val recordId: String,
    val studentId: String,
    val studentName: String,
    val issueType: String
)




