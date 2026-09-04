package com.attendance.app.student.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.attendance.app.auth.presentation.AuthViewModel
import com.attendance.app.core.ui.navigation.Screen
import com.attendance.app.profile.presentation.ProfileUiState
import com.attendance.app.profile.presentation.ProfileViewModel
import com.attendance.app.student.ui.StudentBottomBar
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel(),
    httpClient: HttpClient = koinInject()
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val isSaving by profileViewModel.isSaving.collectAsState()
    val saveMessage by profileViewModel.saveMessage.collectAsState()
    
                OutlinedButton(
                onClick = { navController.navigate(Screen.ChangePassword) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
    var faceApprovalStatus by remember { mutableStateOf("Checking...") }
    val prefs = context.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var batch by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }

    var refreshTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTrigger) {
        profileViewModel.loadProfile()
        try {
            val response = httpClient.get("${com.attendance.app.core.network.Constants.BASE_URL}/requests/me")
            if (response.status.isSuccess()) {
                val array = JSONArray(response.bodyAsText())
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
                    faceApprovalStatus = "Face Status: Pending Teacher Approval"
                } else if (latestApproved != null) {
                    prefs.edit().putBoolean("face_approval_pending", false).apply()
                    faceApprovalStatus = "Face Status: Approved ✅"
                    val pendingFace = prefs.getString("pending_face_embedding", null)
                    if (pendingFace != null) {
                        prefs.edit()
                            .putString("registered_face_embedding", pendingFace)
                            .remove("pending_face_embedding")
                            .apply()
                    } else if (!prefs.contains("registered_face_embedding")) {
                        // Download from server if missing (e.g. new device)
                        prefs.edit().putString("registered_face_embedding", latestApproved.getString("requestedData")).apply()
                    }
                } else {
                    prefs.edit().putBoolean("face_approval_pending", false).apply()
                    faceApprovalStatus = "Face Status: No Face Registered"
                }
            }
        } catch (e: Exception) {
            faceApprovalStatus = "Face Status: Unknown"
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val profile = (uiState as ProfileUiState.Success).profile
            name = profile.name
            email = profile.email
            department = profile.department ?: ""
            section = profile.section ?: ""
            batch = profile.batch ?: ""
            year = profile.year ?: ""
            photoUrl = profile.photoUrl ?: ""
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }, actions = { IconButton(onClick = { profileViewModel.loadProfile(); refreshTrigger++ }) { Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = "Refresh") } }) },
        bottomBar = { StudentBottomBar(navController, 3) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is ProfileUiState.Loading -> CircularProgressIndicator()
                is ProfileUiState.Error -> Text("Error loading profile")
                is ProfileUiState.Success -> {
                                OutlinedButton(
                onClick = { navController.navigate(Screen.ChangePassword) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        uri?.let {
                            val inputStream = context.contentResolver.openInputStream(it)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            if (bytes != null) {
                                val fileName = "profile_photo.jpg" // Can be more dynamic but fine for now
                                profileViewModel.uploadProfilePhoto(bytes, fileName)
                            }
                        }
                    }

                    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
                        coil.compose.AsyncImage(
                            model = if (photoUrl.isNotEmpty()) { if (photoUrl.startsWith("/")) java.io.File(photoUrl!!) else "${com.attendance.app.core.network.Constants.BASE_URL.replace("/api", "")}$photoUrl" } else null,
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = faceApprovalStatus, 
                        style = MaterialTheme.typography.titleMedium,
                        color = if (faceApprovalStatus.contains("Pending")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = section, onValueChange = { section = it }, label = { Text("Section") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = batch, onValueChange = { batch = it }, label = { Text("Batch") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { profileViewModel.updateProfile(name, email, department, section, batch, year, photoUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Profile")
                    }

                    saveMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary)
                        profileViewModel.clearSaveMessage()
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            var showWarningDialog by remember { mutableStateOf(false) }

            if (showWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showWarningDialog = false },
                    title = { Text("Warning") },
                    text = { Text("You will be unable to mark attendance until your teacher approves your new face. Do you wish to continue?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showWarningDialog = false
                            navController.navigate(com.attendance.app.core.ui.navigation.Screen.FaceEnrollment)
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWarningDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Button(
                onClick = {
                    showWarningDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📷 Register / Re-register Face")
            }

            Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                onClick = { navController.navigate(Screen.ChangePassword) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
            OutlinedButton(
                onClick = {
                    com.attendance.app.ble.scanner.BleScannerService.stop(context)
                    authViewModel.logout()
                    navController.navigate(Screen.RoleSelection) {
                        popUpTo(0) { inclusive = true }
                    }
                }, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Made by Yogesh K. Chaneja",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}















