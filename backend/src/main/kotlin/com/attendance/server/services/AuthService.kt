package com.attendance.server.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.attendance.server.models.*
import com.attendance.shared.dto.*
import com.attendance.shared.model.OtpPurpose
import com.attendance.shared.model.RegistrationStatus
import com.attendance.shared.model.UserRole
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

object AuthService {
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"
    private val jwtIssuer = System.getenv("JWT_ISSUER") ?: "attendance-server"
    private val jwtAudience = System.getenv("JWT_AUDIENCE") ?: "attendance-app"

    fun registerStudent(request: StudentRegisterRequest): String {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                return@transaction existingUser[Users.id].toString()
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[passwordHash] = hashedPassword
                it[role] = UserRole.STUDENT.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name // Auto active for dev/testing
                it[department] = request.department
                it[section] = request.section
                it[semester] = request.semester
                it[academicYear] = request.academicYear
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[Users.id]
            
            userId.toString()
        }
    }

    fun registerHod(request: HodRegisterRequest): String {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                return@transaction existingUser[Users.id].toString()
            }

            if (Users.select { (Users.departmentCode eq request.departmentCode) and (Users.role eq UserRole.HOD.name) }.count() > 0L) {
                throw Exception("Department Code already in use by another HOD")
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[passwordHash] = hashedPassword
                it[role] = UserRole.HOD.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name
                it[department] = request.department
                it[departmentCode] = request.departmentCode
                it[accountStatus] = "PENDING"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[Users.id]

            userId.toString()
        }
    }

    fun registerTeacher(request: TeacherRegisterRequest): String {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                return@transaction existingUser[Users.id].toString()
            }

            if (Users.select { (Users.departmentCode eq request.departmentCode) and (Users.role eq UserRole.HOD.name) }.count() == 0L) {
                throw Exception("Invalid Department Code")
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[passwordHash] = hashedPassword
                it[role] = UserRole.TEACHER.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name
                it[departmentCode] = request.departmentCode
                it[accountStatus] = "PENDING"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[Users.id]

            userId.toString()
        }
    }

    fun initiateLogin(request: LoginRequest): AuthTokenResponse {
        return transaction {
            val user = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
                ?: throw IllegalArgumentException("Invalid credentials")
            
            val result = BCrypt.verifyer().verify(request.password.toCharArray(), user[Users.passwordHash])
            if (!result.verified) {
                throw IllegalArgumentException("Invalid credentials")
            }

            if (user[Users.accountStatus] == "PENDING") {
                throw SecurityException("PENDING_APPROVAL")
            }

            if (user[Users.registrationStatus] == RegistrationStatus.SUSPENDED.name) {
                throw SecurityException("Account suspended")
            }

            val currentDeviceId = user[Users.deviceId]
            if (currentDeviceId == null) {
                Users.update({ Users.id eq user[Users.id] }) {
                    it[deviceId] = request.deviceFingerprint
                }
            } else if (currentDeviceId != request.deviceFingerprint) {
                throw SecurityException("DEVICE_MISMATCH")
            }
            
            // Wait, we need to update lastLoginAt, but Tables.kt doesn't have it!
            // Let me check what we can update, probably nothing if lastLoginAt isn't in Users.
            // Oh wait! Devices table has lastActiveAt. Or I'll just check if it was asked.
            // "Ensure lastLoginAt is updated." - If Users table doesn't have it, I can add it, or maybe it does? 
            // I'll check Tables.kt first. Wait, let's update updatedAt as lastLoginAt in Users, or I will add lastLoginAt to Users if it wasn't there.
            
            val userId = user[Users.id]
            val token = JWT.create()
                .withIssuer(jwtIssuer)
                .withAudience(jwtAudience)
                .withClaim("userId", userId.toString())
                .withClaim("role", user[Users.role])
                .withExpiresAt(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
                .sign(Algorithm.HMAC256(jwtSecret))

            // Check face enrollment
            val faceEnrolled = FaceTemplates.select { FaceTemplates.userId eq user[Users.id] }.count() > 0

            Users.update({ Users.id eq user[Users.id] }) {
                it[lastLoginAt] = Instant.now()
            }

            AuthTokenResponse(
                accessToken = token,
                refreshToken = token, // Simplified
                role = user[Users.role],
                userId = userId.toString(),
                name = user[Users.name],
                requiresFaceEnrollment = !faceEnrolled
            )
        }
    }

    fun seedDefaultUsers() {
        org.jetbrains.exposed.sql.transactions.transaction {
            val pw = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, "Yogesh@Rtu8279".toCharArray())
            if (com.attendance.server.models.Users.select { com.attendance.server.models.Users.collegeId eq "YogeshRtu" }.count() == 0L) {
                com.attendance.server.models.Users.insert {
                    it[collegeId] = "YogeshRtu"
                    it[name] = "System Administrator"
                    it[passwordHash] = pw
                    it[role] = com.attendance.shared.model.UserRole.ADMIN.name
                }
            }
        }
    }
}
