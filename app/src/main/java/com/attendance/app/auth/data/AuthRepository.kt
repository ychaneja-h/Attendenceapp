package com.attendance.app.auth.data

import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

import com.attendance.shared.dto.AuthTokenResponse
import com.attendance.shared.dto.LoginRequest
import com.attendance.shared.dto.StudentRegisterRequest
import com.attendance.shared.dto.RegistrationValidation
import com.attendance.shared.dto.normalizedForRegistration
import kotlinx.coroutines.CancellationException
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

    suspend fun verifyStaffCode(passcode: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/verify-staff-code") {
                contentType(ContentType.Application.Json)
                setBody(com.attendance.shared.dto.VerifyStaffCodeRequest(passcode))
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid passcode"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }

    suspend fun registerStudent(request: StudentRegisterRequest): Result<AuthTokenResponse> {
        val registration = request.normalizedForRegistration()
        RegistrationValidation.student(registration)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        return try {
            val response = httpClient.post("$baseUrl/student/register") {
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                tokenManager.saveToken(body.accessToken)
                tokenManager.saveUserId(body.userId)
                tokenManager.saveRole(body.role)
                Result.success(body)
            } else {
                Result.failure(Exception(authErrorMessage(response.bodyAsText(), response.status.value, "Check your registration details and try again.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }

    suspend fun registerTeacher(request: com.attendance.shared.dto.TeacherRegisterRequest): Result<AuthTokenResponse> {
        val registration = request.normalizedForRegistration()
        RegistrationValidation.teacher(registration)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        return try {
            val response = httpClient.post("$baseUrl/teacher/register") {
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                Result.success(body)
            } else {
                Result.failure(Exception(authErrorMessage(response.bodyAsText(), response.status.value, "Check your registration details and try again.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }

    suspend fun registerHod(request: com.attendance.shared.dto.HodRegisterRequest): Result<AuthTokenResponse> {
        val registration = request.normalizedForRegistration()
        RegistrationValidation.hod(registration)?.let { return Result.failure(IllegalArgumentException(it.message)) }
        return try {
            val response = httpClient.post("$baseUrl/hod/register") {
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            if (response.status.isSuccess()) {
                val body: AuthTokenResponse = response.body()
                Result.success(body)
            } else {
                Result.failure(Exception(authErrorMessage(response.bodyAsText(), response.status.value, "Check your registration details and try again.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
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
                Result.failure(Exception(authErrorMessage(response.bodyAsText(), response.status.value, "Unable to sign in. Check your College ID and password.")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }

    suspend fun changePassword(newPassword: String, currentPassword: String): Result<Unit> {
        return try {
            if (tokenManager.getToken() == null) return Result.failure(Exception("Not logged in"))
            val response = httpClient.post("${Constants.BASE_URL}/users/change-password") {
                contentType(ContentType.Application.Json)
                setBody(com.attendance.shared.dto.ChangePasswordRequest(newPassword, emptyList(), currentPassword))
            }
            if (response.status.isSuccess()) {
                // The server increments auth_version on success, invalidating all existing JWTs.
                tokenManager.clear()
                Result.success(Unit)
            } else {
                val errorText = response.bodyAsText()
                val obj = org.json.JSONObject(errorText)
                val msg = obj.optString("message", obj.optString("error", "Failed to change password"))
                Result.failure(Exception(msg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }

    fun getRole(): String? = tokenManager.getRole()
    fun logout() {
        tokenManager.clear()
    }

        suspend fun requestDeviceTransfer(collegeId: String, password: String, newDeviceId: String): Result<String> {
        return try {
            val response = httpClient.post("${Constants.BASE_URL}/auth/request-device-transfer") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "collegeId" to collegeId,
                    "password" to password,
                    "newDeviceId" to newDeviceId
                ))
            }
            if (response.status.isSuccess()) {
                val obj = org.json.JSONObject(response.bodyAsText())
                Result.success(obj.optString("message", "Request sent"))
            } else {
                val obj = org.json.JSONObject(response.bodyAsText())
                Result.failure(Exception(obj.optString("error", "Failed to request transfer")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(authRequestFailure(e))
        }
    }
}






















