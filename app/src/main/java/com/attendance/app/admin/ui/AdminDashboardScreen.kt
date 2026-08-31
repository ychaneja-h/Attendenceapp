package com.attendance.app.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.admin.presentation.AdminDashboardState
import com.attendance.app.admin.presentation.AdminDashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    viewModel: AdminDashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin Dashboard") }) },
        bottomBar = { AdminBottomBar(navController, 0) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("System Overview", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            when (val state = uiState) {
                is AdminDashboardState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AdminDashboardState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
                is AdminDashboardState.Success -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(modifier = Modifier.weight(1f), title = "Total Users", value = state.totalUsers.toString())
                        StatCard(modifier = Modifier.weight(1f), title = "Departments", value = state.activeDepartments.toString())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(modifier = Modifier.weight(1f), title = "System Health", value = state.systemHealth)
                        StatCard(modifier = Modifier.weight(1f), title = "Flagged", value = state.flaggedAccounts.toString())
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Admin Actions", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(onClick = { navController.navigate(Screen.AdminUsers) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ManageAccounts, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Suspended Users")
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun AdminBottomBar(navController: NavController, selectedIndex: Int) {
    NavigationBar {
        NavigationBarItem(icon = { Icon(Icons.Default.Dashboard, "Dashboard") }, label = { Text("Dashboard") }, selected = selectedIndex == 0, onClick = { navController.navigate(Screen.AdminDashboard) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.People, "Users") }, label = { Text("Users") }, selected = selectedIndex == 1, onClick = { navController.navigate(Screen.AdminUsers) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Business, "Depts") }, label = { Text("Depts") }, selected = selectedIndex == 2, onClick = { navController.navigate(Screen.AdminDepartments) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") }, selected = selectedIndex == 3, onClick = { navController.navigate(Screen.AdminSettings) { launchSingleTop = true } })
    }
}
