package com.attendance.app.face.ui

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.attendance.app.face.FaceAnalyzer
import com.attendance.app.face.FaceDetectionResult
import java.util.concurrent.Executors

/**
 * Camera preview composable with proper lifecycle management.
 * 
 * Key improvements over previous version:
 * - DisposableEffect unbinds camera on dispose (prevents leaked camera providers)
 * - Front camera mirrored via FILL_CENTER + implementation mode (natural selfie view)
 * - Single-thread executor for analyzer prevents thread explosion
 * - Structured FaceDetectionResult replaces raw parameter tuple
 */
@Composable
fun CameraPreviewScreen(
    onFaceDetected: (FaceDetectionResult) -> Unit,
    onNoFaceDetected: () -> Unit = {},
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    
    // Track the camera provider for cleanup
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Cleanup camera when composable leaves composition
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
                Log.d("CameraPreview", "Camera unbound on dispose")
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error unbinding camera: ${e.message}")
            }
        }
    }
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // Use COMPATIBLE implementation for better front-camera mirroring
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    val faceAnalyzer = FaceAnalyzer(
                        context = ctx,
                        onFaceDetected = onFaceDetected,
                        onNoFaceDetected = onNoFaceDetected,
                        onError = onError
                    )
                    
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, faceAnalyzer)
                        }
                        
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        Log.d("CameraPreview", "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", e)
                        onError("Camera failed to start: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Failed to get camera provider", e)
                    onError("Camera not available: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
            
            previewView
        }
    )
}
