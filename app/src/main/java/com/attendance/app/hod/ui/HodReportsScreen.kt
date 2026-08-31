package com.attendance.app.hod.ui
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodReportsScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }) }, bottomBar = { HodBottomBar(navController, 2) }) { padding ->
        EmptyStateView(title = "Reports", subtitle = "Department reports will appear here", modifier = Modifier.padding(padding))
    }
}
