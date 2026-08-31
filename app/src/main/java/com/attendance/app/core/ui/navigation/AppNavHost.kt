package com.attendance.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.attendance.app.auth.ui.RoleSelectionScreen
import com.attendance.app.auth.ui.*
import com.attendance.app.student.ui.*
import com.attendance.app.teacher.ui.*
import com.attendance.app.hod.ui.*
import com.attendance.app.admin.ui.*

import org.koin.compose.koinInject
import com.attendance.app.auth.data.TokenManager

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val tokenManager = koinInject<TokenManager>()
    
    val token = tokenManager.getToken()
    val role = tokenManager.getRole()
    
    val startDestination: Any = if (token != null && role != null) {
        when (role.uppercase()) {
            "STUDENT" -> Screen.StudentHome
            "TEACHER" -> Screen.TeacherHome
            "HOD" -> Screen.HodDashboard
            "ADMIN" -> Screen.AdminDashboard
            else -> Screen.RoleSelection
        }
    } else {
        Screen.RoleSelection
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth screens
        composable<Screen.RoleSelection> {
            RoleSelectionScreen(
                onStudentLogin = { navController.navigate(Screen.StudentLogin) },
                onStudentRegister = { navController.navigate(Screen.StudentRegister) },
                onTeacherLogin = { navController.navigate(Screen.TeacherLogin) },
                onHodLogin = { navController.navigate(Screen.HodLogin) },
                onHodRegister = { navController.navigate(Screen.HodRegister) },
                onAdminLogin = { navController.navigate(Screen.AdminLogin) }
            )
        }
        composable<Screen.StudentLogin> {
            StudentLoginScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "STUDENT", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.StudentRegister> {
            StudentRegisterScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "STUDENT_REGISTER", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.TeacherLogin> {
            TeacherLoginScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "TEACHER", token))
                },
                onRegisterClick = { navController.navigate(Screen.TeacherRegister) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.TeacherRegister> {
            TeacherRegisterScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "TEACHER_REGISTER", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.HodLogin> {
            HodLoginScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "HOD", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.HodRegister> {
            HodRegisterScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "HOD_REGISTER", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.AdminLogin> {
            AdminLoginScreen(
                onOtpRequired = { phone, token ->
                    navController.navigate(Screen.OtpVerification(phone, "ADMIN", token))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.OtpVerification> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.OtpVerification>()
            OtpVerificationScreen(
                phone = args.phone,
                sessionToken = args.sessionToken,
                onVerified = { role, requiresFace ->
                    val destination = when (role) {
                        "STUDENT", "STUDENT_REGISTER" -> {
                            if (requiresFace) Screen.FaceEnrollment else Screen.StudentHome
                        }
                        "TEACHER", "TEACHER_REGISTER" -> Screen.TeacherHome
                        "HOD", "HOD_REGISTER" -> Screen.HodDashboard
                        "ADMIN" -> Screen.AdminDashboard
                        else -> Screen.RoleSelection
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.RoleSelection) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Student screens
        composable<Screen.StudentHome> {
            StudentHomeScreen(navController = navController)
        }
        composable<Screen.StudentClasses> {
            StudentClassesScreen(navController = navController)
        }
        composable<Screen.StudentClassDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.StudentClassDetail>()
            StudentClassDetailScreen(
                classId = args.classId,
                navController = navController
            )
        }
        composable<Screen.StudentAttendance> {
            StudentAttendanceScreen(navController = navController)
        }
        composable<Screen.StudentProfile> {
            StudentProfileScreen(navController = navController)
        }
        composable<Screen.JoinClass> {
            JoinClassScreen(onBack = { navController.popBackStack() })
        }

        // Teacher screens
        composable<Screen.TeacherHome> {
            TeacherHomeScreen(navController = navController)
        }
        composable<Screen.TeacherClasses> {
            TeacherClassesScreen(navController = navController)
        }
        composable<Screen.TeacherReviews> {
            TeacherReviewsScreen(navController = navController)
        }
        composable<Screen.TeacherHistory> {
            TeacherHistoryScreen(navController = navController)
        }
        composable<Screen.TeacherProfile> {
            TeacherProfileScreen(navController = navController)
        }
        composable<Screen.TeacherRequests> {
            TeacherRequestsScreen(navController = navController)
        }
        composable<Screen.CreateClass> {
            CreateClassScreen(onBack = { navController.popBackStack() })
        }
        composable<Screen.TeacherClassDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.TeacherClassDetail>()
            TeacherClassDetailScreen(
                classId = args.classId,
                navController = navController
            )
        }
        composable<Screen.LiveSession> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.LiveSession>()
            com.attendance.app.teacher.ui.LiveSessionScreen(
                classId = args.classId,
                navController = navController
            )
        }

        // HOD screens
        composable<Screen.HodDashboard> {
            HodDashboardScreen(navController = navController)
        }
        composable<Screen.HodTeachers> {
            HodTeachersScreen(navController = navController)
        }
        composable<Screen.HodReports> {
            HodReportsScreen(navController = navController)
        }
        composable<Screen.HodProfile> {
            HodProfileScreen(navController = navController)
        }

        // Admin screens
        composable<Screen.AdminDashboard> {
            AdminDashboardScreen(navController = navController)
        }
        composable<Screen.AdminUsers> {
            AdminUsersScreen(navController = navController)
        }
        composable<Screen.AdminDepartments> {
            AdminDepartmentsScreen(navController = navController)
        }
        composable<Screen.AdminSettings> {
            AdminSettingsScreen(navController = navController)
        }

        // Face Screens
        composable<Screen.FaceEnrollment> {
            com.attendance.app.face.ui.FaceRegistrationScreen(
                onBack = { navController.popBackStack() },
                onRegistrationComplete = { 
                    navController.navigate(Screen.StudentHome) {
                        popUpTo(Screen.FaceEnrollment) { inclusive = true }
                    }
                },
                showBackButton = navController.previousBackStackEntry != null
            )
        }
        
        composable<Screen.FaceVerification> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.FaceVerification>()
            com.attendance.app.face.ui.FaceVerificationScreen(
                onBack = { navController.popBackStack() },
                onVerificationSuccess = {
                    // Navigate back with success result to home screen
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("verified_class_id", args.classId)
                    navController.popBackStack()
                }
            )
        }
    }
}
