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
import java.nio.MappedByteBuffer
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
                interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 4 })
            }
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Failed to load TFLite model", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        var uprightBitmap: Bitmap? = null
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Calculate brightness using the raw Y-plane buffer
            val yBuffer = imageProxy.planes[0].buffer
            val yBufferDup = yBuffer.duplicate()
            val yData = ByteArray(yBufferDup.remaining())
            yBufferDup.get(yData)
            val brightness = yData.map { it.toInt() and 0xFF }.average().toFloat()

            // Convert to Bitmap
            val rawBitmap = imageProxy.toBitmap()

            // Rotate bitmap to ensure it is upright (matches screen/face orientation)
            uprightBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            // Pass upright bitmap to ML Kit (rotation is 0 because bitmap is already rotated upright)
            val image = InputImage.fromBitmap(uprightBitmap, 0)
            val currentUpright = uprightBitmap

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        largestFace?.let { face ->
                            // Capture a reference to the bitmap while it's valid; process synchronously
                            val bmpForCrop = uprightBitmap
                            processFace(face, bmpForCrop, brightness)
                        } ?: onNoFaceDetected()
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener { e ->
                    onError("Face detection failed: ${e.message}")
                }
                .addOnCompleteListener {
                    // Recycle only after processing is fully complete
                    uprightBitmap?.recycle()
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error in analyze", e)
            onError("Error processing frame")
            uprightBitmap?.recycle()
            imageProxy.close()
        }
    }

    private fun processFace(face: Face, uprightBitmap: Bitmap?, brightness: Float) {
        try {
            if (uprightBitmap == null || uprightBitmap.isRecycled) {
                Log.e("FaceAnalyzer", "uprightBitmap is null or recycled before crop")
                onError("Bitmap recycled")
                return
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
                Log.e("FaceAnalyzer", "Invalid face dimensions: ${faceWidth}x${faceHeight}")
                onError("Invalid face crop")
                return
            }

            // Crop face directly from upright bitmap
            val croppedFace = Bitmap.createBitmap(uprightBitmap, left, top, faceWidth, faceHeight)

            // Scale to 112x112 using bilinear filtering
            val scaledFace = Bitmap.createScaledBitmap(croppedFace, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)
            croppedFace.recycle()

            // Extract pixels directly from scaled 112x112 face
            scaledFace.getPixels(intValues, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

                Log.d("FaceAnalyzer", "DEBUG: Embedding computed, interpreter OK, first 5=" + embedding.take(5).toString())

            var embedding = FloatArray(EMBEDDING_SIZE)

            if (interpreter != null) {
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
                interpreter?.run(inputBuffer, outputBuffer)
                
                // L2-normalize the embedding vector
                embedding = FaceMath.l2Normalize(outputBuffer[0])

                Log.d("FaceAnalyzer", "Embedding generated. First 5 values: ${embedding.take(5)}")
            } else {
                Log.e("FaceAnalyzer", "TFLite interpreter is null!")
                for (i in 0 until EMBEDDING_SIZE) {
                    embedding[i] = 0.0f
                }
            }

            // Pass a copy of the faceBitmap so UI can render it safely
            onFaceDetected(
                embedding, 
                Bitmap.createBitmap(scaledFace), 
                face.leftEyeOpenProbability, 
                face.rightEyeOpenProbability, 
                face.smilingProbability, 
                brightness
            )
            scaledFace.recycle()
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error extracting face embedding", e)
            onError("Error extracting face")
        }
    }
}
