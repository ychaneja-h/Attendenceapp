package com.attendance.app.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminDashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AdminDashboardState>(AdminDashboardState.Loading)
    val uiState: StateFlow<AdminDashboardState> = _uiState.asStateFlow()

    init {
        loadAdminStats()
    }

    private fun loadAdminStats() {
        viewModelScope.launch {
            _uiState.value = AdminDashboardState.Success(
                totalUsers = 1250,
                activeDepartments = 5,
                systemHealth = "Operational",
                flaggedAccounts = 2
            )
        }
    }
}

sealed class AdminDashboardState {
    object Loading : AdminDashboardState()
    data class Success(
        val totalUsers: Int,
        val activeDepartments: Int,
        val systemHealth: String,
        val flaggedAccounts: Int
    ) : AdminDashboardState()
    data class Error(val message: String) : AdminDashboardState()
}
