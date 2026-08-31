package com.attendance.app.admin.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Users") }) }, bottomBar = { AdminBottomBar(navController, 1) }) { padding ->
        EmptyStateView(title = "User Management", subtitle = "Manage students, teachers, HODs", modifier = Modifier.padding(padding))
    }
}
