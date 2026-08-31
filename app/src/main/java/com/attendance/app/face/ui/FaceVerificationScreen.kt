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
import kotlinx.coroutines.delay

enum class Challenge { BLINK, SMILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationScreen(
    onBack: () -> Unit,
    onVerificationSuccess: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var verificationStatus by remember { mutableStateOf("Position your face") }
    var isProcessing by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    // Auto-verify after brief delay once verified
    LaunchedEffect(isVerified) {
        if (isVerified) {
            delay(1000) // Show "Verified!" for 1 second
            onVerificationSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                val latestEmbeddingRef = remember { mutableStateOf<FloatArray?>(null) }
                var isFaceDetected by remember { mutableStateOf(false) }
                var challengePassed by remember { mutableStateOf(false) }
                val currentChallenge = remember { Challenge.entries.random() }
                
                CameraPreviewScreen(
                    onFaceDetected = { embedding, _, leftEyeOpen, rightEyeOpen, smiling, brightness ->
                        latestEmbeddingRef.value = embedding
                        if (!isFaceDetected) isFaceDetected = true
                        
                        if (!isVerified) {
                            if (brightness < 25f) {
                                verificationStatus = "Too dark! Move to better lighting."
                            } else if (!challengePassed) {
                                if (currentChallenge == Challenge.BLINK) {
                                    verificationStatus = "Please BLINK..."
                                    if (leftEyeOpen != null && rightEyeOpen != null && leftEyeOpen < 0.2f && rightEyeOpen < 0.2f) {
                                        challengePassed = true
                                    }
                                } else {
                                    verificationStatus = "Please SMILE..."
                                    if (smiling != null && smiling > 0.7f) {
                                        challengePassed = true
                                    }
                                }
                            }
                            
                            if (challengePassed && !isProcessing && !isVerified) {
                                isProcessing = true
                                verificationStatus = "Verifying..."
                                
                                val prefs = context.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                                val isPending = prefs.getBoolean("face_approval_pending", false)
                                val storedStr = prefs.getString("registered_face_embedding", null)
                                
                                if (isPending) {
                                    verificationStatus = "Face approval pending! Cannot mark attendance."
                                    isProcessing = false
                                    challengePassed = false
                                } else if (storedStr != null) {
                                    try {
                                        val storedArray = org.json.JSONArray(storedStr)
                                        val stored = FloatArray(storedArray.length()) { i -> storedArray.getDouble(i).toFloat() }
                                        val similarity = com.attendance.app.face.FaceMath.cosineSimilarity(embedding, stored)
                                        
                                        if (similarity > 0.7f) {
                                            isVerified = true
                                        } else {
                                            verificationStatus = "Face does not match registered profile"
                                            isProcessing = false
                                            challengePassed = false
                                        }
                                    } catch (e: Exception) {
                                        verificationStatus = "Error reading profile"
                                        isProcessing = false
                                        challengePassed = false
                                    }
                                } else {
                                    verificationStatus = "No face registered! Please register in Profile."
                                    isProcessing = false
                                    challengePassed = false
                                }
                            }
                        }
                    },
                    onNoFaceDetected = {
                        latestEmbeddingRef.value = null
                        if (isFaceDetected) isFaceDetected = false
                        if (!isProcessing && !isVerified) {
                            verificationStatus = "Position your face"
                        }
                    },
                    onError = {
                        verificationStatus = "Error: $it"
                        isProcessing = false
                    }
                )
                
                // Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = if (isVerified) "✅ Face Verified!" else verificationStatus, 
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isFaceDetected || isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        if (isProcessing && !isVerified) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission is required.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}


