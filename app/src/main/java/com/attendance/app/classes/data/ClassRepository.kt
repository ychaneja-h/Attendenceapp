package com.attendance.app.classes.data

import com.attendance.app.auth.data.TokenManager
import com.attendance.shared.dto.ClassResponse
import com.attendance.shared.dto.CreateClassRequest
import com.attendance.shared.dto.JoinClassRequest
import com.attendance.shared.dto.JoinRequestResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.delete
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

import com.attendance.app.core.data.local.room.ClassDao
import com.attendance.app.core.data.local.room.ClassEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClassRepository(
    private val httpClient: HttpClient,
    private val tokenManager: TokenManager,
    private val classDao: ClassDao
) {
    private val baseUrl = "${com.attendance.app.core.network.Constants.BASE_URL}/classes"

    private fun getAuthHeader(): String {
        val token = tokenManager.getToken() ?: throw IllegalStateException("Not authenticated")
        return "Bearer $token"
    }

    // Expose local DB as the single source of truth
    fun getLocalClasses(): Flow<List<ClassResponse>> {
        return classDao.getAllClasses().map { entities ->
            entities.map {
                ClassResponse(
                    id = it.id,
                    name = it.name,
                    section = it.section,
                    semester = it.semester,
                    department = it.department,
                    academicYear = it.academicYear,
                    classCode = it.classCode,
                    teacherName = it.teacherName,
                    studentCount = it.studentCount,
                    isActive = it.isActive
                )
            }
        }
    }

    // Network Sync functions
    suspend fun fetchTeacherClasses(): Result<Unit> {
        return try {
            val response = httpClient.get("$baseUrl/teacher") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                val list: List<ClassResponse> = response.body()
                syncToLocalDb(list)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load classes: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchStudentClasses(): Result<Unit> {
        return try {
            val response = httpClient.get("$baseUrl/student") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                val list: List<ClassResponse> = response.body()
                syncToLocalDb(list)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load classes: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncToLocalDb(list: List<ClassResponse>) {
        val entities = list.map {
            ClassEntity(
                id = it.id,
                name = it.name,
                section = it.section,
                semester = it.semester,
                department = it.department,
                academicYear = it.academicYear,
                classCode = it.classCode,
                teacherName = it.teacherName,
                studentCount = it.studentCount,
                isActive = it.isActive
            )
        }
        classDao.replaceAllClasses(entities)
    }

    suspend fun createClass(request: CreateClassRequest): Result<ClassResponse> {
        return try {
            val response = httpClient.post(baseUrl) {
                header(HttpHeaders.Authorization, getAuthHeader())
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to create class: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinClass(classCode: String): Result<JoinRequestResponse> {
        return try {
            val response = httpClient.post("$baseUrl/join") {
                header(HttpHeaders.Authorization, getAuthHeader())
                contentType(ContentType.Application.Json)
                setBody(JoinClassRequest(classCode))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to join class: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingRequests(classId: String): Result<List<JoinRequestResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/$classId/pending") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch requests: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveRequest(classId: String, requestId: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/approve/$requestId") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to approve request: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRoster(classId: String): Result<List<JoinRequestResponse>> {
        return try {
            val response = httpClient.get("$baseUrl/$classId/roster") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch roster: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectRequest(classId: String, requestId: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/reject/$requestId") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to reject request: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            val response = httpClient.delete("$baseUrl/$classId") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete class: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeStudent(classId: String, studentId: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/$classId/students/$studentId/remove") {
                header(HttpHeaders.Authorization, getAuthHeader())
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove student: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
