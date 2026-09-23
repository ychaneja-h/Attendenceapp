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
    private val classDao: ClassDao,
    private val studentDao: com.attendance.app.core.data.local.room.StudentDao
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
                    isActive = it.isActive,
                    memberStatus = it.memberStatus
                )
            }
        }
    }

    // Network Sync functions
        suspend fun fetchTeacherClasses(): Result<Unit> {
        return try {
            val response = httpClient.get("$baseUrl/teacher") {

            }
            if (response.status.isSuccess()) {
                val list: List<ClassResponse> = response.body()
                syncToLocalDb(list)
                
                // Sync roster for offline use
                list.forEach { cls ->
                    val rosterResp = httpClient.get("$baseUrl/${cls.id}/roster")
                    if (rosterResp.status.isSuccess()) {
                        val roster: List<com.attendance.shared.dto.JoinRequestResponse> = rosterResp.body()
                        val studentEntities = roster.map {
                            com.attendance.app.core.data.local.room.StudentEntity(
                                id = it.studentId,
                                classId = cls.id,
                                studentName = it.studentName,
                                collegeId = it.collegeId,
                                status = it.status,
                                joinedAt = it.requestedAt
                            )
                        }
                        studentDao.replaceStudentsForClass(cls.id, studentEntities)
                    }
                }
                
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
                isActive = it.isActive,
                memberStatus = it.memberStatus
            )
        }
        classDao.replaceAllClasses(entities)
    }

    suspend fun createClass(request: CreateClassRequest): Result<ClassResponse> {
        return try {
            val response = httpClient.post(baseUrl) {

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

            }
            if (response.status.isSuccess()) {
                val roster: List<JoinRequestResponse> = response.body()
                studentDao.replaceStudentsForClass(classId, roster.map {
                    com.attendance.app.core.data.local.room.StudentEntity(
                        id = it.studentId, classId = classId, studentName = it.studentName,
                        collegeId = it.collegeId, status = it.status, joinedAt = it.requestedAt
                    )
                })
                Result.success(roster)
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


