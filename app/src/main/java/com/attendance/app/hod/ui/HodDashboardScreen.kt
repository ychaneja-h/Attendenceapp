package com.attendance.app.hod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.hod.presentation.HodDashboardState
import com.attendance.app.hod.presentation.HodDashboardViewModel
import com.attendance.app.profile.presentation.ProfileUiState
import com.attendance.app.profile.presentation.ProfileViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodDashboardScreen(
    navController: NavController,
    viewModel: HodDashboardViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profileUiState by profileViewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("HOD Dashboard") }) },
        bottomBar = { HodBottomBar(navController, 0) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (profileUiState is ProfileUiState.Success) {
                val profile = (profileUiState as ProfileUiState.Success).profile
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (profile.photoUrl != null) {
                            AsyncImage(
                                model = profile.photoUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person, 
                                    contentDescription = "Profile Photo",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(profile.email, style = MaterialTheme.typography.bodyMedium)
                            Text(profile.phone, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text("Department Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            when (val state = uiState) {
                is HodDashboardState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HodDashboardState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
                is HodDashboardState.Success -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(modifier = Modifier.weight(1f), title = "Teachers", value = state.totalTeachers.toString(), icon = Icons.Default.People)
                        StatCard(modifier = Modifier.weight(1f), title = "Classes", value = state.totalClasses.toString(), icon = Icons.Default.Class)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(modifier = Modifier.weight(1f), title = "Avg Attendance", value = "${state.averageAttendance}%", icon = Icons.Default.Assessment)
                        StatCard(modifier = Modifier.weight(1f), title = "Pending Approvals", value = state.pendingTeacherApprovals.toString(), icon = Icons.Default.Pending)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text("Quick Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { navController.navigate(Screen.HodReports) }, 
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Export Monthly CSV Reports", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector? = null) {
    Card(
        modifier = modifier, 
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HodBottomBar(navController: NavController, selectedIndex: Int) {
    NavigationBar {
        NavigationBarItem(icon = { Icon(Icons.Default.Dashboard, "Dashboard") }, label = { Text("Dashboard") }, selected = selectedIndex == 0, onClick = { navController.navigate(Screen.HodDashboard) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.People, "Teachers") }, label = { Text("Teachers") }, selected = selectedIndex == 1, onClick = { navController.navigate(Screen.HodTeachers) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Assessment, "Reports") }, label = { Text("Reports") }, selected = selectedIndex == 2, onClick = { navController.navigate(Screen.HodReports) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") }, selected = selectedIndex == 3, onClick = { navController.navigate(Screen.HodProfile) { launchSingleTop = true } })
    }
}
