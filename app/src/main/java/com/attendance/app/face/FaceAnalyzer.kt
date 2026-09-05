package com.attendance.app.face

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FaceAnalyzer(
    private val context: Context,
    private val onFaceDetected: (embedding: FloatArray, bitmap: Bitmap, leftEyeOpen: Float?, rightEyeOpen: Float?, smiling: Float?, brightness: Float) -> Unit,
    private val onNoFaceDetected: () -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var interpreter: Interpreter? = null
    private val INPUT_IMAGE_SIZE = 112
    private val EMBEDDING_SIZE = 128

    private val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
    private val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    init {
        try {
            val model = loadModelFile(context, "mobile_face_net.tflite")
            if (model != null) {
                val options = Interpreter.Options().apply {
                    numThreads = 4
                }
                interpreter = Interpreter(model, options)
                Log.d("FaceAnalyzer", "TFLite model loaded successfully")
            } else {
                Log.e("FaceAnalyzer", "TFLite model buffer is null")
            }
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Failed to initialize TFLite interpreter: ${e.message}", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer? {
        // Method 1: Try openFd (memory mapped)
        try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
        } catch (e: Exception) {
            Log.w("FaceAnalyzer", "openFd failed, falling back to direct byte buffer: ${e.message}")
        }
        
        // Method 2: Direct byte stream (works even if asset is compressed by aapt)
        return try {
            val bytes = context.assets.open(modelName).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            Log.d("FaceAnalyzer", "Loaded model via direct stream (${bytes.size} bytes)")
            buffer
        } catch (e2: Exception) {
            Log.e("FaceAnalyzer", "Failed to load model asset: ${e2.message}", e2)
            null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Calculate brightness from Y plane
            val yBuffer = imageProxy.planes[0].buffer
            val yBufferDup = yBuffer.duplicate()
            val yData = ByteArray(yBufferDup.remaining())
            yBufferDup.get(yData)
            val brightness = yData.map { it.toInt() and 0xFF }.average().toFloat()

            // Pass native media image to ML Kit with rotation
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        if (largestFace != null) {
                            processFace(largestFace, imageProxy, rotationDegrees, brightness)
                        } else {
                            onNoFaceDetected()
                        }
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Face detection failed: ${e.message}")
                    onError("Face detection failed: ${e.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error in analyze: ${e.message}", e)
            onError("Error processing frame")
            imageProxy.close()
        }
    }

    private fun processFace(face: Face, imageProxy: ImageProxy, rotationDegrees: Int, brightness: Float) {
        var rawBitmap: Bitmap? = null
        var uprightBitmap: Bitmap? = null
        var croppedFace: Bitmap? = null
        var scaledFace: Bitmap? = null

        try {
            // Convert imageProxy to bitmap and rotate to match ML Kit coordinate space
            rawBitmap = imageProxy.toBitmap()
            uprightBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            val bbox = face.boundingBox

            // Expand bbox by 15% for facial context, clamped to upright bitmap dimensions
            val expansion = 0.15f
            val expandW = (bbox.width() * expansion).toInt()
            val expandH = (bbox.height() * expansion).toInt()

            val left = (bbox.left - expandW).coerceIn(0, uprightBitmap.width - 1)
            val top = (bbox.top - expandH).coerceIn(0, uprightBitmap.height - 1)
            val right = (bbox.right + expandW).coerceIn(left + 1, uprightBitmap.width)
            val bottom = (bbox.bottom + expandH).coerceIn(top + 1, uprightBitmap.height)

            val faceWidth = right - left
            val faceHeight = bottom - top

            if (faceWidth <= 10 || faceHeight <= 10) {
                Log.w("FaceAnalyzer", "Face bbox too small: ${faceWidth}x${faceHeight}")
                onNoFaceDetected()
                return
            }

            // Crop face directly from upright bitmap
            croppedFace = Bitmap.createBitmap(uprightBitmap, left, top, faceWidth, faceHeight)

            // Scale to 112x112 using bilinear filtering
            scaledFace = Bitmap.createScaledBitmap(croppedFace, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)

            // Extract pixels directly from scaled 112x112 face
            scaledFace.getPixels(intValues, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

            var embedding = FloatArray(EMBEDDING_SIZE)

            val currentInterpreter = interpreter
            if (currentInterpreter != null) {
                inputBuffer.rewind()

                // Normalize pixel values for MobileFaceNet: (pixel - 127.5) / 128.0
                for (pixelValue in intValues) {
                    val r = ((pixelValue shr 16) and 0xFF)
                    val g = ((pixelValue shr 8) and 0xFF)
                    val b = (pixelValue and 0xFF)

                    inputBuffer.putFloat((r - 127.5f) / 128.0f)
                    inputBuffer.putFloat((g - 127.5f) / 128.0f)
                    inputBuffer.putFloat((b - 127.5f) / 128.0f)
                }

                val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
                currentInterpreter.run(inputBuffer, outputBuffer)
                
                // L2-normalize the embedding vector
                embedding = FaceMath.l2Normalize(outputBuffer[0])
            } else {
                Log.e("FaceAnalyzer", "TFLite interpreter is null during face processing")
            }

            // Pass face data to UI callback
            val previewBitmap = Bitmap.createBitmap(scaledFace)
            onFaceDetected(
                embedding, 
                previewBitmap, 
                face.leftEyeOpenProbability, 
                face.rightEyeOpenProbability, 
                face.smilingProbability, 
                brightness
            )
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error in processFace: ${e.message}", e)
            onError("Error extracting face")
        } finally {
            try {
                croppedFace?.recycle()
                scaledFace?.recycle()
                if (uprightBitmap !== rawBitmap) {
                    uprightBitmap?.recycle()
                }
                rawBitmap?.recycle()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}

