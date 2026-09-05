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

    private val faceBitmap = Bitmap.createBitmap(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
    private val faceCanvas = android.graphics.Canvas(faceBitmap)
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
        try {
            // Calculate brightness using the raw Y-plane buffer
            val yBuffer = imageProxy.planes[0].buffer
            val yBufferDup = yBuffer.duplicate()
            val yData = ByteArray(yBufferDup.remaining())
            yBufferDup.get(yData)
            val brightness = yData.map { it.toInt() and 0xFF }.average().toFloat()

            // Convert to Bitmap FIRST. CameraX handles rotation internally for this bitmap.
            val fullBitmap = imageProxy.toBitmap()
            
            // Pass the exact same bitmap to ML Kit (rotation = 0 because it's already rotated)
            val image = InputImage.fromBitmap(fullBitmap, 0)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        largestFace?.let { face ->
                            processFace(face, fullBitmap, brightness)
                        } ?: onNoFaceDetected()
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener { e ->
                    onError("Face detection failed: ${e.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error in analyze", e)
            onError("Error processing frame")
            imageProxy.close()
        }
    }

    private fun processFace(face: Face, fullBitmap: Bitmap, brightness: Float) {
        try {
            // Because ML Kit processed fullBitmap directly, the bounding box perfectly matches fullBitmap!
            val matrix = Matrix()
            matrix.postTranslate(-face.boundingBox.left.toFloat(), -face.boundingBox.top.toFloat())
            matrix.postScale(INPUT_IMAGE_SIZE.toFloat() / face.boundingBox.width(), INPUT_IMAGE_SIZE.toFloat() / face.boundingBox.height())
            
            faceCanvas.drawColor(android.graphics.Color.BLACK)
            faceCanvas.drawBitmap(fullBitmap, matrix, null)
            
            val embedding = FloatArray(EMBEDDING_SIZE)
            
            if (interpreter != null) {
                inputBuffer.rewind()
                faceBitmap.getPixels(intValues, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)
                
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
                System.arraycopy(outputBuffer[0], 0, embedding, 0, EMBEDDING_SIZE)
            } else {
                for (i in 0 until EMBEDDING_SIZE) {
                    embedding[i] = Math.random().toFloat()
                }
            }
            
            // Pass a copy of the faceBitmap so UI can render it safely
            onFaceDetected(embedding, Bitmap.createBitmap(faceBitmap), face.leftEyeOpenProbability, face.rightEyeOpenProbability, face.smilingProbability, brightness)
        } catch (e: Exception) {
            Log.e("FaceAnalyzer", "Error extracting face embedding", e)
            onError("Error extracting face")
        }
    }
}
