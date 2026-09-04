package com.attendance.app.auth.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.attendance.app.auth.presentation.AuthViewModel
import com.attendance.app.face.ui.CameraPreviewScreen
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val context = LocalContext.current
    
    var newPassword by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = Input, 2 = Face Verification
    var hasCameraPermission by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            step = 2
        } else {
            viewModel.error = "Camera permission required for face verification"
        }
    }

    LaunchedEffect(viewModel.resetSuccess) {
        if (viewModel.resetSuccess) {
            viewModel.clearResetSuccess()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = {
                    IconButton(onClick = { if (step == 2) step = 1 else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (step == 1) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Reset Password",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                                        val role = viewModel.getRole()
                    val subtitle = if (role == "STUDENT") "You must verify your face to reset your password." else "Enter your new password below."
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (role != "STUDENT") {
                            Button(
                                onClick = { 
                                    viewModel.changePassword(newPassword, FloatArray(192) { 0f }) 
                                },
                                enabled = newPassword.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("Reset Password", style = MaterialTheme.typography.titleSmall)
                            }
                        } else {
                            Button(
                                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                                enabled = newPassword.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("Verify Face & Reset", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }

                    if (viewModel.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(viewModel.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (step == 2) {
                if (hasCameraPermission) {
                    val latestEmbeddingRef = remember { mutableStateOf<FloatArray?>(null) }
                    var isFaceDetected by remember { mutableStateOf(false) }
                    
                    CameraPreviewScreen(
                        onFaceDetected = { embedding, _, _, _, _, _ ->
                            latestEmbeddingRef.value = embedding
                            if (!isFaceDetected) isFaceDetected = true
                        },
                        onNoFaceDetected = {
                            latestEmbeddingRef.value = null
                            if (isFaceDetected) isFaceDetected = false
                        },
                        onError = {}
                    )
                    
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (viewModel.isLoading) {
                            Card {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Verifying face...")
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(modifier = Modifier.padding(bottom = 16.dp)) {
                                    Text(
                                        if (isFaceDetected) "Face Detected! Tap to verify & reset." else "Position your face in the frame",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isFaceDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Button(
                                    onClick = { 
                                        latestEmbeddingRef.value?.let { 
                                            viewModel.changePassword(newPassword, it)
                                        }
                                    },
                                    enabled = isFaceDetected && !viewModel.isLoading,
                                    modifier = Modifier.size(width = 200.dp, height = 56.dp)
                                ) {
                                    Text("Change Password")
                                }
                            }
                        }
                        
                        if (viewModel.error != null) {
                            AlertDialog(
                                onDismissRequest = { viewModel.error = null },
                                title = { Text("Reset Failed") },
                                text = { Text(viewModel.error!!) },
                                confirmButton = { TextButton(onClick = { viewModel.error = null }) { Text("OK") } }
                            )
                        }
                    }
                }
            }
        }
    }
}



