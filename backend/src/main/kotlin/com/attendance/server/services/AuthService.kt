package com.attendance.server.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.attendance.server.config.AuthConfig
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
    private fun generateToken(userId: UUID, role: String, authVersion: Int = 0): String = JWT.create()
        .withIssuer(AuthConfig.issuer)
        .withAudience(AuthConfig.audience)
        .withClaim("userId", userId.toString())
        .withClaim("role", role)
        .withClaim("authVersion", authVersion)
        .withIssuedAt(Date.from(Instant.now()))
        .withExpiresAt(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
        .sign(Algorithm.HMAC256(AuthConfig.secret))

    internal fun validateNewPassword(password: String) {
        require(password.length >= 8 && password.toByteArray(Charsets.UTF_8).size <= 72) {
            "Password must contain at least 8 characters and at most 72 UTF-8 bytes"
        }
    }

    internal fun verifyCurrentPassword(password: String, passwordHash: String) {
        if (password.isEmpty() || password.toByteArray(Charsets.UTF_8).size > 72 ||
            !BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified) {
            throw SecurityException("Current password is incorrect")
        }
    }

    private fun validateRegistration(collegeId: String, name: String, password: String) {
        require(collegeId.isNotBlank() && collegeId.length <= 50) { "Invalid college ID" }
        require(name.isNotBlank() && name.length <= 200) { "Invalid name" }
        validateNewPassword(password)
    }

    fun registerStudent(request: StudentRegisterRequest): AuthTokenResponse {
        validateRegistration(request.collegeId, request.name, request.password)
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            require(existingUser == null) { "Account with this College ID already exists" }

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
        validateRegistration(request.collegeId, request.name, request.password)
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            require(existingUser == null) { "Account with this College ID already exists" }

            if (Users.select { (Users.departmentCode eq request.departmentCode) and (Users.role eq UserRole.HOD.name) }.count() > 0L) {
                throw IllegalArgumentException("Department Code already in use by another HOD")
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
        validateRegistration(request.collegeId, request.name, request.password)
        return transaction {
            val existingUser = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
            require(existingUser == null) { "Account with this College ID already exists" }

            if (Users.select { (Users.departmentCode eq request.departmentCode) and (Users.role eq UserRole.HOD.name) }.count() == 0L) {
                throw IllegalArgumentException("Invalid Department Code")
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

    fun requestDeviceTransferAuth(request: DeviceTransferAuthReq): com.attendance.shared.dto.GenericResponse {
        require(request.password.toByteArray().size in 1..72) { "Invalid credentials" }
        return transaction {
            val users = Users.select { Users.collegeId eq request.collegeId }.toList()
            if (users.isEmpty()) throw IllegalArgumentException("Invalid credentials")

            var user: org.jetbrains.exposed.sql.ResultRow? = null
            for (u in users) {

                val result = at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(request.password.toCharArray(), u[Users.passwordHash])
                if (result.verified) {

                    user = u
                    break
                } else {

                }
            }
            if (user == null) {
                throw IllegalArgumentException("Invalid credentials")
            }

            val studentId = user[Users.id]
            AccessControl.requireRole(studentId, setOf("STUDENT"))
            require(request.newDeviceId.isNotBlank() && request.newDeviceId.length <= 255) { "Invalid device ID" }

            // Find first enrolled class teacher
            val classId = ClassMembers.select { (ClassMembers.studentId eq studentId) and (ClassMembers.status eq "ACTIVE") }
                .firstOrNull()?.get(ClassMembers.classId)
                ?: throw SecurityException("You must be enrolled in a class to request a device change.")

            val teacherId = Classes.select { Classes.id eq classId }
                .firstOrNull()?.get(Classes.teacherId)
                ?: throw SecurityException("Class has no assigned teacher.")

            val teacherName = Users.select { Users.id eq teacherId }
                .firstOrNull()?.get(Users.name) ?: "Unknown Teacher"

            // Create the request
            ApprovalRequests.insert {
                it[this.studentId] = studentId
                it[requestType] = "DEVICE_CHANGE"
                it[requestedData] = request.newDeviceId
                it[status] = "PENDING"
                it[createdAt] = Instant.now()
            }

            com.attendance.shared.dto.GenericResponse(message = "Device transfer request sent to $teacherName")
        }
    }
    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        validateNewPassword(request.newPassword)
        transaction {
            // Serialize concurrent password changes so the old password cannot authorize a second update.
            val user = Users.select { Users.id eq userId }.forUpdate().singleOrNull()
                ?: throw SecurityException("Account unavailable")
            verifyCurrentPassword(request.currentPassword, user[Users.passwordHash])
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = BCrypt.withDefaults().hashToString(12, request.newPassword.toCharArray())
                it[authVersion] = user[Users.authVersion] + 1
                it[updatedAt] = Instant.now()
            }
        }
    }

    fun initiateLogin(request: LoginRequest): AuthTokenResponse {
        require(request.collegeId.length in 1..50 && request.password.toByteArray().size in 1..72) { "Invalid credentials" }
        require(request.deviceFingerprint.isNotBlank() && request.deviceFingerprint.length <= 255) { "Invalid device ID" }
        return transaction {
            val users = Users.select { Users.collegeId eq request.collegeId }.toList()
            if (users.isEmpty()) throw IllegalArgumentException("Invalid credentials")

            var user: org.jetbrains.exposed.sql.ResultRow? = null
            for (u in users) {

                val result = at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(request.password.toCharArray(), u[Users.passwordHash])
                if (result.verified) {

                    user = u
                    break
                } else {

                }
            }
            if (user == null) {
                throw IllegalArgumentException("Invalid credentials")
            }

            if (user[Users.accountStatus] == "PENDING") {
                throw SecurityException("PENDING_APPROVAL")
            }

            if (user[Users.accountStatus] != "ACTIVE" || user[Users.registrationStatus] != RegistrationStatus.ACTIVE.name) {
                throw SecurityException("Account unavailable")
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
            val token = generateToken(userId, user[Users.role], user[Users.authVersion])

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

    /** Optional first-run provisioning only. Existing credentials are never overwritten on startup. */
    fun seedDefaultUsers() {
        val adminId = System.getenv("BOOTSTRAP_ADMIN_ID") ?: return
        val adminPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD")
            ?: error("BOOTSTRAP_ADMIN_PASSWORD is required when BOOTSTRAP_ADMIN_ID is configured")
        val adminName = System.getenv("BOOTSTRAP_ADMIN_NAME") ?: "System Administrator"
        validateRegistration(adminId, adminName, adminPassword)
        transaction {
            if (Users.select { Users.collegeId eq adminId }.any()) return@transaction
            Users.insert {
                it[collegeId] = adminId
                it[name] = adminName
                it[passwordHash] = BCrypt.withDefaults().hashToString(12, adminPassword.toCharArray())
                it[role] = UserRole.ADMIN.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name
                it[accountStatus] = "ACTIVE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
    }
}
