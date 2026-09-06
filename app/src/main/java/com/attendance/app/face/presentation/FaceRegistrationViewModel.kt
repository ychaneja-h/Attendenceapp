package com.attendance.app.face.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.attendance.app.core.network.Constants
import com.attendance.app.face.FaceMath

/**
 * Multi-sample face registration ViewModel.
 * 
 * Collects REQUIRED_SAMPLES (3) high-quality embeddings, validates they are
 * consistent (same person across all samples), averages them into a single
 * robust template, and saves locally + syncs to backend.
 * 
 * State machine: Idle → Capturing(n/3) → Uploading → Success | Error
 */
class FaceRegistrationViewModel(private val httpClient: HttpClient) : ViewModel() {
    
    companion object {
        private const val TAG = "FaceRegVM"
        private const val REQUIRED_SAMPLES = 3
        // Minimum cosine similarity between samples to accept (same person check)
        private const val INTER_SAMPLE_THRESHOLD = 0.55f
    }

    private val _registrationState = MutableStateFlow<FaceRegState>(FaceRegState.Idle)
    val registrationState: StateFlow<FaceRegState> = _registrationState.asStateFlow()

    private val collectedEmbeddings = mutableListOf<FloatArray>()

    /**
     * Called when a high-quality face embedding is ready to be captured.
     * Collects up to REQUIRED_SAMPLES, validates consistency, then saves.
     */
    fun captureEmbedding(embedding: FloatArray, context: Context) {
        val currentState = _registrationState.value
        
        // Only allow capture during Idle or Capturing states
        if (currentState !is FaceRegState.Idle && currentState !is FaceRegState.Capturing) {
            return
        }

        val normalizedEmbedding = FaceMath.l2Normalize(embedding)

        // Validate this sample is consistent with previous samples (anti-spoofing)
        if (collectedEmbeddings.isNotEmpty()) {
            val avgSoFar = FaceMath.averageEmbeddings(collectedEmbeddings)
            val similarity = FaceMath.cosineSimilarity(normalizedEmbedding, avgSoFar)
            if (similarity < INTER_SAMPLE_THRESHOLD) {
                Log.w(TAG, "Sample rejected: similarity $similarity < $INTER_SAMPLE_THRESHOLD (different face?)")
                _registrationState.value = FaceRegState.Error("Face changed between captures. Hold still and try again.")
                return
            }
        }

        collectedEmbeddings.add(normalizedEmbedding)
        val count = collectedEmbeddings.size

        Log.d(TAG, "Captured sample $count/$REQUIRED_SAMPLES")

        if (count < REQUIRED_SAMPLES) {
            _registrationState.value = FaceRegState.Capturing(count, REQUIRED_SAMPLES)
        } else {
            // All samples collected — average and save
            _registrationState.value = FaceRegState.Uploading
            saveTemplate(context)
        }
    }

    private fun saveTemplate(context: Context) {
        viewModelScope.launch {
            try {
                // Average all collected embeddings into one robust template
                val template = FaceMath.averageEmbeddings(collectedEmbeddings)
                val embeddingJson = org.json.JSONArray(template.toList()).toString()

                // Save locally
                val prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("registered_face_embedding", embeddingJson)
                    .putString("pending_face_embedding", embeddingJson)
                    .putBoolean("face_approval_pending", false)
                    .apply()

                Log.d(TAG, "Face template saved locally (${collectedEmbeddings.size} samples averaged)")

                // Sync to server (non-blocking — local registration is the source of truth)
                try {
                    httpClient.post("${Constants.BASE_URL}/requests/face") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("faceEmbeddingJSON" to embeddingJson))
                    }
                    Log.d(TAG, "Face template synced to server")
                } catch (e: Exception) {
                    Log.w(TAG, "Server sync failed (non-fatal): ${e.message}")
                }

                _registrationState.value = FaceRegState.Success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save face template: ${e.message}", e)
                _registrationState.value = FaceRegState.Error("Registration failed: ${e.message}")
            }
        }
    }

    fun reset() {
        collectedEmbeddings.clear()
        _registrationState.value = FaceRegState.Idle
    }
}

sealed class FaceRegState {
    /** Ready to start capturing */
    object Idle : FaceRegState()
    
    /** Collecting samples: captured/total */
    data class Capturing(val captured: Int, val total: Int) : FaceRegState()
    
    /** All samples collected, saving template */
    object Uploading : FaceRegState()
    
    /** Registration completed successfully */
    object Success : FaceRegState()
    
    /** Error with retry capability */
    data class Error(val message: String) : FaceRegState()
}
