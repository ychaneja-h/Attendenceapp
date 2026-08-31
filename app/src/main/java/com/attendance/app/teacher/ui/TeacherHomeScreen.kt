package com.attendance.app.teacher.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.attendance.app.core.ui.components.EmptyStateView
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.teacher.presentation.TeacherAttendanceViewModel
import com.attendance.app.classes.presentation.ClassViewModel
import com.attendance.app.profile.presentation.ProfileUiState
import com.attendance.app.profile.presentation.ProfileViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    navController: NavController,
    classViewModel: ClassViewModel = koinViewModel(),
    attendanceViewModel: TeacherAttendanceViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val classes by classViewModel.classes.collectAsState()
    val isAdvertising by attendanceViewModel.isAdvertising.collectAsState()
    val context = LocalContext.current

    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedClassName by remember { mutableStateOf<String?>(null) }
    var sessionInterval by remember { mutableStateOf(10f) }
    var sessionMinChecks by remember { mutableStateOf(3f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted && selectedClassId != null) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(context, "Please turn on Bluetooth first!", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
                                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            if (!isLocationEnabled) {
                Toast.makeText(context, "Please turn on Location (GPS) in your phone settings! It is required for BLE.", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }

            attendanceViewModel.startAttendanceSession(
                context, 
                selectedClassId!!, 
                selectedClassName ?: "Class", 
                sessionInterval.toInt(), 
                sessionMinChecks.toInt()
            ) {
                navController.navigate(Screen.LiveSession(selectedClassId!!, sessionInterval.toInt(), sessionMinChecks.toInt()))
                selectedClassId = null
                selectedClassName = null
            }
        } else {
            Toast.makeText(context, "Permissions are required to launch session.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        classViewModel.fetchTeacherClasses()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home - Live Attendance") }) },
        bottomBar = { TeacherBottomBar(navController, 0) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (uiState is ProfileUiState.Success) {
                val profile = (uiState as ProfileUiState.Success).profile
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

            Text("Your Classes", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            if (classes.isEmpty()) {
                EmptyStateView(title = "No classes found", subtitle = "Go to Classes tab to create one")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(classes) { cls ->
                        val isActive = attendanceViewModel.activeClassId == cls.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = cls.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Code: ", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (isActive) {
                                        Button(
                                            onClick = { attendanceViewModel.stopAttendanceSession(context) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Stop")
                                        }
                                    } else {
                                        Button(onClick = {
                                            selectedClassId = cls.id
                                            selectedClassName = cls.name
                                        }) {
                                            Text("Start")
                                        }
                                    }
                                }
                                if (isActive) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { navController.navigate(Screen.LiveSession(cls.id)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("View Active Session")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (selectedClassId != null && selectedClassName != null) {
        LaunchedEffect(selectedClassId) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            permissionLauncher.launch(perms)
        }
        
        if (attendanceViewModel.sessionError != null) {
            AlertDialog(
                onDismissRequest = { attendanceViewModel.sessionError.let { selectedClassId = null; selectedClassName = null } },
                title = { Text("Error") },
                text = { Text(attendanceViewModel.sessionError!!, color = MaterialTheme.colorScheme.error) },
                confirmButton = {
                    TextButton(onClick = { selectedClassId = null; selectedClassName = null }) {
                        Text("OK")
                    }
                }
            )
        } else if (attendanceViewModel.isStartingSession) {
             AlertDialog(
                onDismissRequest = { },
                title = { Text("Starting Session...") },
                text = { CircularProgressIndicator() },
                confirmButton = { }
            )
        }
    }
}

@Composable
fun TeacherBottomBar(navController: NavController, selectedIndex: Int) {
    NavigationBar {
        NavigationBarItem(icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Home") }, selected = selectedIndex == 0, onClick = { navController.navigate(Screen.TeacherHome) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Class, "Classes") }, label = { Text("Classes") }, selected = selectedIndex == 1, onClick = { navController.navigate(Screen.TeacherClasses) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.History, "History") }, label = { Text("History") }, selected = selectedIndex == 2, onClick = { navController.navigate(Screen.TeacherHistory) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.RateReview, "Requests") }, label = { Text("Requests") }, selected = selectedIndex == 3, onClick = { navController.navigate(Screen.TeacherRequests) { launchSingleTop = true } })
        NavigationBarItem(icon = { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") }, selected = selectedIndex == 4, onClick = { navController.navigate(Screen.TeacherProfile) { launchSingleTop = true } })
    }
}
