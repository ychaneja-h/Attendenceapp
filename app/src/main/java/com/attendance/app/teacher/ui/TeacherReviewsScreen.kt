package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView
import com.attendance.app.teacher.presentation.ReviewUiState
import com.attendance.app.teacher.presentation.TeacherReviewsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherReviewsScreen(
    navController: NavController,
    viewModel: TeacherReviewsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val attendanceViewModel: com.attendance.app.teacher.presentation.TeacherAttendanceViewModel = koinViewModel()
    val activeClassId by attendanceViewModel.activeClassIdFlow.collectAsState(initial = null)

    LaunchedEffect(activeClassId) {
        if (activeClassId != null) {
            viewModel.loadPendingReviews(activeClassId!!)
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Manual Overrides") },
                actions = {
                    if (uiState is ReviewUiState.Success && (uiState as ReviewUiState.Success).items.isEmpty()) {
                        TextButton(onClick = { /* Submit to Backend */ }) {
                            Text("FINALIZE", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            ) 
        },
        bottomBar = { TeacherBottomBar(navController, 2) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (activeClassId == null) {
                EmptyStateView(
                    title = "No active session", 
                    subtitle = "Start a Live Session to see students needing review"
                )
            } else {
                when (val state = uiState) {
                    is ReviewUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is ReviewUiState.Error -> {
                        Text("Error: ${state.message}", modifier = Modifier.align(Alignment.Center))
                    }
                    is ReviewUiState.Success -> {
                        if (state.items.isEmpty()) {
                            EmptyStateView(
                                title = "No pending reviews", 
                                subtitle = "All students have been verified"
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.items, key = { it.recordId }) { reviewItem ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(reviewItem.studentName, style = MaterialTheme.typography.titleMedium)
                                            Text("Issue: ${reviewItem.issueType}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.overrideAttendance(reviewItem.recordId, reviewItem.studentId, "ABSENT", "Teacher confirmed absent") },
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Keep Absent")
                                                }
                                                
                                                Button(
                                                    onClick = { viewModel.overrideAttendance(reviewItem.recordId, reviewItem.studentId, "PRESENT", "Manual Teacher Override") }
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Mark Present")
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
    }
}
