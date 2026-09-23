package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this session's history?") }, text = {
            Text("This removes only the selected ${session.className} session and its attendance results. The deletion syncs to the backend and prevents late uploads from bringing it back.")
        }, confirmButton = { TextButton(onClick = { viewModel.deleteSession(session.sessionId); deleting = null }) { Text("Delete session") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Class history") }, navigationIcon = {
            if (classId != null) IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Sync history") } })
    }, bottomBar = { if (classId == null) TeacherBottomBar(navController, 2) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Sessions stay here until you delete them. Late uploads can update attendance and face-review results.", style = MaterialTheme.typography.bodySmall)
                state.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (sessions.isEmpty()) Text("No saved attendance sessions.", Modifier.padding(top = 16.dp))
            }
            items(sessions, key = { it.sessionId }) { session ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = {
                    navController.navigate(Screen.LiveSession(classId = session.classId, sessionId = session.sessionId, isHistory = state.activeTeacherSession?.sessionId != session.sessionId))
                }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.className, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { deleting = session }, enabled = state.activeTeacherSession?.sessionId != session.sessionId) { Icon(Icons.Default.Delete, "Delete this session") }
                        }
                        Text(Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")))
                        Text("Present: ${session.students.count { it.status == "PRESENT" }} · Absent: ${session.students.count { it.status == "ABSENT" }} · Under Review: ${session.students.count { it.status == "UNDER_REVIEW" }}")
                        Text(if (session.stoppedAtMs == null) "Active session" else if (session.syncPending) "Saved · pending reconciliation" else "Saved · synced")
                        Text("Open for student names, checks and teacher corrections", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
