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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.sp
import com.attendance.app.face.FaceDetectionResult
import com.attendance.app.face.presentation.FaceRegState
import com.attendance.app.face.presentation.FaceRegistrationViewModel
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay

/**
 * Face Registration Screen with:
 * - Oval face guide overlay with animated border
 * - Real-time quality feedback (position, lighting, angle)
 * - Auto-capture when quality is high enough
 * - Multi-sample progress (3 captures for robust template)
 * - Error recovery with retry
 */
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

    // Face detection state
    var latestResult by remember { mutableStateOf<FaceDetectionResult?>(null) }
    var isFaceDetected by remember { mutableStateOf(false) }
    var qualityFeedback by remember { mutableStateOf("Position your face inside the oval") }
    var isQualityAcceptable by remember { mutableStateOf(false) }

    // Auto-capture: when quality is good for enough consecutive frames, auto-capture
    var goodFrameCount by remember { mutableIntStateOf(0) }
    val autoCaptureTrigger = 8 // ~0.8 seconds of good frames

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    // Navigate on success after a short delay
    LaunchedEffect(state) {
        if (state is FaceRegState.Success) {
            delay(1500)
            onRegistrationComplete()
        }
    }

    // Auto-capture logic
    LaunchedEffect(isQualityAcceptable, state) {
        if (isQualityAcceptable && (state is FaceRegState.Idle || state is FaceRegState.Capturing)) {
            goodFrameCount++
            if (goodFrameCount >= autoCaptureTrigger) {
                latestResult?.let {
                    viewModel.captureEmbedding(it.embedding, context)
                    goodFrameCount = 0
                }
            }
        } else {
            goodFrameCount = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Face") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                // Camera preview layer
                CameraPreviewScreen(
                    onFaceDetected = { result ->
                        latestResult = result
                        isFaceDetected = true
                        isQualityAcceptable = result.quality.isAcceptable
                        qualityFeedback = result.quality.feedback
                    },
                    onNoFaceDetected = {
                        latestResult = null
                        isFaceDetected = false
                        isQualityAcceptable = false
                        qualityFeedback = "Position your face inside the oval"
                        goodFrameCount = 0
                    },
                    onError = { error ->
                        qualityFeedback = "Camera error: $error"
                        isQualityAcceptable = false
                    }
                )

                // Face oval guide overlay
                FaceOvalOverlay(
                    isFaceDetected = isFaceDetected,
                    isQualityAcceptable = isQualityAcceptable,
                    state = state
                )

                // UI overlay (instructions + progress)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    when (state) {
                        is FaceRegState.Uploading -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Saving your face template...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        is FaceRegState.Success -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1B5E20).copy(alpha = 0.9f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Face Registered Successfully!",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is FaceRegState.Error -> {
                            val errorState = state as FaceRegState.Error
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        errorState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.reset() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Try Again")
                                    }
                                }
                            }
                        }

                        else -> {
                            // Idle or Capturing — show guidance and progress
                            val capturedCount = when (state) {
                                is FaceRegState.Capturing -> (state as FaceRegState.Capturing).captured
                                else -> 0
                            }

                            // Progress dots
                            if (capturedCount > 0) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (i in 1..3) {
                                            val dotColor = if (i <= capturedCount) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                            Canvas(modifier = Modifier.size(12.dp)) {
                                                drawCircle(color = dotColor)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "$capturedCount/3 captured",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Quality feedback card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Text(
                                    text = qualityFeedback,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        isQualityAcceptable -> MaterialTheme.colorScheme.primary
                                        isFaceDetected -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Manual capture button (backup for auto-capture)
                            Button(
                                onClick = {
                                    latestResult?.let {
                                        viewModel.captureEmbedding(it.embedding, context)
                                        goodFrameCount = 0
                                    }
                                },
                                enabled = isFaceDetected && isQualityAcceptable &&
                                        (state is FaceRegState.Idle || state is FaceRegState.Capturing),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    if (isQualityAcceptable) "Capture" else "Waiting for good quality...",
                                    fontSize = 16.sp
                                )
                            }

                            // Auto-capture hint
                            if (isQualityAcceptable && goodFrameCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Auto-capturing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                // No camera permission
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Camera permission is required for face registration.",
                        textAlign = TextAlign.Center
                    )
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
 * Draws an animated oval guide for face positioning.
 * - Gray when no face detected
 * - Amber when face detected but quality is poor
 * - Green with pulse animation when quality is acceptable
 * - Blue pulse on successful capture state
 */
@Composable
private fun FaceOvalOverlay(
    isFaceDetected: Boolean,
    isQualityAcceptable: Boolean,
    state: FaceRegState
) {
    val targetColor = when {
        state is FaceRegState.Success -> Color(0xFF2E7D32)
        state is FaceRegState.Uploading -> Color(0xFF1565C0)
        isQualityAcceptable -> Color(0xFF2E7D32)
        isFaceDetected -> Color(0xFFF9A825)
        else -> Color(0xFFBDBDBD)
    }

    val ovalColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "ovalColor"
    )

    // Pulse animation when quality is acceptable
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val strokeAlpha = if (isQualityAcceptable || state is FaceRegState.Uploading) pulseAlpha else 0.8f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val ovalWidth = size.width * 0.6f
        val ovalHeight = size.height * 0.45f
        val centerX = size.width / 2f
        val centerY = size.height * 0.38f

        // Semi-transparent dark scrim outside the oval
        // (helps the user focus on the oval area)
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            size = size
        )

        // Clear the oval area
        drawOval(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset(centerX - ovalWidth / 2f, centerY - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Draw the oval border
        drawOval(
            color = ovalColor.copy(alpha = strokeAlpha),
            topLeft = Offset(centerX - ovalWidth / 2f, centerY - ovalHeight / 2f),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
