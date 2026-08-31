package com.attendance.app.auth.data

import com.attendance.shared.dto.AuthTokenResponse
import com.attendance.shared.dto.LoginRequest
import com.attendance.shared.dto.StudentRegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import com.attendance.app.core.network.Constants

@Serializable
data class RegisterResponse(val sessionToken: String)

class AuthRepository(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager
) {
    private val baseUrl = "${Constants.BASE_URL}/auth"

    suspend fun registerStudent(request: StudentRegisterRequest): Result<AuthTokenResponse> {
        return try {
            val response = httpClient.post("$baseUrl/student/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveUserId(body.userId)
                tokenManager.saveRole(body.role)
                Result.success(body)
            } else {
                Result.failure(Exception("Registration failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerTeacher(request: com.attendance.shared.dto.TeacherRegisterRequest): Result<AuthTokenResponse> {
        return try {
            val response = httpClient.post("$baseUrl/teacher/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveUserId(body.userId)
                tokenManager.saveRole(body.role)
                Result.success(body)
            } else {
                Result.failure(Exception("Registration failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerHod(request: com.attendance.shared.dto.HodRegisterRequest): Result<AuthTokenResponse> {
        return try {
            val response = httpClient.post("$baseUrl/hod/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveUserId(body.userId)
                tokenManager.saveRole(body.role)
                Result.success(body)
            } else {
                Result.failure(Exception("Registration failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateLogin(request: LoginRequest): Result<AuthTokenResponse> {
        return try {
            val response = httpClient.post("$baseUrl/student/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveUserId(body.userId)
                tokenManager.saveRole(body.role)
                Result.success(body)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception(errorBody.takeIf { it.isNotBlank() } ?: "Login failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        tokenManager.clear()
    }

    suspend fun requestDeviceTransfer(newDeviceId: String): Result<Unit> {
        return try {
            val response = httpClient.post("${Constants.BASE_URL}/requests/device") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("newDeviceId" to newDeviceId))
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
