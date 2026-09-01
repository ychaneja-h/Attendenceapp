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

class TeacherReviewsViewModel(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager
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

    fun overrideAttendance(recordId: String, studentId: String, newStatus: String, reason: String) {
        val cid = currentClassId ?: return
        viewModelScope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    @kotlinx.serialization.Serializable
                    data class OverrideRequest(val classId: String, val studentId: String, val status: String, val reason: String)
                    
                    val response = httpClient.post("${com.attendance.app.core.network.Constants.BASE_URL}/attendance/override") {

                        contentType(ContentType.Application.Json)
                        setBody(OverrideRequest(cid, studentId, newStatus, reason))
                    }
                    if (response.status.isSuccess()) {
                        // Remove from local list
                        val currentState = _uiState.value
                        if (currentState is ReviewUiState.Success) {
                            val updatedList = currentState.items.filterNot { it.recordId == recordId }
                            _uiState.value = ReviewUiState.Success(updatedList)
                        }
                    }
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

