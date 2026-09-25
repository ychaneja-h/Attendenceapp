package com.attendance.app.student.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.core.ui.components.EmptyStateView
import com.attendance.app.core.ui.components.ClassArtwork
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.profile.presentation.ProfileUiState
import com.attendance.app.profile.presentation.ProfileViewModel
import org.koin.androidx.compose.koinViewModel
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = koinViewModel(),
    classViewModel: ClassViewModel = koinViewModel(),
    attendanceViewModel: com.attendance.app.student.presentation.StudentAttendanceViewModel = koinViewModel(),
    httpClient: io.ktor.client.HttpClient = org.koin.compose.koinInject()
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val attendanceState by attendanceViewModel.state.collectAsState()

    val classes by classViewModel.classes.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileViewModel.loadProfile()
                classViewModel.fetchStudentClasses()
                attendanceViewModel.resumeAttendance(context)
                
                // Sync face status quietly in background
                scope.launch {
                    try {
                        com.attendance.app.face.syncApprovedFace(context, httpClient)
                    } catch (e: Exception) {
                        // Ignore background sync errors
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }) },
        bottomBar = { StudentBottomBar(navController, 0) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showJoinDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Join Class")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is ProfileUiState.Loading -> CircularProgressIndicator()
                is ProfileUiState.Error -> Text("Failed to load profile.")
                is ProfileUiState.Success -> {
                    val profile = (uiState as ProfileUiState.Success).profile
                    
                    // Profile Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                                                Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!profile.photoUrl.isNullOrEmpty()) {
                                coil.compose.AsyncImage(
                                    model = if (profile.photoUrl?.startsWith("/") == true) java.io.File(profile.photoUrl!!) else "${com.attendance.app.core.network.Constants.BASE_URL.replace("/api", "")}${profile.photoUrl}",
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
                                Text("Ready for your next class?", style = MaterialTheme.typography.bodyMedium)
                                }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                }
            }

            attendanceState.activeStudentAttempt?.let { attempt ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("You're checked in", style = MaterialTheme.typography.titleMedium)
                        Text("Keep Bluetooth on until class ends.")
                        Button(onClick = { navController.navigate(Screen.StudentClassDetail(attempt.classId)) }) {
                            Text("Open attendance")
                        }
                    }
                }
            }
            attendanceState.serviceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Your classes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            if (classes.isEmpty()) {
                EmptyStateView("No classes yet", "Tap + to join your first class")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        items(classes, key = { it.id }) { cls ->
                        val isPending = cls.memberStatus != "ACTIVE"
                        Card(
                            onClick = { 
                                if (!isPending) navController.navigate(Screen.StudentClassDetail(cls.id)) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ClassArtwork()
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(cls.name, style = MaterialTheme.typography.titleMedium)
                                        Text(cls.teacherName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isPending) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant, 
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("Pending", modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        if (showJoinDialog) {
            var classCode by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showJoinDialog = false },
                title = { Text("Join Class") },
                text = {
                    OutlinedTextField(
                        value = classCode,
                        onValueChange = { classCode = it.uppercase() },
                        label = { Text("Class Code") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            classViewModel.joinClass(classCode) { showJoinDialog = false }
                        },
                        enabled = classCode.isNotBlank() && !classViewModel.isLoading
                    ) {
                        Text("Join")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun StudentBottomBar(navController: NavController, selectedIndex: Int) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedIndex == 0,
            onClick = { navController.navigate(Screen.StudentHome) { popUpTo(0) } }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = selectedIndex == 3,
            onClick = { navController.navigate(Screen.StudentProfile) { popUpTo(0) } }
        )
    }
}






















