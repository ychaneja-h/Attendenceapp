package com.attendance.app.admin.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDepartmentsScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Departments") }) }, bottomBar = { AdminBottomBar(navController, 2) }) { padding ->
        EmptyStateView(title = "Department Management", subtitle = "Manage departments and academic years", modifier = Modifier.padding(padding))
    }
}
