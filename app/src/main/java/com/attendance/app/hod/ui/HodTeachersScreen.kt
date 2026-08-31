package com.attendance.app.hod.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.hod.presentation.HodDashboardViewModel
import com.attendance.shared.dto.TeacherProfileDto
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HodTeachersScreen(
    navController: NavController,
    viewModel: HodDashboardViewModel = koinViewModel()
) {
    val pendingTeachers by viewModel.pendingTeachers.collectAsState()
    val activeTeachers by viewModel.activeTeachers.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Teachers") }) },
        bottomBar = { HodBottomBar(navController, 1) }
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
                    text = { Text("Active Teachers") }
                )
            }
            if (selectedTabIndex == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(pendingTeachers) { teacher ->
                        TeacherCard(
                            teacher = teacher,
                            isPending = true,
                            onApprove = { viewModel.approveTeacher(teacher.id) },
                            onReject = { viewModel.rejectTeacher(teacher.id) },
                            onRemove = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    items(activeTeachers) { teacher ->
                        TeacherCard(
                            teacher = teacher,
                            isPending = false,
                            onApprove = {},
                            onReject = {},
                            onRemove = { viewModel.deleteTeacher(teacher.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherCard(
    teacher: TeacherProfileDto,
    isPending: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = teacher.name, style = MaterialTheme.typography.titleMedium)
            Text(text = "ID: ${teacher.facultyId}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Phone: ${teacher.phone}", style = MaterialTheme.typography.bodyMedium)
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
