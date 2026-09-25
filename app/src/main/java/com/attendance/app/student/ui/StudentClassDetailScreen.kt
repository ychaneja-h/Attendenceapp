package com.attendance.app.student.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.core.network.ReportDownloader
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.core.ui.components.AttendanceCounts
import com.attendance.app.core.ui.components.AttendanceDetails
import com.attendance.app.core.ui.components.AttendanceStatus
import com.attendance.app.core.ui.components.AttendanceSyncNote
import com.attendance.app.core.ui.components.attendanceLabel
import com.attendance.app.student.presentation.AttendanceAction
import com.attendance.app.student.presentation.StudentAttendanceViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val state by attendanceViewModel.state.collectAsState()
    val action by attendanceViewModel.attendanceAction.collectAsState()
    val scanStatus by attendanceViewModel.scanStatus.collectAsState()
    val attempt = state.activeStudentAttempt
    var showLeaveDialog by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val monthResults = state.studentResults.filter {
        it.classId == classId && YearMonth.from(Instant.ofEpochMilli(it.startedAtMs).atZone(ZoneId.systemDefault())) == month
    }.sortedByDescending { it.startedAtMs }
    LaunchedEffect(action) {
        val face = action as? AttendanceAction.RequireFaceVerification ?: return@LaunchedEffect
        attendanceViewModel.resetAction()
        navController.navigate(Screen.FaceVerification(face.classId, face.isCheckOut)) { launchSingleTop = true }
    }
    LaunchedEffect(classId) { attendanceViewModel.refresh() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, attendanceViewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) attendanceViewModel.resumeAttendance(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val beginCheckIn = {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter?.isEnabled == true) attendanceViewModel.checkIn(context, classId)
        else Toast.makeText(context, "Turn on Bluetooth before joining.", Toast.LENGTH_LONG).show()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.all { it }) beginCheckIn()
        else Toast.makeText(context, "Bluetooth permission is required for attendance.", Toast.LENGTH_LONG).show()
    }
    BackHandler(enabled = attempt != null) { showLeaveDialog = true }
    if (showLeaveDialog) AlertDialog(
        onDismissRequest = { showLeaveDialog = false },
        title = { Text("Still checked in") },
        text = { Text("When class ends, scan your face to check out. You have five minutes after your teacher stops attendance.") },
        confirmButton = { TextButton(onClick = {
            showLeaveDialog = false
            attempt?.let { attendanceViewModel.initiateCheckOut(it.classId) }
        }) { Text("Check out") } },
        dismissButton = { TextButton(onClick = { showLeaveDialog = false }) { Text("Stay") } }
    )
    val downloader = remember { ReportDownloader(context) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(classItem?.name ?: "Class attendance") }, navigationIcon = {
            IconButton(onClick = { if (attempt != null) showLeaveDialog = true else navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        }, actions = {
            IconButton(onClick = { attendanceViewModel.refresh() }) { Icon(Icons.Default.Refresh, "Sync attendance") }
            IconButton(onClick = {
                val token = org.koin.java.KoinJavaComponent.getKoin().get<com.attendance.app.auth.data.TokenManager>()
                token.getUserId()?.let { downloader.downloadStudentReport(classId, it, month.toString()) }
            }) { Icon(Icons.Default.Download, "Download monthly report") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                classItem?.let { Text("Teacher: ${it.teacherName}", Modifier.padding(top = 12.dp)) }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            attempt?.classId == classId -> {
                                Text("You're checked in", style = MaterialTheme.typography.titleLarge)
                                Text("Keep Bluetooth on until class ends.", style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { attendanceViewModel.initiateCheckOut(classId) }, modifier = Modifier.fillMaxWidth()) { Text("Check out · scan face") }
                            }
                            attempt != null -> {
                                Text("Attendance is active in another class.")
                                Button(onClick = { navController.navigate(Screen.StudentClassDetail(attempt.classId)) }) { Text("Return to active class") }
                            }
                            else -> {
                                Text("Ready for class?", style = MaterialTheme.typography.titleLarge)
                                Text("Scan your face when your teacher starts attendance.")
                                Button(onClick = {
                                    val permissions = mutableListOf<String>()
                                    if (Build.VERSION.SDK_INT >= 31) permissions.addAll(listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
                                    if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                    if (permissions.isEmpty()) beginCheckIn() else permissionLauncher.launch(permissions.toTypedArray())
                                }, modifier = Modifier.fillMaxWidth()) { Text("Check in · scan face") }
                            }
                        }
                        if (scanStatus.isNotBlank() && scanStatus != "Ready" && !scanStatus.startsWith("Attendance System Active") && !scanStatus.startsWith("Face checkout saved")) Text(scanStatus)
                        state.serviceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        AttendanceSyncNote(state.syncMessage)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("Previous") }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Text("Next") }
                }
                AttendanceCounts(monthResults.count { it.status == "PRESENT" }, monthResults.count { it.status == "ABSENT" }, monthResults.count { it.status == "UNDER_REVIEW" })
                if (monthResults.isEmpty()) Text("No classes recorded this month.", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(monthResults, key = { it.sessionId }) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Instant.ofEpochMilli(result.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM, h:mm a")))
                        AttendanceStatus(result.status, result.provisional)
                        AttendanceDetails {
                            Text("Checks: ${result.successfulChecks} of ${result.totalChecks}")
                            Text("Check-in: ${attendanceLabel(result.entryFaceStatus)}")
                            Text("Check-out: ${attendanceLabel(result.exitFaceStatus)}")
                            result.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
