package com.attendance.app.hod.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HodDashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<HodDashboardState>(HodDashboardState.Loading)
    val uiState: StateFlow<HodDashboardState> = _uiState.asStateFlow()

    init {
        loadDashboardStats()
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
