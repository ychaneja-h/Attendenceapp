package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.teacher.presentation.LiveSessionUiState
import com.attendance.app.teacher.presentation.LiveSessionViewModel
import com.attendance.app.teacher.presentation.TeacherAttendanceViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    classId: String,
    navController: NavController,
    historyStartTime: Long? = null,
    liveSessionViewModel: LiveSessionViewModel = koinViewModel(),
    attendanceViewModel: TeacherAttendanceViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by liveSessionViewModel.uiState.collectAsState()
    val presentStudents by liveSessionViewModel.presentStudents.collectAsState()
    val recheckInterval by liveSessionViewModel.recheckIntervalMinutes.collectAsState()

    LaunchedEffect(classId) {
        liveSessionViewModel.loadSession(classId, historyStartTime ?: attendanceViewModel.activeSessionStartTime.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (historyStartTime != null) "Session Details" else "Live Session") },
                actions = {
                    if (historyStartTime == null) {
                        IconButton(onClick = { 
                            liveSessionViewModel.endSession(context, "Recent Class") // Hardcoded name for demo
                            attendanceViewModel.stopAttendanceSession(context)
                            navController.popBackStack() 
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "End Session", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is LiveSessionUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LiveSessionUiState.Ended -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Session Ended.", style = MaterialTheme.typography.titleLarge)
                }
            }
            is LiveSessionUiState.Active -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    
                    // Header Stats Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Attendance Progress", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val presentCount = presentStudents.count { it.successfulChecks > 0 || it.manualStatus == "PRESENT" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$presentCount / ${state.totalEnrolled}",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { presentCount.toFloat() / state.totalEnrolled.toFloat().coerceAtLeast(1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    val lastSyncTime by liveSessionViewModel.lastSyncTime.collectAsState()
                    Spacer(modifier = Modifier.height(8.dp))
                    lastSyncTime?.let {
                        Text(
                            text = "Last updated at $it", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val maxChecks = presentStudents.maxOfOrNull { it.successfulChecks } ?: 0
                    val minChecksReq = (maxChecks * 0.75).toInt().coerceAtLeast(1)
                    
                    val presentList = presentStudents.filter { it.manualStatus == "PRESENT" || (it.manualStatus == null && it.successfulChecks >= minChecksReq && it.successfulChecks > 0) }
                    val reviewList = presentStudents.filter { it.manualStatus == null && it.successfulChecks > 0 && it.successfulChecks < minChecksReq }
                    val absentList = presentStudents.filter { it.manualStatus == "ABSENT" || (it.manualStatus == null && it.successfulChecks == 0) }

                    // Live Student List
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val reviewsViewModel: com.attendance.app.teacher.presentation.TeacherReviewsViewModel = org.koin.java.KoinJavaComponent.getKoin().get()

                        if (presentList.isNotEmpty()) {
                            item { Text("Present (${presentList.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                            items(presentList, key = { it.studentId }) { student -> StudentCard(student, reviewsViewModel, classId) }
                        }
                        
                        if (reviewList.isNotEmpty()) {
                            item { Text("Under Review (${reviewList.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp)) }
                            items(reviewList, key = { it.studentId }) { student -> StudentCard(student, reviewsViewModel, classId) }
                        }
                        
                        if (absentList.isNotEmpty()) {
                            item { Text("Absent (${absentList.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                            items(absentList, key = { it.studentId }) { student -> StudentCard(student, reviewsViewModel, classId) }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun StudentCard(student: com.attendance.app.teacher.presentation.StudentPresentInfo, reviewsViewModel: com.attendance.app.teacher.presentation.TeacherReviewsViewModel, classId: String) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, style = MaterialTheme.typography.bodyLarge)
                if (student.manualStatus != null) {
                    Text(
                        "Manually Marked: ${student.manualStatus}", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (student.successfulChecks > 0) {
                    val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(student.timeVerified))
                    Text("Synced at $timeString (Checks: ${student.successfulChecks})", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("No checks yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            
            if (student.manualStatus == null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mark Absent") },
                            onClick = { 
                                menuExpanded = false
                                reviewsViewModel.overrideAttendance(java.util.UUID.randomUUID().toString(), student.studentId, "ABSENT", "Teacher manual", classId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark Present") },
                            onClick = { 
                                menuExpanded = false
                                reviewsViewModel.overrideAttendance(java.util.UUID.randomUUID().toString(), student.studentId, "PRESENT", "Teacher manual", classId)
                            }
                        )
                    }
                }
            } else {
                Icon(
                    if (student.manualStatus == "PRESENT") Icons.Default.CheckCircle else Icons.Default.Cancel, 
                    contentDescription = null, 
                    tint = if (student.manualStatus == "PRESENT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
