package com.attendance.server.services

import com.attendance.server.models.ApprovalRequests
import com.attendance.server.models.Users
import com.attendance.server.models.ClassMembers
import com.attendance.server.models.Classes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class ApprovalRequestDTO(
    val id: String,
    val studentId: String,
    val studentName: String? = null,
    val requestType: String,
    val requestedData: String,
    val status: String,
    val createdAt: String
)

class RequestService {
        fun createFaceRequest(studentId: UUID, requestedData: String): String {
        return transaction {
            AccessControl.requireRole(studentId, setOf("STUDENT"))
            validateFace(requestedData)
            val user = Users.select { Users.id eq studentId }.forUpdate().singleOrNull()
            val pending = ApprovalRequests.select {
                (ApprovalRequests.studentId eq studentId) and
                    (ApprovalRequests.requestType eq "FACE_REGISTRATION") and (ApprovalRequests.status eq "PENDING")
            }.firstOrNull()
            if (pending != null) {
                require(pending[ApprovalRequests.requestedData] == requestedData) { "A face replacement is already awaiting teacher approval" }
                return@transaction "pending"
            }
            // Retrying an accepted initial upload must not create a replacement request.
            if (user?.get(Users.faceEmbedding) == requestedData) return@transaction "auto-approved"
            if (user != null && user[Users.faceEmbedding].isNullOrEmpty()) {
                Users.update({ Users.id eq studentId }) {
                    it[faceEmbedding] = requestedData
                }
                "auto-approved"
            } else {
                ApprovalRequests.insert {
                    it[this.studentId] = studentId
                    it[this.requestType] = "FACE_REGISTRATION"
                    it[this.requestedData] = requestedData
                    it[status] = "PENDING"
                    it[createdAt] = Instant.now()
                }
                "pending"
            }
        }
    }

    fun createRequest(studentId: UUID, requestType: String, requestedData: String) {
        transaction {
            AccessControl.requireRole(studentId, setOf("STUDENT"))
            require(requestType == "DEVICE_CHANGE" && requestedData.isNotBlank() && requestedData.length <= 255) { "Invalid device request" }
            ApprovalRequests.insert {
                it[this.studentId] = studentId
                it[this.requestType] = requestType
                it[this.requestedData] = requestedData
                it[status] = "PENDING"
                it[createdAt] = Instant.now()
            }
        }
    }

    fun getPendingRequests(actorId: UUID): List<ApprovalRequestDTO> {
        return transaction {
            AccessControl.requireRole(actorId, AccessControl.staffRoles)
            val students = (ClassMembers innerJoin Classes).select { (Classes.teacherId eq actorId) and
                (Classes.isActive eq true) and (ClassMembers.status eq "ACTIVE") }.map { it[ClassMembers.studentId] }
            (ApprovalRequests innerJoin Users).select { (ApprovalRequests.status eq "PENDING") and (ApprovalRequests.studentId inList students) }
                .map {
                    ApprovalRequestDTO(
                        id = it[ApprovalRequests.id].toString(),
                        studentId = it[ApprovalRequests.studentId].toString(),
                        studentName = it[Users.name],
                        requestType = it[ApprovalRequests.requestType],
                        requestedData = if (it[ApprovalRequests.requestType] == "FACE_REGISTRATION") "Face registration requested" else it[ApprovalRequests.requestedData],
                        status = it[ApprovalRequests.status],
                        createdAt = it[ApprovalRequests.createdAt].toString()
                    )
                }
        }
    }

    fun getMyRequests(studentId: UUID): List<ApprovalRequestDTO> {
        return transaction {
            (ApprovalRequests innerJoin Users).select { ApprovalRequests.studentId eq studentId }
                .orderBy(ApprovalRequests.createdAt to SortOrder.DESC)
                .map {
                    ApprovalRequestDTO(
                        id = it[ApprovalRequests.id].toString(),
                        studentId = it[ApprovalRequests.studentId].toString(),
                        studentName = it[Users.name],
                        requestType = it[ApprovalRequests.requestType],
                        requestedData = it[ApprovalRequests.requestedData],
                        status = it[ApprovalRequests.status],
                        createdAt = it[ApprovalRequests.createdAt].toString()
                    )
                }
        }
    }

    fun approveRequest(requestId: UUID, actorId: UUID) {
        transaction {
            val req = ApprovalRequests.select { ApprovalRequests.id eq requestId }.forUpdate().singleOrNull() ?: throw IllegalArgumentException("Request not found")
            AccessControl.requireStudentTeacher(actorId, req[ApprovalRequests.studentId])
            require(req[ApprovalRequests.status] == "PENDING") { "Request is no longer pending" }
            
            ApprovalRequests.update({ ApprovalRequests.id eq requestId }) {
                it[status] = "APPROVED"
            }
            
            if (req[ApprovalRequests.requestType] == "DEVICE_CHANGE") {
                val student = Users.select { Users.id eq req[ApprovalRequests.studentId] }.forUpdate().single()
                Users.update({ Users.id eq req[ApprovalRequests.studentId] }) {
                    it[deviceId] = req[ApprovalRequests.requestedData]
                    it[authVersion] = student[Users.authVersion] + 1
                }
            } else if (req[ApprovalRequests.requestType] == "FACE_REGISTRATION") {
                validateFace(req[ApprovalRequests.requestedData])
                Users.update({ Users.id eq req[ApprovalRequests.studentId] }) {
                    it[faceEmbedding] = req[ApprovalRequests.requestedData]
                }
            }
        }
    }

    fun rejectRequest(requestId: UUID, actorId: UUID) {
        transaction {
            val req = ApprovalRequests.select { ApprovalRequests.id eq requestId }.forUpdate().singleOrNull() ?: throw IllegalArgumentException("Request not found")
            AccessControl.requireStudentTeacher(actorId, req[ApprovalRequests.studentId])
            require(req[ApprovalRequests.status] == "PENDING") { "Request is no longer pending" }
            ApprovalRequests.update({ ApprovalRequests.id eq requestId }) {
                it[status] = "REJECTED"
            }
        }
    }

    private fun validateFace(value: String) {
        require(value.length <= 20_000) { "Face template is too large" }
        val vector = kotlinx.serialization.json.Json.decodeFromString<List<Float>>(value)
        require(vector.size in setOf(128, 192, 512) && vector.all { it.isFinite() } && vector.any { it != 0f }) { "Invalid face template" }
    }
}



