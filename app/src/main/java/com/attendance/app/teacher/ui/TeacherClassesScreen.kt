package com.attendance.app.teacher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.attendance.app.core.ui.components.ClassArtwork
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherClassesScreen(
    navController: NavController,
    viewModel: ClassViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.fetchTeacherClasses()
    }
    
    val classes by viewModel.classes.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Classes") }) },
        bottomBar = { TeacherBottomBar(navController, 1) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.CreateClass) }) {
                Icon(Icons.Default.Add, "Create Class")
            }
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (classes.isEmpty()) {
            EmptyStateView(title = "No classes yet", subtitle = "Tap + to create your first class", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(classes, key = { it.id }) { classItem ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        onClick = { navController.navigate(Screen.TeacherClassDetail(classItem.id)) }
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ClassArtwork()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(classItem.name, style = MaterialTheme.typography.titleMedium)
                                Text("${classItem.studentCount} students · ${classItem.section}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Code ${classItem.classCode}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
