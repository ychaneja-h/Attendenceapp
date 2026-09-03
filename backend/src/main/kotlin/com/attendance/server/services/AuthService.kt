package com.attendance.server.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.attendance.server.models.*
import com.attendance.shared.dto.*
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

    private fun generateToken(userId: UUID, role: String): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("userId", userId.toString())
            .withClaim("role", role)
            .withExpiresAt(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    fun registerStudent(request: StudentRegisterRequest): AuthTokenResponse {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                // Return token for existing pending user
                val uid = existingUser[Users.id]
                val token = generateToken(uid, existingUser[Users.role])
                return@transaction AuthTokenResponse(
                    accessToken = token,
                    refreshToken = token,
                    role = existingUser[Users.role],
                    userId = uid.toString(),
                    name = existingUser[Users.name],
                    requiresFaceEnrollment = true
                )
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[passwordHash] = hashedPassword
                it[deviceId] = request.deviceFingerprint
                it[role] = UserRole.STUDENT.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name
                it[accountStatus] = "ACTIVE"
                it[department] = request.department
                it[section] = request.section
                it[semester] = request.semester
                it[academicYear] = request.academicYear
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[Users.id]

            val token = generateToken(userId, UserRole.STUDENT.name)
            AuthTokenResponse(
                accessToken = token,
                refreshToken = token,
                role = UserRole.STUDENT.name,
                userId = userId.toString(),
                name = request.name,
                requiresFaceEnrollment = true
            )
        }
    }

    fun registerHod(request: HodRegisterRequest): AuthTokenResponse {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                val uid = existingUser[Users.id]
                return@transaction AuthTokenResponse(
                    accessToken = "",
                    refreshToken = "",
                    role = UserRole.HOD.name,
                    userId = uid.toString(),
                    name = existingUser[Users.name],
                    requiresFaceEnrollment = false
                )
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

            AuthTokenResponse(
                accessToken = "",
                refreshToken = "",
                role = UserRole.HOD.name,
                userId = userId.toString(),
                name = request.name,
                requiresFaceEnrollment = false
            )
        }
    }

    fun registerTeacher(request: TeacherRegisterRequest): AuthTokenResponse {
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this College ID already exists")
                }
                val uid = existingUser[Users.id]
                return@transaction AuthTokenResponse(
                    accessToken = "",
                    refreshToken = "",
                    role = UserRole.TEACHER.name,
                    userId = uid.toString(),
                    name = existingUser[Users.name],
                    requiresFaceEnrollment = false
                )
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

            AuthTokenResponse(
                accessToken = "",
                refreshToken = "",
                role = UserRole.TEACHER.name,
                userId = userId.toString(),
                name = request.name,
                requiresFaceEnrollment = false
            )
        }
    }

        private fun cosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        return (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))).toFloat()
    }

    fun resetPassword(request: com.attendance.shared.dto.ResetPasswordRequest) {
        transaction {
            val user = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
                ?: throw IllegalArgumentException("User not found")
            
            val storedEmbeddingStr = user[Users.faceEmbedding]
            if (storedEmbeddingStr.isNullOrEmpty()) {
                throw SecurityException("No registered face found. Cannot reset password.")
            }
            
                        val storedEmbedding = try {
                kotlinx.serialization.json.Json.decodeFromString<List<Float>>(storedEmbeddingStr)
            } catch (e: Exception) {
                throw SecurityException("Invalid stored face profile")
            }
            
            val similarity = cosineSimilarity(request.faceEmbedding, storedEmbedding)
            if (similarity < 0.73f) {
                throw SecurityException("Face does not match registered profile")
            }
            
            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.newPassword.toCharArray())
            Users.update({ Users.id eq user[Users.id] }) {
                it[passwordHash] = hashedPassword
            }
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

            val userId = user[Users.id]
            val token = generateToken(userId, user[Users.role])

            val faceEnrolled = !user[Users.faceEmbedding].isNullOrEmpty()

            Users.update({ Users.id eq userId }) {
                it[lastLoginAt] = Instant.now()
            }

            AuthTokenResponse(
                accessToken = token,
                refreshToken = token,
                role = user[Users.role],
                userId = userId.toString(),
                name = user[Users.name],
                requiresFaceEnrollment = !faceEnrolled
            )
        }
    }

    fun seedDefaultUsers() {
        transaction {
            val pw = BCrypt.withDefaults().hashToString(12, "Yogesh@Rtu8279".toCharArray())
            if (Users.select { Users.collegeId eq "YogeshRtu" }.count() == 0L) {
                Users.insert {
                    it[collegeId] = "YogeshRtu"
                    it[name] = "System Administrator"
                    it[passwordHash] = pw
                    it[role] = UserRole.ADMIN.name
                    it[registrationStatus] = RegistrationStatus.ACTIVE.name
                    it[accountStatus] = "ACTIVE"
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }
        }
    }
}




