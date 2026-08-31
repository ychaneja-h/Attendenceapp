package com.attendance.app.student.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAttendanceScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Attendance") }) },
        bottomBar = { StudentBottomBar(navController, 2) }
    ) { padding ->
        EmptyStateView(
            title = "No attendance history",
            subtitle = "Your attendance records will appear here",
            modifier = Modifier.padding(padding)
        )
    }
}
