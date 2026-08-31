package com.attendance.app.face

import kotlin.math.sqrt

object FaceMath {
    
    /**
     * Calculates the cosine similarity between two 128-dimensional vectors.
     * Value ranges from -1.0 to 1.0. A higher value indicates higher similarity.
     * Threshold is typically ~0.7 to 0.8 for MobileFaceNet.
     */
    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) throw IllegalArgumentException("Vectors must be same length")
        
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
}
