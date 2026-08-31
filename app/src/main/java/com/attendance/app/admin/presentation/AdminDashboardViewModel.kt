package com.attendance.app.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.core.network.Constants
import com.attendance.shared.dto.HodProfileDto
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

class AdminDashboardViewModel(
    private val httpClient: HttpClient
) : ViewModel() {
    private val _pendingHods = MutableStateFlow<List<HodProfileDto>>(emptyList())
    val pendingHods: StateFlow<List<HodProfileDto>> = _pendingHods.asStateFlow()

    private val _activeHods = MutableStateFlow<List<HodProfileDto>>(emptyList())
    val activeHods: StateFlow<List<HodProfileDto>> = _activeHods.asStateFlow()

    init {
        loadHods()
    }

    fun loadHods() {
        viewModelScope.launch {
            try {
                val pendingResponse = httpClient.get("${Constants.BASE_URL}/admin/hods/pending")
                if (pendingResponse.status.isSuccess()) {
                    _pendingHods.value = pendingResponse.body()
                }

                val activeResponse = httpClient.get("${Constants.BASE_URL}/admin/hods/active")
                if (activeResponse.status.isSuccess()) {
                    _activeHods.value = activeResponse.body()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveHod(hodId: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.post("${Constants.BASE_URL}/admin/hods/$hodId/approve")
                if (response.status.isSuccess()) {
                    loadHods()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectHod(hodId: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.post("${Constants.BASE_URL}/admin/hods/$hodId/reject")
                if (response.status.isSuccess()) {
                    loadHods()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteHod(hodId: String) {
        viewModelScope.launch {
            try {
                val response = httpClient.delete("${Constants.BASE_URL}/admin/hods/$hodId")
                if (response.status.isSuccess()) {
                    loadHods()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
