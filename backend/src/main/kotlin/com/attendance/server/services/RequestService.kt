package com.attendance.server.services

import com.attendance.server.models.ApprovalRequests
import com.attendance.server.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalRequestDTO(
    val id: String,
    val studentId: String,
    val requestType: String,
    val requestedData: String,
    val status: String,
    val createdAt: String
)

class RequestService {
    fun createRequest(studentId: UUID, requestType: String, requestedData: String) {
        transaction {
            ApprovalRequests.insert {
                it[this.studentId] = studentId
                it[this.requestType] = requestType
                it[this.requestedData] = requestedData
                it[status] = "PENDING"
                it[createdAt] = Instant.now()
            }
        }
    }

    fun getPendingRequests(): List<ApprovalRequestDTO> {
        return transaction {
            ApprovalRequests.select { ApprovalRequests.status eq "PENDING" }
                .map {
                    ApprovalRequestDTO(
                        id = it[ApprovalRequests.id].toString(),
                        studentId = it[ApprovalRequests.studentId].toString(),
                        requestType = it[ApprovalRequests.requestType],
                        requestedData = it[ApprovalRequests.requestedData],
                        status = it[ApprovalRequests.status],
                        createdAt = it[ApprovalRequests.createdAt].toString()
                    )
                }
        }
    }

    fun getMyRequests(studentId: UUID): List<ApprovalRequestDTO> {
        return transaction {
            ApprovalRequests.select { ApprovalRequests.studentId eq studentId }
                .orderBy(ApprovalRequests.createdAt to SortOrder.DESC)
                .map {
                    ApprovalRequestDTO(
                        id = it[ApprovalRequests.id].toString(),
                        studentId = it[ApprovalRequests.studentId].toString(),
                        requestType = it[ApprovalRequests.requestType],
                        requestedData = it[ApprovalRequests.requestedData],
                        status = it[ApprovalRequests.status],
                        createdAt = it[ApprovalRequests.createdAt].toString()
                    )
                }
        }
    }

    fun approveRequest(requestId: UUID) {
        transaction {
            val req = ApprovalRequests.select { ApprovalRequests.id eq requestId }.singleOrNull() ?: throw IllegalArgumentException("Request not found")
            
            ApprovalRequests.update({ ApprovalRequests.id eq requestId }) {
                it[status] = "APPROVED"
            }
            
            if (req[ApprovalRequests.requestType] == "DEVICE_CHANGE") {
                Users.update({ Users.id eq req[ApprovalRequests.studentId] }) {
                    it[deviceId] = req[ApprovalRequests.requestedData]
                }
            } else if (req[ApprovalRequests.requestType] == "FACE_REGISTRATION") {
                Users.update({ Users.id eq req[ApprovalRequests.studentId] }) {
                    it[faceEmbedding] = req[ApprovalRequests.requestedData]
                }
            }
        }
    }

    fun rejectRequest(requestId: UUID) {
        transaction {
            ApprovalRequests.update({ ApprovalRequests.id eq requestId }) {
                it[status] = "REJECTED"
            }
        }
    }
}
