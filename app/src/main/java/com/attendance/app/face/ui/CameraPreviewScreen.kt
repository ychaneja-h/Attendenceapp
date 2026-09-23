package com.attendance.app.face.ui

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attendance.app.face.FaceAnalyzer
import com.attendance.app.face.FaceDetectionResult
import java.util.concurrent.Executors

@Composable
fun CameraPreviewScreen(
    onFaceDetected: (FaceDetectionResult) -> Unit,
    onNoFaceDetected: () -> Unit = {},
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onDetected by rememberUpdatedState(onFaceDetected)
    val onMissing by rememberUpdatedState(onNoFaceDetected)
    val onCameraError by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

    DisposableEffect(context, lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analyzer = FaceAnalyzer(context,
            onFaceDetected = { result ->
                if (!disposed) onDetected(result) else result.previewBitmap.recycle()
            },
            onNoFaceDetected = { if (!disposed) onMissing() },
            onError = { if (!disposed) onCameraError(it) })
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            .also { it.setAnalyzer(executor, analyzer) }
        providerFuture.addListener({
            if (!disposed) {
                try {
                    provider = providerFuture.get()
                    provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (error: Exception) {
                    onCameraError("Unable to open the front camera: ${error.message ?: "Please try again."}")
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            analyzer.close()
            executor.shutdown()
        }
    }
}
