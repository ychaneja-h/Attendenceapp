package com.attendance.app.profile.data

import com.attendance.app.auth.data.TokenManager
import com.attendance.app.core.network.Constants
import com.attendance.shared.dto.ProfileResponse
import com.attendance.shared.dto.UpdateProfileRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.isSuccess

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileRepository(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager,
    private val context: android.content.Context
) {
    private val baseUrl = "${Constants.BASE_URL}/users/me"

    suspend fun getProfile(): Result<ProfileResponse> {
        val prefs = context.getSharedPreferences("profile_cache", android.content.Context.MODE_PRIVATE)
        return try {
            val token = tokenManager.getToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val response = httpClient.get(baseUrl) {

            }

            if (response.status.isSuccess()) {
                val profile: ProfileResponse = response.body()
                prefs.edit().putString("cached_profile", Json.encodeToString(profile)).apply()
                Result.success(profile)
            } else {
                // Network failed but not exception, try cache
                val cached = prefs.getString("cached_profile", null)
                if (cached != null) {
                    Result.success(Json.decodeFromString(cached))
                } else {
                    Result.failure(Exception("Failed to load profile: ${response.status}"))
                }
            }
        } catch (e: Exception) {
            val cached = prefs.getString("cached_profile", null)
            if (cached != null) {
                Result.success(Json.decodeFromString(cached))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProfile(name: String, email: String, department: String? = null, section: String? = null, batch: String? = null, year: String? = null, photoUrl: String? = null): Result<ProfileResponse> {
        return try {
            val token = tokenManager.getToken()
                ?: return Result.failure(Exception("Not authenticated"))

            val request = UpdateProfileRequest(name = name, email = email, department = department, section = section, batch = batch, year = year, photoUrl = photoUrl)

            val response = httpClient.put(baseUrl) {

                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to update profile: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfilePhoto(byteArray: ByteArray, fileName: String): Result<ProfileResponse> {
        return try {
            val token = tokenManager.getToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = httpClient.submitFormWithBinaryData(
                url = "${Constants.BASE_URL}/users/me/photo",
                formData = io.ktor.client.request.forms.formData {
                    append("image", byteArray, io.ktor.http.Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }
            ) {

            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Upload failed: ${response.status}"))
            }
        } catch(e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStudentSummary(): Result<StudentSummaryResponse> {
        return try {
            val token = tokenManager.getToken() ?: return Result.failure(Exception("Not authenticated"))
            val response = httpClient.get("${Constants.BASE_URL}/reports/student/summary") {

            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to load summary: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@kotlinx.serialization.Serializable
data class StudentSummaryResponse(
    val totalPresent: Int,
    val totalAbsent: Int
)



