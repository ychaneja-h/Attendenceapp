package com.attendance.app.face

import kotlin.math.sqrt
import kotlin.math.abs

object FaceMath {

    /**
     * Calculates the cosine similarity between two 128-dimensional vectors.
     * Value ranges from -1.0 to 1.0. A higher value indicates higher similarity.
     * Threshold is typically ~0.65 to 0.75 for MobileFaceNet.
     */
    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) throw IllegalArgumentException("Vectors must be same length (${vectorA.size} vs ${vectorB.size})")

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        if (normA == 0.0f || normB == 0.0f) return 0.0f

        return (dotProduct / (sqrt(normA) * sqrt(normB)))
    }

    /**
     * Calculates the Euclidean (L2) distance between two vectors.
     * Lower value = more similar. Typically < 1.0 means same person for MobileFaceNet.
     */
    fun euclideanDistance(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) throw IllegalArgumentException("Vectors must be same length")
        var sum = 0.0f
        for (i in vectorA.indices) {
            val diff = vectorA[i] - vectorB[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Normalizes a vector to unit length (L2 norm = 1.0).
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all { it.isFinite() }) { "Face scan is invalid. Please scan again." }
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        require(norm.isFinite() && norm > 0f) { "Face scan is invalid. Please scan again." }
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }

    /**
     * Averages multiple embeddings into a single robust template.
     * Used during multi-sample registration to create a stable face template.
     */
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val size = embeddings[0].size
        require(embeddings.all { it.size == size }) { "Face model changed. Please register again." }
        val avg = FloatArray(size)
        for (emb in embeddings) {
            for (i in avg.indices) {
                avg[i] += emb[i]
            }
        }
        for (i in avg.indices) {
            avg[i] /= embeddings.size.toFloat()
        }
        return l2Normalize(avg)
    }

    /**
     * Checks if a face meets quality requirements for registration/verification.
     * Returns a quality score 0.0-1.0 and a human-readable feedback message.
     */
    fun assessFaceQuality(
        headEulerAngleX: Float?,  // pitch (up/down)
        headEulerAngleY: Float?,  // yaw (left/right)
        headEulerAngleZ: Float?,  // roll (tilt)
        leftEyeOpen: Float?,
        rightEyeOpen: Float?,
        faceWidthRatio: Float,    // face width / image width
        brightness: Float
    ): FaceQualityResult {
        val issues = mutableListOf<String>()
        var score = 1.0f

        // Check head pose — face should be roughly frontal
        if (headEulerAngleY != null && abs(headEulerAngleY) > 20f) {
            issues.add("Look straight at camera")
            score -= 0.3f
        }
        if (headEulerAngleX != null && abs(headEulerAngleX) > 20f) {
            issues.add("Keep your head level")
            score -= 0.2f
        }
        if (headEulerAngleZ != null && abs(headEulerAngleZ) > 15f) {
            issues.add("Don't tilt your head")
            score -= 0.15f
        }

        // Check eyes are open (not blinking during capture)
        if (leftEyeOpen != null && rightEyeOpen != null) {
            if (leftEyeOpen < 0.4f || rightEyeOpen < 0.4f) {
                issues.add("Keep your eyes open")
                score -= 0.25f
            }
        }

        // Check face is large enough in frame
        if (faceWidthRatio < 0.25f) {
            issues.add("Move closer")
            score -= 0.3f
        } else if (faceWidthRatio > 0.85f) {
            issues.add("Move further away")
            score -= 0.2f
        }

        // Check lighting
        if (brightness < 30f) {
            issues.add("Too dark — find better lighting")
            score -= 0.4f
        } else if (brightness < 60f) {
            issues.add("Lighting is dim")
            score -= 0.15f
        } else if (brightness > 230f) {
            issues.add("Too bright — avoid direct light")
            score -= 0.2f
        }

        return FaceQualityResult(
            score = score.coerceIn(0f, 1f),
            isAcceptable = score >= 0.6f && issues.isEmpty(),
            feedback = if (issues.isEmpty()) "Perfect! Hold still." else issues.first()
        )
    }
}

data class FaceQualityResult(
    val score: Float,
    val isAcceptable: Boolean,
    val feedback: String
)
