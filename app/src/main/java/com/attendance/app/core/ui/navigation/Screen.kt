package com.attendance.app.core.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    // Auth
    @Serializable data object RoleSelection : Screen
    @Serializable data object StudentLogin : Screen
    @Serializable data object StudentRegister : Screen
    @Serializable data object TeacherLogin : Screen
    @Serializable data object TeacherRegister : Screen
    @Serializable data object HodLogin : Screen
    @Serializable data object HodRegister : Screen
    @Serializable data object AdminLogin : Screen
    @Serializable data object ForgotPassword : Screen

    // Student
    @Serializable data object StudentHome : Screen
    @Serializable data object StudentClasses : Screen
    @Serializable data object StudentAttendance : Screen
    @Serializable data object StudentProfile : Screen
    @Serializable data object JoinClass : Screen
    @Serializable data class StudentClassDetail(val classId: String) : Screen
    @Serializable data class StudentAttendanceDetail(val classId: String) : Screen

    // Teacher
    @Serializable data object TeacherHome : Screen
    @Serializable data object TeacherClasses : Screen
    @Serializable data object TeacherReviews : Screen
    @Serializable data object TeacherHistory : Screen
    @Serializable data object TeacherProfile : Screen
    @Serializable data object CreateClass : Screen
    @Serializable data class TeacherClassDetail(val classId: String) : Screen
    @Serializable data class ClassMembers(val classId: String) : Screen
    @Serializable data class PendingRequests(val classId: String) : Screen
    @Serializable data class StartSession(val classId: String) : Screen
    @Serializable data class LiveSession(val classId: String, val interval: Int = 1, val minChecks: Int = 3, val startTime: Long = 0L) : Screen
    @Serializable data object TeacherRequests : Screen

    // HOD
    @Serializable data object HodDashboard : Screen
    @Serializable data object HodTeachers : Screen
    @Serializable data object HodReports : Screen
    @Serializable data object HodProfile : Screen

    // Admin
    @Serializable data object AdminHome : Screen
    @Serializable data object AdminUsers : Screen
    @Serializable data object AdminDepartments : Screen
    @Serializable data object AdminSettings : Screen
    @Serializable data object AdminAuditLogs : Screen
    @Serializable data object AdminSecurity : Screen

    // Face
    @Serializable data object FaceEnrollment : Screen
    @Serializable data class FaceVerification(val classId: String) : Screen
}


