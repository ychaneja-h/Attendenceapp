package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.teacher.presentation.LiveSessionViewModel
import com.attendance.app.teacher.presentation.TeacherAttendanceViewModel
import com.attendance.app.core.ui.components.AttendanceCounts
import com.attendance.app.core.ui.components.AttendanceDetails
import com.attendance.app.core.ui.components.AttendanceStatus
import com.attendance.app.core.ui.components.AttendanceSyncNote
import com.attendance.app.core.ui.components.attendanceLabel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    classId: String,
    navController: NavController,
    sessionId: String? = null,
    isHistory: Boolean = false,
    liveSessionViewModel: LiveSessionViewModel = koinViewModel(),
    attendanceViewModel: TeacherAttendanceViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by liveSessionViewModel.state.collectAsState()
    val actionError by liveSessionViewModel.error.collectAsState()
    // Remember the selected session even when stopping clears the active pointer.
    var selectedId by rememberSaveable(sessionId, classId) { mutableStateOf(sessionId) }
    LaunchedEffect(state.activeTeacherSession?.sessionId) {
        if (selectedId == null) selectedId = state.activeTeacherSession?.takeIf { it.classId == classId }?.sessionId
    }
    val session = state.sessions.firstOrNull { it.sessionId == selectedId }
    val isActive = !isHistory && session != null && state.activeTeacherSession?.sessionId == session.sessionId
    var confirmStop by remember { mutableStateOf(false) }
    var correction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var correctionReason by remember { mutableStateOf("") }
    LaunchedEffect(classId, sessionId) { liveSessionViewModel.refresh() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, attendanceViewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) attendanceViewModel.resumeAttendance(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Stop attendance?") },
        text = { Text("Ask students to check out within five minutes.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; attendanceViewModel.stopAttendanceSession(context) }) { Text("Stop session") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continue session") } }
    )
    correction?.let { (studentId, status) ->
        AlertDialog(onDismissRequest = { correction = null }, title = { Text("Mark ${status.lowercase()}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(correctionReason, { correctionReason = it }, label = { Text("Reason (optional)") })
            }
        }, confirmButton = { TextButton(onClick = {
            session?.let { liveSessionViewModel.overrideStudent(it.sessionId, studentId, status, correctionReason) }
            correction = null
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { correction = null }) { Text("Cancel") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (isActive) "Live attendance" else "Session history") }, navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            IconButton(onClick = { liveSessionViewModel.refresh() }) { Icon(Icons.Default.Refresh, "Sync attendance") }
            if (isActive) TextButton(onClick = { confirmStop = true }, enabled = !attendanceViewModel.isStoppingSession) { Text("Stop", color = MaterialTheme.colorScheme.error) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (session == null) Text("Session is unavailable or has been deleted.")
                else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(session.className, style = MaterialTheme.typography.headlineSmall)
                        Text(Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a")), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AttendanceCounts(session.students.count { it.status == "PRESENT" }, session.students.count { it.status == "ABSENT" }, session.students.count { it.status == "UNDER_REVIEW" })
                        if (session.stoppedAtMs != null) Text("Session ended", style = MaterialTheme.typography.labelLarge)
                        if (session.syncPending) Text("Attendance updating", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (session.legacy) Text("Older record", style = MaterialTheme.typography.bodySmall)
                    }
                }
                state.serviceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                attendanceViewModel.sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AttendanceSyncNote(state.syncMessage)
            }
            for (status in listOf("PRESENT", "UNDER_REVIEW", "ABSENT")) {
                val students = session?.students?.filter { it.status == status }.orEmpty()
                if (students.isNotEmpty()) item { Text("${attendanceLabel(status)} · ${students.size}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium) }
                items(students, key = { it.studentId }) { student ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(student.name, style = MaterialTheme.typography.titleMedium)
                            AttendanceStatus(student.status, student.provisional)
                            AttendanceDetails {
                                Text("Checks: ${student.successfulChecks} of ${student.totalChecks}")
                                student.lastReceivedAtMs?.let { time -> Text("Last check: " + Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))) }
                                Text("Check-in: ${attendanceLabel(student.entryFaceStatus)}")
                                Text("Check-out: ${attendanceLabel(student.exitFaceStatus)}")
                                student.manualStatus?.let { Text("Your decision: ${attendanceLabel(it)}") }
                                student.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (session?.legacy != true) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { correctionReason = ""; correction = student.studentId to "PRESENT" }) { Text("Mark present") }
                                TextButton(onClick = { correctionReason = ""; correction = student.studentId to "ABSENT" }) { Text("Mark absent") }
                            }
                        }
                    }
                }
            }
        }
    }
}
