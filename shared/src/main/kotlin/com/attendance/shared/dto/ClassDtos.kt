package com.attendance.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateClassRequest(
    val name: String,
    val section: String,
    val semester: Int,
    val departmentId: String? = null,
    val academicYearId: String? = null
)

@Serializable
data class ClassResponse(
    val id: String,
    val name: String,
    val section: String,
    val semester: Int,
    val department: String,
    val academicYear: String,
    val classCode: String,
    val teacherName: String,
    val studentCount: Int,
    val isActive: Boolean,
    val memberStatus: String = "ACTIVE"
)

@Serializable
data class JoinClassRequest(
    val classCode: String
)

@Serializable
data class ClassMemberResponse(
    val id: String,
    val studentId: String,
    val studentName: String,
    val collegeId: String,
    val status: String,
    val joinedAt: Long
)

@Serializable
data class JoinRequestResponse(
    val id: String,
    val studentId: String,
    val studentName: String,
    val collegeId: String,
    val status: String,
    val requestedAt: Long
)
