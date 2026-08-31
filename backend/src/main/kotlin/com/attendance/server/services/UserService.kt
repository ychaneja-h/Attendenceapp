package com.attendance.server.services

import com.attendance.server.models.Users
import com.attendance.shared.dto.ProfileResponse
import com.attendance.shared.dto.UpdateProfileRequest
import java.util.UUID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

class UserService {
    fun getProfile(userId: UUID): ProfileResponse = transaction {
        val row = Users.select { Users.id eq userId }.singleOrNull()
            ?: throw Exception("User not found")
            
        ProfileResponse(
            id = row[Users.id].toString(),
            name = row[Users.name],
            email = row[Users.email],
            phone = row[Users.phone],
            role = row[Users.role],
            department = row[Users.department],
            section = row[Users.section],
            batch = row[Users.batch],
            year = row[Users.academicYear],
            photoUrl = row[Users.photoUrl]
        )
    }

    fun updateProfile(userId: UUID, req: UpdateProfileRequest): ProfileResponse = transaction {
        Users.update({ Users.id eq userId }) {
            it[name] = req.name
            it[email] = req.email
            it[phone] = req.phone
            req.department?.let { dept -> it[department] = dept }
            req.section?.let { sec -> it[section] = sec }
            req.batch?.let { b -> it[batch] = b }
            req.year?.let { yr -> it[academicYear] = yr }
            req.photoUrl?.let { url -> it[photoUrl] = url }
        }
        getProfile(userId)
    }

    fun updatePhoto(userId: UUID, photoUrl: String): ProfileResponse = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.photoUrl] = photoUrl
        }
        getProfile(userId)
    }
}
