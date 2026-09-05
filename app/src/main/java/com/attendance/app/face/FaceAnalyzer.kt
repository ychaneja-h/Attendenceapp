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

    // ML Kit Face Detector (Fast, Offline, Accurate)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    // TFLite Interpreter for MobileFaceNet
    private var interpreter: Interpreter? = null
    
    // MobileFaceNet Input specs: 112x112 RGB
    private val INPUT_IMAGE_SIZE = 112
    private val EMBEDDING_SIZE = 128

    init {
        try {
            val model = loadModelFile(context, "mobile_face_net.tflite")
            if (model != null) {
                interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 4 })
                Log.d("FaceAnalyzer", "TFLite Model loaded successfully")
            } else {
                Log.w("FaceAnalyzer", "Model not found in assets, using mock embedding generator.")
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
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        // Get the largest face
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        largestFace?.let { face ->
                            processFace(face, imageProxy, mediaImage)
                        }
                    } else {
                        onNoFaceDetected()
                        imageProxy.close()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Face detection failed", e)
                    onError("Face detection failed: ${e.message}")
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

        @SuppressLint("UnsafeOptInUsageError")
    private fun processFace(face: Face, imageProxy: ImageProxy, mediaImage: android.media.Image) {
        try {
            val yBuffer = imageProxy.planes[0].buffer
            val yBufferDup = yBuffer.duplicate()
            val yData = ByteArray(yBufferDup.remaining())
            yBufferDup.get(yData)
            val brightness = yData.map { it.toInt() and 0xFF }.average().toFloat()
            
            // Extract full bitmap using CameraX built-in method
            val fullBitmap = imageProxy.toBitmap()
            
            // Rotate the bitmap to match the screen orientation
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, fullBitmap.height, matrix, true)
            
            // Crop face bounding box
            val box = face.boundingBox
            val left = maxOf(0, box.left)
            val top = maxOf(0, box.top)
            val right = minOf(rotatedBitmap.width, box.right)
            val bottom = minOf(rotatedBitmap.height, box.bottom)
            var width = right - left
            var height = bottom - top
            
            val faceBitmap = if (width > 0 && height > 0) {
                Bitmap.createBitmap(rotatedBitmap, left, top, width, height)
            } else {
                Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            }
            
            val scaledFace = Bitmap.createScaledBitmap(faceBitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)
            val embedding = FloatArray(EMBEDDING_SIZE)
            
            if (interpreter != null) {
                val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                
                val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
                scaledFace.getPixels(intValues, 0, scaledFace.width, 0, 0, scaledFace.width, scaledFace.height)
                
                for (pixelValue in intValues) {
                    val r = ((pixelValue shr 16) and 0xFF)
                    val g = ((pixelValue shr 8) and 0xFF)
                    val b = (pixelValue and 0xFF)
                    
                    // Normalize to [-1, 1]
                    inputBuffer.putFloat((r - 127.5f) / 128.0f)
                    inputBuffer.putFloat((g - 127.5f) / 128.0f)
                    inputBuffer.putFloat((b - 127.5f) / 128.0f)
                }
                
                val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
                interpreter?.run(inputBuffer, outputBuffer)
                System.arraycopy(outputBuffer[0], 0, embedding, 0, EMBEDDING_SIZE)
            } else {
                for (i in 0 until EMBEDDING_SIZE) {
                    embedding[i] = Math.random().toFloat()
                }
            }
            
            onFaceDetected(embedding, scaledFace, face.leftEyeOpenProbability, face.rightEyeOpenProbability, face.smilingProbability, brightness)
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error processing face", e)
            onError("Error processing face")
        } finally {
            imageProxy.close()
        }
    }
            
            // Create a dummy bitmap for the UI to display the "captured" face
            val dummyBitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            
            onFaceDetected(embedding, dummyBitmap, face.leftEyeOpenProbability, face.rightEyeOpenProbability, face.smilingProbability, brightness)
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error processing face", e)
            onError("Error processing face")
        } finally {
            imageProxy.close() // ALWAYS close the image proxy to free the camera buffer
        }
    }
}

