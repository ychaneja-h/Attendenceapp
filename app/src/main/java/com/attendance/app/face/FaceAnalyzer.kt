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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Industrial-standard face analyzer that processes camera frames,
 * detects faces via ML Kit, extracts embeddings via MobileFaceNet,
 * and performs face quality assessment before emitting results.
 *
 * Key design choices:
 * - AtomicBoolean throttle ensures only one frame is processed at a time
 * - ML Kit landmarks enabled for head pose (euler angles) quality checks
 * - Bitmap lifecycle carefully managed — preview copy created BEFORE source recycling
 * - Model input/output dimensions read dynamically from the TFLite tensor shapes
 */
class FaceAnalyzer(
    private val context: Context,
    private val onFaceDetected: (FaceDetectionResult) -> Unit,
    private val onNoFaceDetected: () -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    private var interpreter: Interpreter? = null

    // These are determined dynamically from the model's actual tensor shapes
    private var batchSize = 1
    private var inputImageHeight = 112
    private var inputImageWidth = 112
    private var embeddingSize = 128
    private var inputBufferSize = 0

    // Pre-allocated buffers — sized after model is loaded
    private var intValues: IntArray = IntArray(0)
    private var inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(0)

    // Throttle: skip frames while one is still being processed
    private val isProcessing = AtomicBoolean(false)

    init {
        try {
            val model = loadModelFile(context, "mobile_face_net.tflite")
            if (model != null) {
                val options = Interpreter.Options().apply {
                    numThreads = 4
                }
                val interp = Interpreter(model, options)
                interpreter = interp

                val inputShape = interp.getInputTensor(0).shape()  // e.g. [2, 112, 112, 3]
                val outputShape = interp.getOutputTensor(0).shape() // e.g. [2, 192]
                inputBufferSize = interp.getInputTensor(0).numBytes()

                batchSize = outputShape[0]
                inputImageHeight = inputShape[1]
                inputImageWidth = inputShape[2]
                embeddingSize = outputShape[1]

                // Allocate correctly sized buffers
                intValues = IntArray(inputImageHeight * inputImageWidth)
                inputBuffer = ByteBuffer.allocateDirect(inputBufferSize).apply {
                    order(ByteOrder.nativeOrder())
                }

                Log.d(TAG, "TFLite model loaded: input=${inputShape.contentToString()} (${inputBufferSize} bytes), output=${outputShape.contentToString()}, batchSize=$batchSize, image=${inputImageWidth}x${inputImageHeight}, embedding=$embeddingSize")
            } else {
                Log.e(TAG, "TFLite model buffer is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLite interpreter: ${e.message}", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer? {
        // Method 1: Try openFd (memory mapped — fastest)
        try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
        } catch (e: Exception) {
            Log.w(TAG, "openFd failed, falling back to direct byte buffer: ${e.message}")
        }
        
        // Method 2: Direct byte stream (works even if asset is compressed by aapt)
        return try {
            val bytes = context.assets.open(modelName).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            Log.d(TAG, "Loaded model via direct stream (${bytes.size} bytes)")
            buffer
        } catch (e2: Exception) {
            Log.e(TAG, "Failed to load model asset: ${e2.message}", e2)
            null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        // Throttle: skip this frame if we're still processing the previous one
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
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
            val imageWidth = inputImage.width
            val imageHeight = inputImage.height

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        if (largestFace != null) {
                            processFace(largestFace, imageProxy, rotationDegrees, brightness, imageWidth, imageHeight)
                        } else {
                            onNoFaceDetected()
                        }
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed: ${e.message}")
                    onError("Face detection failed: ${e.message}")
                }
                .addOnCompleteListener {
                    isProcessing.set(false)
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyze: ${e.message}", e)
            onError("Error processing frame")
            isProcessing.set(false)
            imageProxy.close()
        }
    }

    private fun processFace(
        face: Face,
        imageProxy: ImageProxy,
        rotationDegrees: Int,
        brightness: Float,
        imageWidth: Int,
        imageHeight: Int
    ) {
        var rawBitmap: Bitmap? = null
        var uprightBitmap: Bitmap? = null
        var croppedFace: Bitmap? = null
        var scaledFace: Bitmap? = null
        var previewCopy: Bitmap? = null

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
                Log.w(TAG, "Face bbox too small: ${faceWidth}x${faceHeight}")
                onNoFaceDetected()
                return
            }

            // Calculate face-to-image ratio for quality assessment
            val faceWidthRatio = bbox.width().toFloat() / imageWidth.coerceAtLeast(1)

            // Crop face directly from upright bitmap
            croppedFace = Bitmap.createBitmap(uprightBitmap, left, top, faceWidth, faceHeight)

            // Scale to model's expected input dimensions (read from tensor shape)
            scaledFace = Bitmap.createScaledBitmap(croppedFace, inputImageWidth, inputImageHeight, true)

            // CRITICAL: Create preview copy BEFORE any recycling happens
            previewCopy = scaledFace.copy(Bitmap.Config.ARGB_8888, false)

            // Extract pixels from scaled face
            scaledFace.getPixels(intValues, 0, inputImageWidth, 0, 0, inputImageWidth, inputImageHeight)

            var embedding = FloatArray(embeddingSize)

            val currentInterpreter = interpreter
            if (currentInterpreter != null) {
                inputBuffer.rewind()

                // Normalize pixel values for MobileFaceNet: (pixel - 127.5) / 128.0
                // Fill the buffer for every batch index the model expects
                for (b in 0 until batchSize) {
                    for (pixelValue in intValues) {
                        val r = ((pixelValue shr 16) and 0xFF)
                        val g = ((pixelValue shr 8) and 0xFF)
                        val bColor = (pixelValue and 0xFF)

                        inputBuffer.putFloat((r - 127.5f) / 128.0f)
                        inputBuffer.putFloat((g - 127.5f) / 128.0f)
                        inputBuffer.putFloat((bColor - 127.5f) / 128.0f)
                    }
                }

                // Rewind after writing so TFLite reads from position 0
                inputBuffer.rewind()

                val outputBuffer = Array(batchSize) { FloatArray(embeddingSize) }
                currentInterpreter.run(inputBuffer, outputBuffer)
                
                // L2-normalize the embedding vector
                embedding = FaceMath.l2Normalize(outputBuffer[0])
            } else {
                Log.e(TAG, "TFLite interpreter is null during face processing")
                onError("Face model not loaded")
                return
            }

            // Assess face quality
            val quality = FaceMath.assessFaceQuality(
                headEulerAngleX = face.headEulerAngleX,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                leftEyeOpen = face.leftEyeOpenProbability,
                rightEyeOpen = face.rightEyeOpenProbability,
                faceWidthRatio = faceWidthRatio,
                brightness = brightness
            )

            // Emit the result — previewCopy is safe to use after source recycling
            val result = FaceDetectionResult(
                embedding = embedding,
                previewBitmap = previewCopy,
                leftEyeOpen = face.leftEyeOpenProbability,
                rightEyeOpen = face.rightEyeOpenProbability,
                smiling = face.smilingProbability,
                brightness = brightness,
                headEulerAngleX = face.headEulerAngleX,
                headEulerAngleY = face.headEulerAngleY,
                headEulerAngleZ = face.headEulerAngleZ,
                quality = quality
            )

            // Mark previewCopy as null so finally block doesn't recycle it — ownership transferred to callback
            previewCopy = null
            onFaceDetected(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error in processFace: ${e.message}", e)
            onError("Error extracting face: ${e.message}")
        } finally {
            try {
                // Safely recycle all intermediate bitmaps
                previewCopy?.recycle()
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

    companion object {
        private const val TAG = "FaceAnalyzer"
    }
}

/**
 * Immutable result from a single face detection frame.
 */
data class FaceDetectionResult(
    val embedding: FloatArray,
    val previewBitmap: Bitmap,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val smiling: Float?,
    val brightness: Float,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val quality: FaceQualityResult
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceDetectionResult) return false
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int = embedding.contentHashCode()
}
