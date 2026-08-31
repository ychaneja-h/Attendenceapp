package com.attendance.app.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.admin.presentation.AdminDashboardViewModel
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.shared.dto.HodProfileDto
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    viewModel: AdminDashboardViewModel = koinViewModel()
) {
    val pendingHods by viewModel.pendingHods.collectAsState()
    val activeHods by viewModel.activeHods.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin Dashboard") }) },
        bottomBar = { AdminBottomBar(navController, 0) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Pending Requests") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Active HODs") }
                )
            }
            if (selectedTabIndex == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(pendingHods) { hod ->
                        HodCard(
                            hod = hod,
                            isPending = true,
                            onApprove = { viewModel.approveHod(hod.id) },
                            onReject = { viewModel.rejectHod(hod.id) },
                            onRemove = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(activeHods) { hod ->
                        HodCard(
                            hod = hod,
                            isPending = false,
                            onApprove = {},
                            onReject = {},
                            onRemove = { viewModel.deleteHod(hod.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HodCard(
    hod: HodProfileDto,
    isPending: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = hod.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "ID: ${hod.facultyId}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: ${hod.status}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isPending) {
                    TextButton(onClick = onReject) { Text("Reject") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onApprove) { Text("Approve") }
                } else {
                    OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
fun AdminBottomBar(navController: NavController, selectedIndex: Int) {
    NavigationBar {
        NavigationBarItem(icon = { Icon(Icons.Default.Dashboard, "Dashboard") }, label = { Text("Dashboard") }, selected = selectedIndex == 0, onClick = { navController.navigate(Screen.AdminHome) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.People, "Users") }, label = { Text("Users") }, selected = selectedIndex == 1, onClick = { navController.navigate(Screen.AdminUsers) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Business, "Depts") }, label = { Text("Depts") }, selected = selectedIndex == 2, onClick = { navController.navigate(Screen.AdminDepartments) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") }, selected = selectedIndex == 3, onClick = { navController.navigate(Screen.AdminSettings) { launchSingleTop = true } })
    }
}
