package com.attendance.app.hod.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.network.Constants
import com.attendance.shared.dto.TeacherProfileDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HodDashboardViewModel(
    private val tokenManager: com.attendance.app.auth.data.TokenManager,
    private val httpClient: HttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<HodDashboardState>(HodDashboardState.Loading)
    val uiState: StateFlow<HodDashboardState> = _uiState.asStateFlow()

    private val _pendingTeachers = MutableStateFlow<List<TeacherProfileDto>>(emptyList())
    val pendingTeachers: StateFlow<List<TeacherProfileDto>> = _pendingTeachers.asStateFlow()

    private val _activeTeachers = MutableStateFlow<List<TeacherProfileDto>>(emptyList())
    val activeTeachers: StateFlow<List<TeacherProfileDto>> = _activeTeachers.asStateFlow()

    init {
        loadDashboardStats()
        fetchTeachers()
    }

    private fun loadDashboardStats() {
        viewModelScope.launch {
            // Mock backend call
            _uiState.value = HodDashboardState.Success(
                totalTeachers = 15,
                totalClasses = 42,
                averageAttendance = 87.5f,
                pendingTeacherApprovals = 3
            )
        }
    }
    
    fun exportCsvReport(classId: String) {
        // Logic to trigger backend CSV generation or local Room export
    }

    fun fetchTeachers() {
        viewModelScope.launch {
            try {
                val response = httpClient.get("${Constants.BASE_URL}/hod/teachers/pending") { io.ktor.client.request.header("Authorization", "Bearer ${tokenManager.getToken()}") }
                if (response.status.isSuccess()) {
                    _pendingTeachers.value = response.body()
                }
                
                val activeResponse = httpClient.get("${Constants.BASE_URL}/hod/teachers/active") { io.ktor.client.request.header("Authorization", "Bearer ${tokenManager.getToken()}") }
                if (activeResponse.status.isSuccess()) {
                    _activeTeachers.value = activeResponse.body()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun approveTeacher(id: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.post("${Constants.BASE_URL}/hod/teachers/$id/approve") { io.ktor.client.request.header("Authorization", "Bearer ${tokenManager.getToken()}") }
                if (response.status.isSuccess()) {
                    fetchTeachers()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun rejectTeacher(id: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.post("${Constants.BASE_URL}/hod/teachers/$id/reject") { io.ktor.client.request.header("Authorization", "Bearer ${tokenManager.getToken()}") }
                if (response.status.isSuccess()) {
                    fetchTeachers()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteTeacher(id: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.delete("${Constants.BASE_URL}/hod/teachers/$id") { io.ktor.client.request.header("Authorization", "Bearer ${tokenManager.getToken()}") }
                if (response.status.isSuccess()) {
                    fetchTeachers()
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

sealed class HodDashboardState {
    object Loading : HodDashboardState()
    data class Success(
        val totalTeachers: Int,
        val totalClasses: Int,
        val averageAttendance: Float,
        val pendingTeacherApprovals: Int
    ) : HodDashboardState()
    data class Error(val message: String) : HodDashboardState()
}

