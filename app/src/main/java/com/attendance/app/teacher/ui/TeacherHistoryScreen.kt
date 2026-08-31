package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHistoryScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) },
        bottomBar = { TeacherBottomBar(navController, 2) }
    ) { padding ->
        EmptyStateView(title = "No attendance history", subtitle = "Past sessions will appear here", modifier = Modifier.padding(padding))
    }
}
