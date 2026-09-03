package com.attendance.app.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.AuthRepository
import com.attendance.shared.dto.LoginRequest
import com.attendance.shared.dto.StudentRegisterRequest
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

        var resetSuccess by mutableStateOf(false)
        private set

    fun resetPassword(collegeId: String, newPassword: String, faceEmbedding: FloatArray) {
        viewModelScope.launch {
            isLoading = true
            error = null
            resetSuccess = false
            
            val result = repository.resetPassword(
                com.attendance.shared.dto.ResetPasswordRequest(
                    collegeId = collegeId,
                    newPassword = newPassword,
                    faceEmbedding = faceEmbedding.toList()
                )
            )
            
            result.onSuccess {
                resetSuccess = true
            }.onFailure { e ->
                error = e.message ?: "Reset failed"
            }
            isLoading = false
        }
    }

    fun clearResetSuccess() {
        resetSuccess = false
    }

    fun login(context: android.content.Context, collegeId: String, password: String, onSuccess: (role: String, requiresFace: Boolean) -> Unit, onDeviceMismatch: (String) -> Unit, onPendingApproval: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
            val result = repository.initiateLogin(LoginRequest(collegeId, password, androidId, "Android Device"))
            result.onSuccess { response ->
                onSuccess(response.role, response.requiresFaceEnrollment)
            }.onFailure { e ->
                val errorMsg = e.message ?: "Login failed"
                if (errorMsg.contains("DEVICE_MISMATCH")) {
                    onDeviceMismatch(androidId)
                } else if (errorMsg.contains("PENDING_APPROVAL")) {
                    onPendingApproval()
                } else {
                    error = errorMsg
                }
            }
            isLoading = false
        }
    }

    fun verifyStaffCode(passcode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.verifyStaffCode(passcode)
            isLoading = false
            result.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Invalid passcode") }
        }
    }

    fun requestDeviceTransfer(newDeviceId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.requestDeviceTransfer(newDeviceId)
            result.onFailure { e ->
                error = e.message ?: "Failed to request device transfer"
            }
            onComplete()
            isLoading = false
        }
    }

        fun register(
        context: android.content.Context,
        name: String,
        collegeId: String,
        department: String,
        section: String,
        semester: Int,
        academicYear: String,
        password: String,
        onSuccess: (role: String, requiresFace: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
            val result = repository.registerStudent(
                StudentRegisterRequest(
                    collegeId = collegeId,
                    name = name,
                    password = password,
                    department = department,
                    section = section,
                    semester = semester,
                    academicYear = academicYear,
                    deviceFingerprint = androidId
                )
            )
            result.onSuccess { response ->
                onSuccess(response.role, response.requiresFaceEnrollment)
            }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun registerTeacher(name: String, collegeId: String, password: String, departmentCode: String, onPendingApproval: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.registerTeacher(
                com.attendance.shared.dto.TeacherRegisterRequest(
                    collegeId = collegeId,
                    name = name,
                    password = password,
                    departmentCode = departmentCode
                )
            )
            result.onSuccess { onPendingApproval() }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun registerHod(name: String, collegeId: String, password: String, department: String, departmentCode: String, onPendingApproval: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.registerHod(
                com.attendance.shared.dto.HodRegisterRequest(
                    name = name,
                    collegeId = collegeId,
                    password = password,
                    department = department,
                    departmentCode = departmentCode
                )
            )
            result.onSuccess { onPendingApproval() }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun logout() {
        repository.logout()
    }
}









