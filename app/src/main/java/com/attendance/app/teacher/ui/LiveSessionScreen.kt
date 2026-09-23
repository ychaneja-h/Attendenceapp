package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        text = { Text("Tell students to scan their faces to leave. Their five-minute exit window starts when you stop this session.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; attendanceViewModel.stopAttendanceSession(context) }) { Text("Stop session") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continue session") } }
    )
    correction?.let { (studentId, status) ->
        AlertDialog(onDismissRequest = { correction = null }, title = { Text("Mark ${status.lowercase()}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your decision is saved offline and takes priority when records sync. You can change it again later.")
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
                    Text(session.className, style = MaterialTheme.typography.headlineSmall)
                    Text(Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")))
                    Text("Checks received on this phone · five-minute rounds")
                    Text("Present: ${session.students.count { it.status == "PRESENT" }} · Absent: ${session.students.count { it.status == "ABSENT" }} · Under Review: ${session.students.count { it.status == "UNDER_REVIEW" }}")
                    if (session.stoppedAtMs != null) Text("Session stopped. Students must complete face checkout within five minutes.")
                    if (session.syncPending) Text("Provisional results. Face timing and the 75% rule are reconciled after both phones sync.", style = MaterialTheme.typography.bodySmall)
                    if (session.legacy) Text("Saved from the previous app version; detailed check evidence may be unavailable.", style = MaterialTheme.typography.bodySmall)
                }
                state.serviceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                attendanceViewModel.sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            for (status in listOf("PRESENT", "UNDER_REVIEW", "ABSENT")) {
                val students = session?.students?.filter { it.status == status }.orEmpty()
                if (students.isNotEmpty()) item { Text("${status.replace('_', ' ')} (${students.size})", style = MaterialTheme.typography.titleMedium) }
                items(students, key = { it.studentId }) { student ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(student.name, style = MaterialTheme.typography.titleMedium)
                            Text(student.status.replace('_', ' ') + if (student.provisional) " · provisional" else "")
                            Text("Checks: ${student.successfulChecks} out of ${student.totalChecks}")
                            student.lastReceivedAtMs?.let { time -> Text("Last received: " + Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))) }
                            Text("Entry face: ${student.entryFaceStatus.replace('_', ' ')} · Exit face: ${student.exitFaceStatus.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
                            student.manualStatus?.let { Text("Teacher decision: ${it.replace('_', ' ')}") }
                            student.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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
