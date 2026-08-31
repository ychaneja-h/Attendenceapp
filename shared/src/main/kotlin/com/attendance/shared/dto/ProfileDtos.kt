package com.attendance.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val photoUrl: String? = null,
    val department: String? = null,
    val section: String? = null,
    val batch: String? = null,
    val year: String? = null
)

@Serializable
data class TeacherProfileDto(
    val id: String,
    val name: String,
    val facultyId: String,
    val status: String,
    val joinedAt: String
)

@Serializable
data class StudentProfileDto(
    val id: String,
    val name: String,
    val studentId: String,
    val status: String,
    val joinedAt: String
)

@Serializable
data class HodProfileDto(
    val id: String,
    val name: String,
    val facultyId: String,
    val status: String,
    val joinedAt: String
)

@Serializable
data class UpdateProfileRequest(
    val name: String,
    val email: String,
    val phone: String,
    val department: String? = null,
    val section: String? = null,
    val batch: String? = null,
    val year: String? = null,
    val photoUrl: String? = null
)
