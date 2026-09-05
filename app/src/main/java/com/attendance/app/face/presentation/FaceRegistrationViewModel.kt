package com.attendance.app.face.presentation

import android.content.Context
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

class FaceRegistrationViewModel(private val httpClient: HttpClient) : ViewModel() {
    
    private val _registrationState = MutableStateFlow<FaceRegState>(FaceRegState.Idle)
    val registrationState: StateFlow<FaceRegState> = _registrationState.asStateFlow()

    fun onFaceDetected(embedding: FloatArray, context: Context) {
        // Only allow capture when Idle — prevents looping
        if (_registrationState.value != FaceRegState.Idle) return
        
        _registrationState.value = FaceRegState.Processing
        
        viewModelScope.launch {
            try {
                // Ensure embedding is L2-normalized
                val normalizedEmbedding = com.attendance.app.face.FaceMath.l2Normalize(embedding)
                val embeddingJson = org.json.JSONArray(normalizedEmbedding.toList()).toString()
                
                // Save locally as PENDING so it doesn't overwrite active face until approved
                val prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("pending_face_embedding", embeddingJson)
                    .putBoolean("face_approval_pending", true)
                    .apply()
                
                // Then try to send to server for teacher approval
                val response = httpClient.post("${Constants.BASE_URL}/requests/face") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("faceEmbeddingJSON" to embeddingJson))
                }
                if (response.status.isSuccess()) {
                    val respText = response.bodyAsText()
                    if (respText.contains("auto-approved")) {
                        prefs.edit()
                            .putString("registered_face_embedding", embeddingJson)
                            .remove("pending_face_embedding")
                            .putBoolean("face_approval_pending", false)
                            .apply()
                    }
                    _registrationState.value = FaceRegState.Success
                } else {
                    // Server rejected, but local save is done â€” still success for UX
                    _registrationState.value = FaceRegState.Success
                }
            } catch (e: Exception) {
                // Network error â€” face is saved locally, mark as success so student can proceed
                _registrationState.value = FaceRegState.Success
            }
        }
    }
    
    fun reset() {
        _registrationState.value = FaceRegState.Idle
    }
}

sealed class FaceRegState {
    object Idle : FaceRegState()
    object Processing : FaceRegState()
    object Success : FaceRegState()
    object Error : FaceRegState()
}



