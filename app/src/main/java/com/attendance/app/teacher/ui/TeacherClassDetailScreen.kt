package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.network.ReportDownloader
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.core.ui.components.ClassArtwork
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherClassDetailScreen(
    classId: String,
    navController: NavController,
    viewModel: ClassViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val downloader = remember { ReportDownloader(context) }
    val classes by viewModel.classes.collectAsState()
    val selectedClass = classes.firstOrNull { it.id == classId }

    LaunchedEffect(classId) {
        viewModel.fetchRoster(classId)
    }

    var selectedTabIndex by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedClass?.name ?: "Class") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
IconButton(onClick = {
                        val month = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            java.time.YearMonth.now().toString()
                        } else {
                            val now = java.util.Calendar.getInstance()
                            "${now.get(java.util.Calendar.YEAR)}-${(now.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}"
                        }
                        downloader.downloadClassReport(classId, month)
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download Report")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Class")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedButton(onClick = {
                navController.navigate(com.attendance.app.core.ui.navigation.Screen.TeacherClassHistory(classId))
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Attendance history")
            }
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Pending") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Approved") })
                Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Rejected") })
            }

            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val currentStatus = when (selectedTabIndex) {
                    0 -> "PENDING"
                    1 -> "APPROVED"
                    2 -> "REJECTED"
                    else -> "PENDING"
                }

                val currentList = viewModel.roster.filter { it.status == currentStatus }

                if (currentList.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        ClassArtwork()
                        Text(when (currentStatus) { "PENDING" -> "No join requests"; "REJECTED" -> "No rejected requests"; else -> "No students yet" }, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentList) { request ->
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(request.studentName, style = MaterialTheme.typography.titleMedium)
                                        Text("College ID: ${request.collegeId}", style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row {
if (currentStatus == "APPROVED") {
                                             IconButton(onClick = {
                                                 val month = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                     java.time.YearMonth.now().toString()
                                                 } else {
                                                     val now = java.util.Calendar.getInstance()
                                                     "${now.get(java.util.Calendar.YEAR)}-${(now.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}"
                                                 }
                                                 downloader.downloadStudentReport(classId, request.studentId, month)
                                             }) {
                                                Icon(Icons.Default.Download, contentDescription = "Download Student Report")
                                            }
                                            IconButton(onClick = { viewModel.removeStudent(classId, request.studentId) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove Student", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }

                                        if (currentStatus == "PENDING") {
                                            IconButton(onClick = { viewModel.rejectRequest(classId, request.id) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                                            }
                                            IconButton(onClick = { viewModel.approveRequest(classId, request.id) }) {
                                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF4CAF50))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Class") },
            text = { Text("Are you sure you want to delete this class? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteClass(classId) {
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
