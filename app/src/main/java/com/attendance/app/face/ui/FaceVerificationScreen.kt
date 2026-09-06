package com.attendance.app.face.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attendance.app.face.FaceDetectionResult
import com.attendance.app.face.FaceMath
import kotlinx.coroutines.delay

/**
 * Security-hardened Face Verification Screen.
 * 
 * Verification pipeline:
 * 1. Detect face → quality gate
 * 2. Liveness check: require BOTH blink AND smile (sequential, timed)
 * 3. Face matching: cosine similarity ≥ 0.70, require 3 consecutive matches
 * 4. Anti-spoofing: liveness actions must complete within time window
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationScreen(
    onBack: () -> Unit,
    onVerificationSuccess: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    // Verification state machine
    var verificationPhase by remember { mutableStateOf(VerificationPhase.DETECTING_FACE) }
    var statusMessage by remember { mutableStateOf("Position your face in the oval") }
    var isVerified by remember { mutableStateOf(false) }
    
    // Face detection state
    var isFaceDetected by remember { mutableStateOf(false) }
    var latestResult by remember { mutableStateOf<FaceDetectionResult?>(null) }

    // Liveness state
    var blinkDetected by remember { mutableStateOf(false) }
    var smileDetected by remember { mutableStateOf(false) }
    var eyesWereClosed by remember { mutableStateOf(false) }
    var livenessStartTime by remember { mutableLongStateOf(0L) }
    
    // Matching state
    var consecutiveMatches by remember { mutableIntStateOf(0) }
    
    // Thresholds
    val MATCH_THRESHOLD = 0.70f
    val REQUIRED_CONSECUTIVE_MATCHES = 3
    val LIVENESS_TIMEOUT_MS = 15000L // 15 seconds to complete liveness
    
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
            delay(1200)
            onVerificationSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                // Camera preview
                CameraPreviewScreen(
                    onFaceDetected = { result ->
                        latestResult = result
                        if (!isFaceDetected) isFaceDetected = true

                        val prefs = context.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
                        
                        // Check if face approval is pending
                        val isPending = prefs.getBoolean("face_approval_pending", false)
                        if (isPending) {
                            statusMessage = "Face approval pending! Cannot verify."
                            return@CameraPreviewScreen
                        }

                        // Skip if already verified
                        if (isVerified) return@CameraPreviewScreen

                        when (verificationPhase) {
                            VerificationPhase.DETECTING_FACE -> {
                                // Check basic quality
                                if (result.brightness < 25f) {
                                    statusMessage = "Too dark — move to better lighting"
                                } else if (!result.quality.isAcceptable) {
                                    statusMessage = result.quality.feedback
                                } else {
                                    // Face quality OK, move to liveness
                                    verificationPhase = VerificationPhase.LIVENESS_BLINK
                                    livenessStartTime = System.currentTimeMillis()
                                    statusMessage = "👁 Please BLINK your eyes"
                                }
                            }

                            VerificationPhase.LIVENESS_BLINK -> {
                                // Check timeout
                                if (System.currentTimeMillis() - livenessStartTime > LIVENESS_TIMEOUT_MS) {
                                    verificationPhase = VerificationPhase.DETECTING_FACE
                                    eyesWereClosed = false
                                    statusMessage = "Timeout — please try again"
                                    return@CameraPreviewScreen
                                }

                                val leftEye = result.leftEyeOpen
                                val rightEye = result.rightEyeOpen
                                
                                if (leftEye != null && rightEye != null) {
                                    // Detect eyes closing
                                    if (leftEye < 0.30f && rightEye < 0.30f) {
                                        eyesWereClosed = true
                                        statusMessage = "👁 Good... now open your eyes"
                                    }
                                    // Detect eyes reopening after closing = blink complete
                                    else if (eyesWereClosed && leftEye > 0.55f && rightEye > 0.55f) {
                                        blinkDetected = true
                                        verificationPhase = VerificationPhase.LIVENESS_SMILE
                                        statusMessage = "😊 Now SMILE!"
                                    }
                                }
                            }

                            VerificationPhase.LIVENESS_SMILE -> {
                                // Check timeout
                                if (System.currentTimeMillis() - livenessStartTime > LIVENESS_TIMEOUT_MS) {
                                    verificationPhase = VerificationPhase.DETECTING_FACE
                                    blinkDetected = false
                                    eyesWereClosed = false
                                    smileDetected = false
                                    statusMessage = "Timeout — please try again"
                                    return@CameraPreviewScreen
                                }

                                val smile = result.smiling
                                if (smile != null && smile > 0.45f) {
                                    smileDetected = true
                                    verificationPhase = VerificationPhase.MATCHING
                                    statusMessage = "Verifying your identity..."
                                }
                            }

                            VerificationPhase.MATCHING -> {
                                // Get stored embedding
                                val storedStr = prefs.getString("registered_face_embedding", null)
                                    ?: prefs.getString("pending_face_embedding", null)?.also {
                                        prefs.edit().putString("registered_face_embedding", it).apply()
                                    }

                                if (storedStr == null) {
                                    statusMessage = "No face registered! Register in Profile first."
                                    verificationPhase = VerificationPhase.DETECTING_FACE
                                    return@CameraPreviewScreen
                                }

                                try {
                                    val storedArray = org.json.JSONArray(storedStr)
                                    val stored = FloatArray(storedArray.length()) { i -> storedArray.getDouble(i).toFloat() }

                                    val normalizedCurrent = FaceMath.l2Normalize(result.embedding)
                                    val normalizedStored = FaceMath.l2Normalize(stored)
                                    val similarity = FaceMath.cosineSimilarity(normalizedCurrent, normalizedStored)
                                    val distance = FaceMath.euclideanDistance(normalizedCurrent, normalizedStored)

                                    if (similarity >= MATCH_THRESHOLD) {
                                        consecutiveMatches++
                                        if (consecutiveMatches >= REQUIRED_CONSECUTIVE_MATCHES) {
                                            // VERIFIED
                                            isVerified = true
                                            statusMessage = "✓ Identity Verified!"
                                        } else {
                                            statusMessage = "Hold still... (${consecutiveMatches}/$REQUIRED_CONSECUTIVE_MATCHES)"
                                        }
                                    } else {
                                        consecutiveMatches = 0
                                        val percent = (similarity * 100).toInt().coerceIn(0, 100)
                                        statusMessage = "Face does not match ($percent%). Re-register in Profile."
                                        // Reset liveness after mismatch to prevent spoofing
                                        verificationPhase = VerificationPhase.DETECTING_FACE
                                        blinkDetected = false
                                        smileDetected = false
                                        eyesWereClosed = false
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error reading face data"
                                    verificationPhase = VerificationPhase.DETECTING_FACE
                                }
                            }
                        }
                    },
                    onNoFaceDetected = {
                        latestResult = null
                        if (isFaceDetected) isFaceDetected = false
                        if (!isVerified) {
                            // Reset to face detection phase if we lose the face
                            if (verificationPhase != VerificationPhase.DETECTING_FACE) {
                                verificationPhase = VerificationPhase.DETECTING_FACE
                                blinkDetected = false
                                smileDetected = false
                                eyesWereClosed = false
                                consecutiveMatches = 0
                            }
                            statusMessage = "Position your face in the oval"
                        }
                    },
                    onError = { error ->
                        statusMessage = "Error: $error"
                    }
                )

                // Face oval overlay
                VerificationOvalOverlay(
                    isFaceDetected = isFaceDetected,
                    verificationPhase = verificationPhase,
                    isVerified = isVerified
                )

                // Status overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Phase indicators
                    if (!isVerified && verificationPhase != VerificationPhase.DETECTING_FACE) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Blink indicator
                                PhaseChip(
                                    label = "Blink",
                                    isComplete = blinkDetected,
                                    isActive = verificationPhase == VerificationPhase.LIVENESS_BLINK
                                )
                                // Smile indicator
                                PhaseChip(
                                    label = "Smile",
                                    isComplete = smileDetected,
                                    isActive = verificationPhase == VerificationPhase.LIVENESS_SMILE
                                )
                                // Match indicator
                                PhaseChip(
                                    label = "Match",
                                    isComplete = isVerified,
                                    isActive = verificationPhase == VerificationPhase.MATCHING
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Status card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isVerified -> Color(0xFF1B5E20).copy(alpha = 0.95f)
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isVerified) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            
                            if (verificationPhase == VerificationPhase.MATCHING && !isVerified) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isVerified) FontWeight.Bold else FontWeight.Normal,
                                color = if (isVerified) Color.White else {
                                    when {
                                        isFaceDetected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                // No camera permission
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Camera permission is required.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

/**
 * Verification phases — must complete in order.
 */
