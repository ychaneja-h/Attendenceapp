package com.attendance.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class StudentRegisterRequest(
    val collegeId: String,
    val name: String,
    val password: String,
    val department: String,
    val section: String,
    val semester: Int,
    val academicYear: String,
    val deviceFingerprint: String = "unknown"
)

@Serializable
data class HodRegisterRequest(
    val name: String,
    val collegeId: String,
    val password: String,
    val department: String,
    val departmentCode: String
)

@Serializable
data class TeacherRegisterRequest(
    val collegeId: String,
    val name: String,
    val password: String,
    val departmentCode: String
)

@Serializable
data class LoginRequest(
    val collegeId: String,
    val password: String,
    val deviceFingerprint: String,
    val deviceModel: String? = null
)

@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val role: String,
    val userId: String,
    val name: String,
    val requiresFaceEnrollment: Boolean = false
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)


@Serializable
data class VerifyStaffCodeRequest(
    val passcode: String
)


@Serializable
data class ResetPasswordRequest(
    val collegeId: String,
    val newPassword: String,
    val faceEmbedding: List<Float>
)

@Serializable
data class DeviceTransferAuthReq(
    val collegeId: String,
    val password: String,
    val newDeviceId: String
)

@Serializable
data class ChangePasswordRequest(
    val newPassword: String,
    val faceEmbedding: List<Float>
)

@Serializable
data class GenericResponse(
    val message: String? = null,
    val error: String? = null,
    val success: Boolean? = null,
    val status: String? = null,
    val sessionId: String? = null
)


