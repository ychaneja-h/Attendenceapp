package com.attendance.app.student.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.core.ui.components.EmptyStateView
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.classes.presentation.ClassViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentClassesScreen(
    navController: NavController,
    viewModel: ClassViewModel = koinViewModel()
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.fetchStudentClasses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val classes by viewModel.classes.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Classes") }) },
        bottomBar = { StudentBottomBar(navController, 1) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.JoinClass) }) {
                Icon(Icons.Default.Add, "Join Class")
            }
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (classes.isEmpty()) {
            EmptyStateView(title = "No classes yet", subtitle = "Tap + to join a class with a code", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(classes) { classItem ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { 
                            if (classItem.memberStatus != "PENDING_APPROVAL") {
                                navController.navigate(Screen.StudentClassDetail(classItem.id)) 
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = classItem.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Teacher: ${classItem.teacherName}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Section: ${classItem.section} | Sem: ${classItem.semester}", style = MaterialTheme.typography.labelMedium)
                            
                            if (classItem.memberStatus == "PENDING_APPROVAL") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pending Approval",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
