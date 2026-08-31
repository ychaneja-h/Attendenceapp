package com.attendance.server.services

import com.attendance.server.models.Users
import com.attendance.shared.dto.TeacherProfileDto
import com.attendance.shared.model.UserRole
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

object HodService {
    fun getPendingTeachers(hodId: UUID): List<TeacherProfileDto> {
        return transaction {
            val hodDeptCode = Users.select { Users.id eq hodId }.singleOrNull()?.get(Users.departmentCode) ?: return@transaction emptyList()
            
            Users.select { (Users.departmentCode eq hodDeptCode) and (Users.role eq UserRole.TEACHER.name) and (Users.accountStatus eq "PENDING") }
                .map {
                    TeacherProfileDto(
                        id = it[Users.id].toString(),
                        name = it[Users.name],
                        facultyId = it[Users.collegeId],
                        phone = it[Users.phone],
                        status = it[Users.accountStatus],
                        joinedAt = it[Users.createdAt].toString()
                    )
                }
        }
    }

    fun getActiveTeachers(hodId: UUID): List<TeacherProfileDto> {
        return transaction {
            val hodDeptCode = Users.select { Users.id eq hodId }.singleOrNull()?.get(Users.departmentCode) ?: return@transaction emptyList()
            
            Users.select { (Users.departmentCode eq hodDeptCode) and (Users.role eq UserRole.TEACHER.name) and (Users.accountStatus eq "ACTIVE") }
                .map {
                    TeacherProfileDto(
                        id = it[Users.id].toString(),
                        name = it[Users.name],
                        facultyId = it[Users.collegeId],
                        phone = it[Users.phone],
                        status = it[Users.accountStatus],
                        joinedAt = it[Users.createdAt].toString()
                    )
                }
        }
    }

    fun approveTeacher(hodId: UUID, teacherId: UUID) {
        transaction {
            val hodDeptCode = Users.select { Users.id eq hodId }.singleOrNull()?.get(Users.departmentCode) ?: throw Exception("HOD not found")
            val teacher = Users.select { Users.id eq teacherId }.singleOrNull() ?: throw Exception("Teacher not found")
            
            if (teacher[Users.departmentCode] != hodDeptCode) {
                throw SecurityException("Unauthorized to approve this teacher")
            }
            
            Users.update({ Users.id eq teacherId }) {
                it[accountStatus] = "ACTIVE"
            }
        }
    }

    fun rejectTeacher(hodId: UUID, teacherId: UUID) {
        deleteTeacher(hodId, teacherId)
    }

    fun deleteTeacher(hodId: UUID, teacherId: UUID) {
        transaction {
            val hodDeptCode = Users.select { Users.id eq hodId }.singleOrNull()?.get(Users.departmentCode) ?: throw Exception("HOD not found")
            val teacher = Users.select { Users.id eq teacherId }.singleOrNull() ?: throw Exception("Teacher not found")
            
            if (teacher[Users.departmentCode] != hodDeptCode) {
                throw SecurityException("Unauthorized to delete this teacher")
            }
            
            Users.deleteWhere { Users.id eq teacherId }
        }
    }
}
