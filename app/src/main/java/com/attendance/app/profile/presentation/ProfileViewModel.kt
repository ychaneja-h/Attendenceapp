package com.attendance.app.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.profile.data.ProfileRepository
import com.attendance.shared.dto.ProfileResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: ProfileResponse) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val result = repository.getProfile()
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success(result.getOrNull()!!)
            } else {
                _uiState.value = ProfileUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
        }
    }

    fun updateProfile(name: String, email: String, department: String? = null, section: String? = null, batch: String? = null, year: String? = null, photoUrl: String? = null) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null
            val result = repository.updateProfile(name, email, department, section, batch, year, photoUrl)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success(result.getOrNull()!!)
                _saveMessage.value = "Profile updated successfully!"
            } else {
                _saveMessage.value = result.exceptionOrNull()?.message ?: "Failed to update profile"
            }
            _isSaving.value = false
        }
    }

    fun uploadProfilePhoto(byteArray: ByteArray, fileName: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = "Uploading photo..."
            val result = repository.uploadProfilePhoto(byteArray, fileName)
            if (result.isSuccess) {
                _uiState.value = ProfileUiState.Success(result.getOrNull()!!)
                _saveMessage.value = "Photo uploaded successfully!"
            } else {
                _saveMessage.value = result.exceptionOrNull()?.message ?: "Failed to upload photo"
            }
            _isSaving.value = false
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}

