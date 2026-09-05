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

    LaunchedEffect(isVerified) {
        if (isVerified) {
            delay(1000)
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
                var blinkDetected by remember { mutableStateOf(false) }
                var livenessPassed by remember { mutableStateOf(false) }
                var consecutiveMatches by remember { mutableIntStateOf(0) }
                
                CameraPreviewScreen(
                    onFaceDetected = { embedding, _, leftEyeOpen, rightEyeOpen, smiling, brightness ->
                        latestEmbeddingRef.value = embedding
                        if (!isFaceDetected) isFaceDetected = true
                        
                        val prefs = context.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                        val isPending = prefs.getBoolean("face_approval_pending", false)
                        
                        if (isPending) {
                            verificationStatus = "Face approval pending! Cannot mark attendance."
                            if (isProcessing) isProcessing = false
                            return@CameraPreviewScreen
                        }
                        
                        if (!isVerified) {
                            if (brightness < 20f) {
                                verificationStatus = "Too dark! Move to better lighting."
                            } else if (!livenessPassed) {
                                verificationStatus = "Please Blink or Smile to verify..."
                                
                                // Check for smile
                                if (smiling != null && smiling > 0.40f) {
                                    livenessPassed = true
                                }
                                
                                // Check for blink
                                if (leftEyeOpen != null && rightEyeOpen != null) {
                                    if (leftEyeOpen < 0.35f && rightEyeOpen < 0.35f) {
                                        blinkDetected = true
                                    } else if (blinkDetected && leftEyeOpen > 0.50f && rightEyeOpen > 0.50f) {
                                        livenessPassed = true
                                    }
                                }
                            }
                            
                            if (livenessPassed && !isProcessing && !isVerified) {
                                isProcessing = true
                                verificationStatus = "Verifying..."
                                
                                val storedStr = prefs.getString("registered_face_embedding", null)
                                    ?: prefs.getString("pending_face_embedding", null)?.also {
                                        prefs.edit().putString("registered_face_embedding", it).apply()
                                    }
                                
                                if (storedStr != null) {
                                    try {
                                        val storedArray = org.json.JSONArray(storedStr)
                                        val stored = FloatArray(storedArray.length()) { i -> storedArray.getDouble(i).toFloat() }
                                        
                                        val normalizedCurrent = com.attendance.app.face.FaceMath.l2Normalize(embedding)
                                        val normalizedStored = com.attendance.app.face.FaceMath.l2Normalize(stored)
                                        val similarity = com.attendance.app.face.FaceMath.cosineSimilarity(normalizedCurrent, normalizedStored)
                                        
                                        // Threshold 0.60 with 2 consecutive matches for fast and secure verification
                                        if (similarity >= 0.60f) {
                                            consecutiveMatches++
                                            if (consecutiveMatches >= 2) {
                                                isVerified = true
                                            } else {
                                                verificationStatus = "Hold still..."
                                                isProcessing = false
                                            }
                                        } else {
                                            consecutiveMatches = 0
                                            val percent = (similarity * 100).toInt().coerceIn(0, 100)
                                            verificationStatus = "Face does not match ($percent%) - Re-register in Profile"
                                            isProcessing = false
                                        }
                                    } catch (e: Exception) {
                                        verificationStatus = "Error reading profile"
                                        isProcessing = false
                                    }
                                } else {
                                    verificationStatus = "No face registered! Please register in Profile."
                                    isProcessing = false
                                    livenessPassed = false
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
                                text = if (isVerified) "✓ Face Verified!" else verificationStatus, 
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

