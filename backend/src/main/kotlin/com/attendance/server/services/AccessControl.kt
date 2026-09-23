package com.attendance.server.services

import com.attendance.server.models.*
import org.jetbrains.exposed.sql.*
import java.util.UUID

/** All checks run inside the caller's transaction, before reading or changing protected rows. */
internal object AccessControl {
    val staffRoles = setOf("TEACHER", "HOD", "ADMIN")

    fun requireRole(actorId: UUID, roles: Set<String>): ResultRow {
        val actor = Users.select { Users.id eq actorId }.singleOrNull()
            ?: throw SecurityException("Account unavailable")
        if (!isActiveRole(actor[Users.role], actor[Users.accountStatus], actor[Users.registrationStatus], roles)) {
            throw SecurityException("Access denied")
        }
        return actor
    }

    fun isActiveRole(role: String, accountStatus: String, registrationStatus: String, roles: Set<String>): Boolean =
        role in roles && accountStatus == "ACTIVE" && registrationStatus == "ACTIVE"

    fun requireClassOwner(actorId: UUID, classId: UUID) {
        requireRole(actorId, staffRoles)
        val owner = Classes.select { Classes.id eq classId }.singleOrNull()?.get(Classes.teacherId)
        if (owner != actorId) throw SecurityException("Only the class owner may perform this action")
    }

    fun requireStudentTeacher(actorId: UUID, studentId: UUID) {
        requireRole(actorId, staffRoles)
        val enrolled = (ClassMembers innerJoin Classes).select {
            (ClassMembers.studentId eq studentId) and (ClassMembers.status eq "ACTIVE") and
                (Classes.teacherId eq actorId) and (Classes.isActive eq true)
        }.any()
        if (!enrolled) throw SecurityException("Only the student's class teacher may review this request")
    }
}