private enum class VerificationPhase {
    DETECTING_FACE,
    LIVENESS_BLINK,
    LIVENESS_SMILE,
    MATCHING
}

/**
 * Small chip showing liveness/match phase status.
 */
@Composable
private fun PhaseChip(
    label: String,
    isComplete: Boolean,
    isActive: Boolean
) {
    val bgColor = when {
        isComplete -> Color(0xFF2E7D32)
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = when {
        isComplete || isActive -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isComplete) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Verification oval overlay — changes color based on verification phase.
 */
@Composable
private fun VerificationOvalOverlay(
    isFaceDetected: Boolean,
    verificationPhase: VerificationPhase,
    isVerified: Boolean
) {
    val targetColor = when {
        isVerified -> Color(0xFF2E7D32)
        verificationPhase == VerificationPhase.MATCHING -> Color(0xFF1565C0)
        verificationPhase == VerificationPhase.LIVENESS_SMILE -> Color(0xFFF9A825)
        verificationPhase == VerificationPhase.LIVENESS_BLINK -> Color(0xFFE65100)
        isFaceDetected -> Color(0xFFF9A825)
        else -> Color(0xFFBDBDBD)
    }

    val ovalColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "verifyOvalColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "verifyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "verifyPulseAlpha"
    )

    val shouldPulse = verificationPhase != VerificationPhase.DETECTING_FACE || isVerified
    val strokeAlpha = if (shouldPulse) pulseAlpha else 0.8f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val ovalWidth = size.width * 0.6f
        val ovalHeight = size.height * 0.45f
        val centerX = size.width / 2f
        val centerY = size.height * 0.38f

        // Semi-transparent scrim
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            size = size
        )

        // Clear oval area
        drawOval(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(centerX - ovalWidth / 2f, centerY - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Oval border
        drawOval(
            color = ovalColor.copy(alpha = strokeAlpha),
            topLeft = Offset(centerX - ovalWidth / 2f, centerY - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
