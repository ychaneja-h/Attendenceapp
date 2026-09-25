package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.data.attendance.SessionHistoryItem
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.teacher.presentation.TeacherHistoryViewModel
import com.attendance.app.core.ui.components.AttendanceCounts
import com.attendance.app.core.ui.components.AttendanceSyncNote
import com.attendance.app.core.ui.components.ClassArtwork
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHistoryScreen(
    navController: NavController,
    classId: String? = null,
    viewModel: TeacherHistoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val actionError by viewModel.error.collectAsState()
    val sessions = state.sessions.filter { classId == null || it.classId == classId }.sortedByDescending { it.startedAtMs }
    var deleting by remember { mutableStateOf<SessionHistoryItem?>(null) }
    LaunchedEffect(classId) { viewModel.refresh() }
    deleting?.let { session ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete session?") }, text = {
            Text("Remove this ${session.className} session and its attendance records?")
        }, confirmButton = { TextButton(onClick = { viewModel.deleteSession(session.sessionId); deleting = null }) { Text("Delete session") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Attendance history") }, navigationIcon = {
            if (classId != null) IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Sync history") } })
    }, bottomBar = { if (classId == null) TeacherBottomBar(navController, 2) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AttendanceSyncNote(state.syncMessage)
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (sessions.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ClassArtwork()
                    Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
                    Text("Your attendance sessions will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(sessions, key = { it.sessionId }) { session ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = {
                    navController.navigate(Screen.LiveSession(classId = session.classId, sessionId = session.sessionId, isHistory = state.activeTeacherSession?.sessionId != session.sessionId))
                }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.className, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { deleting = session }, enabled = state.activeTeacherSession?.sessionId != session.sessionId) { Icon(Icons.Default.Delete, "Delete this session") }
                        }
                        Text(Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")))
                        Spacer(Modifier.height(4.dp))
                        AttendanceCounts(session.students.count { it.status == "PRESENT" }, session.students.count { it.status == "ABSENT" }, session.students.count { it.status == "UNDER_REVIEW" })
                        Text(if (session.stoppedAtMs == null) "Live now" else if (session.syncPending) "Updating attendance" else "Session complete", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
