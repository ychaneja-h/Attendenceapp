package com.attendance.app.student.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
    httpClient: io.ktor.client.HttpClient = org.koin.compose.koinInject()
) {
    val uiState by profileViewModel.uiState.collectAsState()

    val summary by profileViewModel.studentSummary.collectAsState()
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
                
                // Sync face status quietly in background
                scope.launch {
                    try {
                        val prefs = context.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                        val response = httpClient.get("${com.attendance.app.core.network.Constants.BASE_URL}/requests/me")
                        if (response.status.value in 200..299) {
                            val array = org.json.JSONArray(response.bodyAsText())
                            var hasPending = false
                            var latestApproved: org.json.JSONObject? = null
                            for (i in 0 until array.length()) {
                                val req = array.getJSONObject(i)
                                if (req.getString("requestType") == "FACE_REGISTRATION") {
                                    val status = req.getString("status")
                                    if (status == "PENDING") {
                                        hasPending = true
                                        break
                                    } else if (status == "APPROVED" && latestApproved == null) {
                                        latestApproved = req
                                    }
                                }
                            }
                                                                                    if (hasPending) {
                                prefs.edit().putBoolean("face_approval_pending", true).apply()
                            } else if (latestApproved != null) {
                                prefs.edit().putBoolean("face_approval_pending", false).apply()
                                val pendingFace = prefs.getString("pending_face_embedding", null)
                                if (pendingFace != null) {
                                    prefs.edit().putString("registered_face_embedding", pendingFace).remove("pending_face_embedding").apply()
                                }
                            }
                            
                            // If we still don't have a registered face locally, try to fetch it from backend
                            if (prefs.getString("registered_face_embedding", null) == null) {
                                val faceRes = httpClient.get("${com.attendance.app.core.network.Constants.BASE_URL}/users/me/face")
                                if (faceRes.status.value in 200..299) {
                                    val obj = org.json.JSONObject(faceRes.bodyAsText())
                                    val embed = obj.optString("faceEmbedding", "")
                                    if (embed.isNotEmpty()) {
                                        prefs.edit().putString("registered_face_embedding", embed).apply()
                                    }
                                }
                            }
                        }
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
        topBar = { TopAppBar(title = { Text("Dashboard") }) },
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
                                Text(profile.email, style = MaterialTheme.typography.bodyMedium)
                                }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("This Month's Attendance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Present", color = Color(0xFF2E7D32), style = MaterialTheme.typography.titleMedium)
                                    Text("${summary!!.totalPresent}", color = Color(0xFF2E7D32), style = MaterialTheme.typography.headlineMedium)
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Absent", color = Color(0xFFC62828), style = MaterialTheme.typography.titleMedium)
                                    Text("${summary!!.totalAbsent}", color = Color(0xFFC62828), style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Enrolled Classes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            if (classes.isEmpty()) {
                EmptyStateView("You are not enrolled in any classes.", "Tap + to join a class")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(classes, key = { it.id }) { cls ->
                        Card(
                            onClick = { navController.navigate(Screen.StudentClassDetail(cls.id)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(cls.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Code: ${cls.id.substring(0, 5)} � Section: ${cls.section}", style = MaterialTheme.typography.bodyMedium)
                                Text("Teacher: ${cls.teacherName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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




















