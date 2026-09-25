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
import androidx.compose.runtime.saveable.rememberSaveable
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
    val activeClassId by attendanceViewModel.activeClassIdFlow.collectAsState()
    val attendanceState by attendanceViewModel.state.collectAsState()
    val context = LocalContext.current

    var selectedClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedClassName by rememberSaveable { mutableStateOf<String?>(null) }
    var showBackgroundPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val startSelectedSession: () -> Unit = start@{
        val classId = selectedClassId ?: return@start
        if (attendanceViewModel.isStartingSession) return@start
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(context, "Please turn on Bluetooth first!", Toast.LENGTH_LONG).show()
                return@start
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || 
                                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            if (!isLocationEnabled) {
                Toast.makeText(context, "Please turn on Location (GPS) in your phone settings! It is required for BLE.", Toast.LENGTH_LONG).show()
                return@start
            }

            attendanceViewModel.startAttendanceSession(
                context, 
                classId,
                selectedClassName ?: "Class"
            ) {
                navController.navigate(Screen.LiveSession(classId, sessionId = attendanceViewModel.state.value.activeTeacherSession?.sessionId))
                selectedClassId = null
                selectedClassName = null
            }
        } catch (_: SecurityException) {
            Toast.makeText(context, "Grant the Bluetooth and location permissions before starting.", Toast.LENGTH_LONG).show()
        }
    }
    val hasBackgroundLocation = {
        Build.VERSION.SDK_INT !in 29..30 || androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val backgroundSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasBackgroundLocation()) startSelectedSession()
        else Toast.makeText(context, "Choose Allow all the time for location to receive attendance while this phone is locked.", Toast.LENGTH_LONG).show()
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSelectedSession()
        else Toast.makeText(context, "Location access in the background is required while attendance is active.", Toast.LENGTH_LONG).show()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }.values.all { it }
        if (allGranted && selectedClassId != null) {
            if (hasBackgroundLocation()) startSelectedSession() else showBackgroundPermissionDialog = true
        } else {
            Toast.makeText(context, "Permissions are required to launch session.", Toast.LENGTH_LONG).show()
        }
    }
    if (showBackgroundPermissionDialog) AlertDialog(
        onDismissRequest = { showBackgroundPermissionDialog = false },
        title = { Text("Receive checks while your phone is locked") },
        text = { Text("To keep attendance active, open Permissions → Location and select Allow all the time.") },
        confirmButton = { TextButton(onClick = {
            showBackgroundPermissionDialog = false
            if (Build.VERSION.SDK_INT == 29) backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else backgroundSettingsLauncher.launch(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")))
        }) { Text(if (Build.VERSION.SDK_INT == 29) "Continue" else "Open settings") } },
        dismissButton = { TextButton(onClick = { showBackgroundPermissionDialog = false }) { Text("Cancel") } }
    )
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Stop attendance?") },
        text = { Text("Ask students to check out within five minutes.") },
        confirmButton = { TextButton(onClick = { confirmStop = false; attendanceViewModel.stopAttendanceSession(context) }) { Text("Stop session") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continue session") } }
    )

    val teacherLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(teacherLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileViewModel.loadProfile()
                classViewModel.fetchTeacherClasses()
                attendanceViewModel.resumeAttendance(context)
            }
        }
        teacherLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { teacherLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }) },
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
                                model = if (!profile.photoUrl.isNullOrEmpty()) { if (profile.photoUrl!!.startsWith("/")) java.io.File(profile.photoUrl!!) else "${com.attendance.app.core.network.Constants.BASE_URL.replace("/api", "")}${profile.photoUrl}" } else null,
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
                            Text("Let's get class started", style = MaterialTheme.typography.bodyMedium)
                            }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text("Your Classes", style = MaterialTheme.typography.titleLarge)
            attendanceViewModel.sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            attendanceState.serviceMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (attendanceViewModel.isStartingSession) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))

            if (classes.isEmpty()) {
                EmptyStateView(title = "No classes found", subtitle = "Go to Classes tab to create one")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(classes) { cls ->
                        val isActive = activeClassId == cls.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
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
                                        Text(text = if (isActive) "Live now" else "${cls.studentCount} students", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isActive) {
                                        Button(
                                            onClick = { confirmStop = true },
                                            enabled = !attendanceViewModel.isStoppingSession,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Stop")
                                        }
                                    } else {
                                        Button(enabled = activeClassId == null && !attendanceViewModel.isStartingSession, onClick = {
                                            selectedClassId = cls.id
                                            selectedClassName = cls.name
                                            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                arrayOf(
                                                    android.Manifest.permission.BLUETOOTH_SCAN,
                                                    android.Manifest.permission.BLUETOOTH_CONNECT,
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                                arrayOf(
                                                    android.Manifest.permission.BLUETOOTH_SCAN,
                                                    android.Manifest.permission.BLUETOOTH_CONNECT,
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            } else {
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            }
                                            permissionLauncher.launch(perms)
                                        }) {
                                            Text("Start")
                                        }
                                    }
                                }
                                if (isActive) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { navController.navigate(Screen.LiveSession(cls.id, sessionId = attendanceState.activeTeacherSession?.sessionId)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open attendance")
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
