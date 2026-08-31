package com.attendance.server.services

import com.attendance.server.models.Users
import com.attendance.shared.dto.HodProfileDto
import com.attendance.shared.model.UserRole
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

object AdminService {
    fun getPendingHods(): List<HodProfileDto> {
        return transaction {
            Users.select { (Users.role eq UserRole.HOD.name) and (Users.accountStatus eq "PENDING") }
                .map {
                    HodProfileDto(
                        id = it[Users.id].toString(),
                        name = it[Users.name],
                        facultyId = it[Users.collegeId],
                        status = it[Users.accountStatus],
                        joinedAt = it[Users.createdAt].toString()
                    )
                }
        }
    }

    fun getActiveHods(): List<HodProfileDto> {
        return transaction {
            Users.select { (Users.role eq UserRole.HOD.name) and (Users.accountStatus eq "ACTIVE") }
                .map {
                    HodProfileDto(
                        id = it[Users.id].toString(),
                        name = it[Users.name],
                        facultyId = it[Users.collegeId],
                        status = it[Users.accountStatus],
                        joinedAt = it[Users.createdAt].toString()
                    )
                }
        }
    }

    fun approveHod(hodId: UUID) {
        transaction {
            val hod = Users.select { Users.id eq hodId }.singleOrNull() ?: throw Exception("HOD not found")
            Users.update({ Users.id eq hodId }) {
                it[accountStatus] = "ACTIVE"
            }
        }
    }

    fun rejectHod(hodId: UUID) {
        deleteHod(hodId)
    }

    fun deleteHod(hodId: UUID) {
        transaction {
            val hod = Users.select { Users.id eq hodId }.singleOrNull() ?: throw Exception("HOD not found")
            Users.deleteWhere { Users.id eq hodId }
        }
    }
}
