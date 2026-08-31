package com.attendance.app.hod.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodTeachersScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Teachers") }) }, bottomBar = { HodBottomBar(navController, 1) }) { padding ->
        EmptyStateView(title = "Department Teachers", subtitle = "Teacher list will appear here", modifier = Modifier.padding(padding))
    }
}
