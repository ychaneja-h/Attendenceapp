package com.attendance.app.face

import kotlin.math.sqrt

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
     * Normalizes a vector to unit length (L2 norm = 1.0).
     */
    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm == 0.0f) return vector
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }
}

