package com.attendance.app.student.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.network.ReportDownloader
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.student.presentation.StudentAttendanceViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentClassDetailScreen(
    classId: String,
    navController: NavController,
    classViewModel: ClassViewModel = koinViewModel(),
    attendanceViewModel: StudentAttendanceViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val classes by classViewModel.classes.collectAsState()
    val classItem = classes.find { it.id == classId }

    val isScanning by attendanceViewModel.isScanning.collectAsState()
    val scanStatus by attendanceViewModel.scanStatus.collectAsState()
    val todayStatus by attendanceViewModel.todayStatus.collectAsState()
    val currentBackStackEntry = navController.currentBackStackEntry
    val verifiedClassId = currentBackStackEntry?.savedStateHandle?.getStateFlow<String?>("verified_class_id", null)?.collectAsState()?.value

    androidx.compose.runtime.LaunchedEffect(verifiedClassId) {
        if (verifiedClassId != null && verifiedClassId == classId) {
            currentBackStackEntry.savedStateHandle.remove<String>("verified_class_id")
            attendanceViewModel.onFaceVerificationSuccess(classId, context)
        }
    }
    val attendanceAction by attendanceViewModel.attendanceAction.collectAsState()

    androidx.compose.runtime.LaunchedEffect(attendanceAction) {
        when (val action = attendanceAction) {
            is com.attendance.app.student.presentation.AttendanceAction.RequireFaceVerification -> {
                navController.navigate(com.attendance.app.core.ui.navigation.Screen.FaceVerification(action.classId))
                attendanceViewModel.resetAction()
            }
            else -> {}
        }
    }

    LaunchedEffect(classId) {
        attendanceViewModel.fetchTodayStatus(classId)
    }

        val downloader = remember { ReportDownloader(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(context, "Please turn on Bluetooth first!", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            attendanceViewModel.checkIn(context, classId)
        } else {
            Toast.makeText(context, "Permissions required for broadcasting", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(classItem?.name ?: "Class Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val tokenManager = org.koin.java.KoinJavaComponent.getKoin().get<com.attendance.app.auth.data.TokenManager>()
                        val studentId = tokenManager.getUserId() ?: return@IconButton
                        downloader.downloadStudentReport(classId, studentId, java.time.YearMonth.now().toString()) 
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download Report")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (classItem == null) {
                CircularProgressIndicator()
            } else {
                Text(classItem.name, style = MaterialTheme.typography.headlineMedium)
                Text("Teacher: ${classItem.teacherName}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                if (todayStatus != null) {
                    Text("Today's Status: $todayStatus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Fetching today's status...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(32.dp))

                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(scanStatus, style = MaterialTheme.typography.bodyLarge)
                } else if (scanStatus.contains("checked in") || scanStatus.contains("Active")) {
                    Text(scanStatus, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { attendanceViewModel.initiateCheckOut(classId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Leave Session / Stop Attendance", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = {
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            permissionLauncher.launch(permissions)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark Attendance", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}



