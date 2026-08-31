package com.attendance.app.face.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.attendance.app.face.presentation.FaceRegState
import com.attendance.app.face.presentation.FaceRegistrationViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRegistrationScreen(
    onBack: () -> Unit,
    onRegistrationComplete: () -> Unit,
    showBackButton: Boolean = true,
    viewModel: FaceRegistrationViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.registrationState.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state) {
        if (state is FaceRegState.Success) {
            delay(1500) // Show success for 1.5 seconds
            onRegistrationComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Face") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                    onError = {
                        // Handle error
                    }
                )
                
                // Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is FaceRegState.Processing -> {
                            Card {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Registering your face...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        is FaceRegState.Success -> {
                            Card {
                                Text(
                                    "✅ Face Registered Successfully!",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(modifier = Modifier.padding(bottom = 16.dp)) {
                                    Text(
                                        if (isFaceDetected) "Face Detected! Tap to capture." else "Position your face in the frame",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isFaceDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Button(
                                    onClick = { 
                                        latestEmbeddingRef.value?.let { 
                                            viewModel.onFaceDetected(it, context)
                                        }
                                    },
                                    enabled = isFaceDetected && state is FaceRegState.Idle,
                                    modifier = Modifier.size(width = 200.dp, height = 56.dp)
                                ) {
                                    Text("Capture Face")
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission is required for face registration.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
