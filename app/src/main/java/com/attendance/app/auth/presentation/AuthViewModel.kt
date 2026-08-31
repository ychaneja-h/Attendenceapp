package com.attendance.app.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.auth.data.AuthRepository
import com.attendance.shared.dto.LoginRequest
import com.attendance.shared.dto.StudentRegisterRequest
import com.attendance.shared.dto.VerifyOtpRequest
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(context: android.content.Context, collegeId: String, password: String, onOtpRequired: (String) -> Unit, onDeviceMismatch: (String) -> Unit, onPendingApproval: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
            val result = repository.initiateLogin(LoginRequest(collegeId, password, androidId, "Android Device"))
            result.onSuccess { response ->
                onOtpRequired(response.sessionToken)
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
        name: String,
        collegeId: String,
        phone: String,
        department: String,
        section: String,
        semester: Int,
        academicYear: String,
        password: String,
        onOtpRequired: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.registerStudent(
                StudentRegisterRequest(
                    collegeId = collegeId,
                    name = name,
                    phone = phone,
                    password = password,
                    department = department,
                    section = section,
                    semester = semester,
                    academicYear = academicYear
                )
            )
            result.onSuccess { response ->
                onOtpRequired(response.sessionToken)
            }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun registerTeacher(
        name: String,
        collegeId: String,
        phone: String,
        departmentCode: String,
        password: String,
        onOtpRequired: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.registerTeacher(
                com.attendance.shared.dto.TeacherRegisterRequest(
                    collegeId = collegeId,
                    name = name,
                    phone = phone,
                    password = password,
                    departmentCode = departmentCode
                )
            )
            result.onSuccess { response ->
                onOtpRequired(response.sessionToken)
            }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun registerHod(
        name: String,
        collegeId: String,
        phone: String,
        department: String,
        departmentCode: String,
        password: String,
        onOtpRequired: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.registerHod(
                com.attendance.shared.dto.HodRegisterRequest(
                    name = name,
                    collegeId = collegeId,
                    phone = phone,
                    password = password,
                    department = department,
                    departmentCode = departmentCode
                )
            )
            result.onSuccess { response ->
                onOtpRequired(response.sessionToken)
            }.onFailure { e ->
                error = e.message ?: "Registration failed"
            }
            isLoading = false
        }
    }

    fun verifyOtp(phone: String, otp: String, sessionToken: String, onVerified: (role: String, requiresFaceEnrollment: Boolean) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            error = null
            val result = repository.verifyOtp(VerifyOtpRequest(phone, otp, sessionToken))
            result.onSuccess { response ->
                onVerified(response.role, response.requiresFaceEnrollment)
            }.onFailure { e ->
                error = e.message ?: "OTP Verification failed"
            }
            isLoading = false
        }
    }

    fun logout() {
        repository.logout()
    }
}
