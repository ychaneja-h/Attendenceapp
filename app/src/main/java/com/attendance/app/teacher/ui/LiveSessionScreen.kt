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
    liveSessionViewModel: LiveSessionViewModel = koinViewModel(),
    attendanceViewModel: TeacherAttendanceViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by liveSessionViewModel.uiState.collectAsState()
    val presentStudents by liveSessionViewModel.presentStudents.collectAsState()
    val recheckInterval by liveSessionViewModel.recheckIntervalMinutes.collectAsState()

    LaunchedEffect(classId) {
        liveSessionViewModel.loadSession(classId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Session") },
                actions = {
                    IconButton(onClick = { 
                        liveSessionViewModel.endSession(context, "Recent Class") // Hardcoded name for demo
                        attendanceViewModel.stopAttendanceSession(context)
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.Default.Stop, contentDescription = "End Session", tint = MaterialTheme.colorScheme.error)
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
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${presentStudents.size} / ${state.totalEnrolled}",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { presentStudents.size.toFloat() / state.totalEnrolled.toFloat().coerceAtLeast(1f) },
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
                    Text("Verified Students (${presentStudents.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Live Student List
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presentStudents, key = { it.studentId }) { student ->
                            var menuExpanded by remember { mutableStateOf(false) }
                            val reviewsViewModel: com.attendance.app.teacher.presentation.TeacherReviewsViewModel = koinViewModel()
                            LaunchedEffect(Unit) {
                                reviewsViewModel.loadPendingReviews(classId)
                            }
                            
                            Card {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(student.name, style = MaterialTheme.typography.bodyLarge)
                                        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(student.timeVerified))
                                        Text("Synced at $timeString (Checks: ${student.successfulChecks})", style = MaterialTheme.typography.bodySmall)
                                    }
                                    
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
                                                    reviewsViewModel.overrideAttendance("", student.studentId, "ABSENT", "Teacher marked absent from Live Session")
                                                    // In a real app we'd refresh the list, but it polls anyway
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Mark Present") },
                                                onClick = { 
                                                    menuExpanded = false
                                                    reviewsViewModel.overrideAttendance("", student.studentId, "PRESENT", "Teacher marked present from Live Session")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
