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
    val academicYear: String
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
data class VerifyPhoneRequest(
    val phone: String,
    val otp: String
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
