package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView
import com.attendance.app.teacher.presentation.TeacherHistoryViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHistoryScreen(
    navController: NavController,
    viewModel: TeacherHistoryViewModel = koinViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Offline History") }) },
        bottomBar = { TeacherBottomBar(navController, 2) }
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyStateView(title = "No attendance history", subtitle = "Past sessions will appear here", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions) { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(session.className, style = MaterialTheme.typography.titleMedium)
                            Text(session.dateString, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Students present: ${session.studentCount}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
