package com.attendance.app.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
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
    val attendanceRepository = koinInject<com.attendance.app.core.data.attendance.AttendanceRepository>()
    val attendance by attendanceRepository.state.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()

    // Reopening into Home or using another app route cannot bypass unfinished face checkout.
    LaunchedEffect(attendance.activeStudentAttempt?.attemptId, currentEntry) {
        val active = attendance.activeStudentAttempt ?: return@LaunchedEffect
        val destination = currentEntry?.destination ?: return@LaunchedEffect
        if (tokenManager.getRole()?.uppercase() == "STUDENT" &&
            !destination.hasRoute<Screen.StudentClassDetail>() && !destination.hasRoute<Screen.FaceVerification>()) {
            navController.navigate(Screen.StudentClassDetail(active.classId)) { launchSingleTop = true }
        }
    }
    
    val token = tokenManager.getToken()
    val role = tokenManager.getRole()
    
    val startDestination: Any = if (token != null && role != null) {
        when (role.uppercase()) {
            "STUDENT" -> Screen.StudentHome
            "TEACHER" -> Screen.TeacherHome
            "HOD" -> Screen.HodDashboard
            "ADMIN" -> Screen.AdminHome
            else -> Screen.RoleSelection
        }
    } else {
        Screen.RoleSelection
    }

    val handleAuthSuccess: (String, Boolean) -> Unit = { userRole, requiresFace ->
        val destination = when (userRole.uppercase()) {
            "STUDENT", "STUDENT_REGISTER" -> {
                if (requiresFace) Screen.FaceEnrollment else Screen.StudentHome
            }
            "TEACHER", "TEACHER_REGISTER" -> Screen.TeacherHome
            "HOD", "HOD_REGISTER" -> Screen.HodDashboard
            "ADMIN" -> Screen.AdminHome
            else -> Screen.RoleSelection
        }
        navController.navigate(destination) {
            popUpTo(Screen.RoleSelection) { inclusive = true }
        }
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
                onTeacherRegister = { navController.navigate(Screen.TeacherRegister) },
                onHodLogin = { navController.navigate(Screen.HodLogin) },
                onHodRegister = { navController.navigate(Screen.HodRegister) },
                onAdminLogin = { navController.navigate(Screen.AdminLogin) }
            )
        }
        composable<Screen.ChangePassword> {
            com.attendance.app.auth.ui.ChangePasswordScreen(
                onBack = { navController.popBackStack() },
                onPasswordChanged = {
                    navController.navigate(Screen.RoleSelection) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<Screen.StudentLogin> {
            StudentLoginScreen(
                onSuccess = handleAuthSuccess,
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.StudentRegister> {
            StudentRegisterScreen(
                onSuccess = handleAuthSuccess,
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.TeacherLogin> {
            TeacherLoginScreen(
                onSuccess = handleAuthSuccess,
                onRegisterClick = { navController.navigate(Screen.TeacherRegister) },
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.TeacherRegister> {
            TeacherRegisterScreen(
                
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.HodLogin> {
            HodLoginScreen(
                onSuccess = handleAuthSuccess,
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.HodRegister> {
            HodRegisterScreen(
                
                onBack = { navController.popBackStack() }
            )
        }
        composable<Screen.AdminLogin> {
            AdminLoginScreen(
                onSuccess = handleAuthSuccess,
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
            JoinClassScreen(onBack = { navController.popBackStack() }
            )
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
        composable<Screen.TeacherClassHistory> { entry ->
            TeacherHistoryScreen(navController = navController, classId = entry.toRoute<Screen.TeacherClassHistory>().classId)
        }
        composable<Screen.TeacherProfile> {
            TeacherProfileScreen(navController = navController)
        }
        composable<Screen.TeacherRequests> {
            TeacherRequestsScreen(navController = navController)
        }
        composable<Screen.CreateClass> {
            CreateClassScreen(onBack = { navController.popBackStack() }
            )
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
            com.attendance.app.teacher.ui.LiveSessionScreen(classId = args.classId, navController = navController, sessionId = args.sessionId, isHistory = args.isHistory)
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
        composable<Screen.AdminHome> {
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
            val context = LocalContext.current
            val attendanceViewModel: com.attendance.app.student.presentation.StudentAttendanceViewModel = org.koin.androidx.compose.koinViewModel()
            val saveError by attendanceViewModel.faceSaveError.collectAsState()
            val isSaving by attendanceViewModel.isSavingFace.collectAsState()
            com.attendance.app.face.ui.FaceVerificationScreen(
                onBack = { navController.popBackStack() },
                isCheckOut = args.isCheckOut,
                saveError = saveError,
                isSaving = isSaving,
                onVerificationSuccess = { verifiedAtMs, verifiedElapsedMs ->
                    attendanceViewModel.onFaceVerificationSuccess(
                        args.classId, context, args.isCheckOut, verifiedAtMs, verifiedElapsedMs
                    ) {
                        // The scan is durable before leaving the camera screen.
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}









