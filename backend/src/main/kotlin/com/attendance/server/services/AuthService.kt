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
            // Check if college ID or phone already exists
            val existingUser = Users.select { Users.phone eq request.phone }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this Phone already exists")
                }
                // If existing user is unverified, regenerate OTP for the phone they just provided
                return@transaction generateAndSendOtp(existingUser[Users.id], request.phone, OtpPurpose.REGISTRATION)
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[phone] = request.phone
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

            // Generate OTP for phone verification
            generateAndSendOtp(userId, request.phone, OtpPurpose.REGISTRATION)
        }
    }

    fun registerTeacher(request: TeacherRegisterRequest): String {
        return transaction {
            val existingUser = Users.select { Users.phone eq request.phone }.firstOrNull()
            if (existingUser != null) {
                if (existingUser[Users.registrationStatus] == RegistrationStatus.ACTIVE.name) {
                    throw IllegalArgumentException("Account with this Phone already exists")
                }
                return@transaction generateAndSendOtp(existingUser[Users.id], request.phone, OtpPurpose.REGISTRATION)
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

            val userId = Users.insert {
                it[collegeId] = request.collegeId
                it[name] = request.name
                it[phone] = request.phone
                it[passwordHash] = hashedPassword
                it[role] = UserRole.TEACHER.name
                it[registrationStatus] = RegistrationStatus.ACTIVE.name
                it[department] = request.department
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[Users.id]

            generateAndSendOtp(userId, request.phone, OtpPurpose.REGISTRATION)
        }
    }

    fun seedDefaultUsers() {
        transaction {
            val pw = BCrypt.withDefaults().hashToString(12, "123456".toCharArray())
            
            // Admin
            if (Users.select { Users.collegeId eq "admin" }.count() == 0L) {
                Users.insert {
                    it[collegeId] = "admin"
                    it[name] = "College Administrator"
                    it[phone] = "9999999999"
                    it[passwordHash] = pw
                    it[role] = UserRole.ADMIN.name
                    it[registrationStatus] = RegistrationStatus.ACTIVE.name
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }

            // HOD
            if (Users.select { Users.collegeId eq "hod_cs" }.count() == 0L) {
                Users.insert {
                    it[collegeId] = "hod_cs"
                    it[name] = "HOD Computer Science"
                    it[phone] = "9999999998"
                    it[passwordHash] = pw
                    it[role] = UserRole.HOD.name
                    it[department] = "CS"
                    it[registrationStatus] = RegistrationStatus.ACTIVE.name
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }

            // Teacher
            if (Users.select { Users.collegeId eq "teacher1" }.count() == 0L) {
                Users.insert {
                    it[collegeId] = "teacher1"
                    it[name] = "Prof. Alan Turing"
                    it[phone] = "9999999997"
                    it[passwordHash] = pw
                    it[role] = UserRole.TEACHER.name
                    it[department] = "CS"
                    it[registrationStatus] = RegistrationStatus.ACTIVE.name
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }

            // Student
            if (Users.select { Users.collegeId eq "student1" }.count() == 0L) {
                Users.insert {
                    it[collegeId] = "student1"
                    it[name] = "John Doe"
                    it[phone] = "9999999996"
                    it[passwordHash] = pw
                    it[role] = UserRole.STUDENT.name
                    it[department] = "CS"
                    it[section] = "A"
                    it[semester] = 5
                    it[academicYear] = "2026"
                    it[registrationStatus] = RegistrationStatus.ACTIVE.name
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }

            println("Seeded default users: admin, hod_cs, teacher1, student1 (password: 123456) if missing")
        }
    }

    fun initiateLogin(request: LoginRequest): LoginStepOneResponse {
        return transaction {
            val user = Users.select { Users.collegeId eq request.collegeId }.firstOrNull()
                ?: throw IllegalArgumentException("Invalid credentials")
            
            val result = BCrypt.verifyer().verify(request.password.toCharArray(), user[Users.passwordHash])
            if (!result.verified) {
                throw IllegalArgumentException("Invalid credentials")
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

            val sessionToken = generateAndSendOtp(user[Users.id], user[Users.phone], OtpPurpose.LOGIN)
            
            val maskedPhone = user[Users.phone].let { "******" + it.takeLast(4) }
            LoginStepOneResponse(otpSent = true, maskedPhone = maskedPhone, sessionToken = sessionToken)
        }
    }

    fun verifyOtpAndLogin(request: VerifyOtpRequest): AuthTokenResponse {
        return transaction {
            // Find valid OTP record for that phone
            val otpRecord = OtpRecords.select { 
                (OtpRecords.phone eq request.phone) and 
                (OtpRecords.verified eq false) and 
                (OtpRecords.expiresAt greater Instant.now())
            }.orderBy(OtpRecords.createdAt to SortOrder.DESC).firstOrNull()
                ?: throw IllegalArgumentException("Invalid or expired OTP")

            // Check OTP (also allow 123456 as universal dev bypass)
            if (otpRecord[OtpRecords.otpHash] != request.otp && request.otp != "123456") {
                OtpRecords.update({ OtpRecords.id eq otpRecord[OtpRecords.id] }) {
                    it[attempts] = otpRecord[OtpRecords.attempts] + 1
                }
                throw IllegalArgumentException("Invalid OTP")
            }

            // Mark OTP as verified
            OtpRecords.update({ OtpRecords.id eq otpRecord[OtpRecords.id] }) {
                it[verified] = true
            }

            val userIdFromOtp = otpRecord[OtpRecords.userId]
            if (userIdFromOtp == null) throw IllegalArgumentException("Invalid user for OTP")

            val user = Users.select { Users.id eq userIdFromOtp }.single()
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

    private fun generateAndSendOtp(userId: UUID?, phone: String, purpose: OtpPurpose): String {
        // Generate a 6-digit OTP
        val otp = (100000..999999).random().toString()
        val sessionToken = UUID.randomUUID().toString()

        OtpRecords.insert {
            it[this.userId] = userId
            it[this.phone] = phone
            it[this.otpHash] = otp // Storing plain text for simplicity in this demo!
            it[this.purpose] = purpose.name
            it[this.expiresAt] = Instant.now().plus(10, ChronoUnit.MINUTES)
            it[this.createdAt] = Instant.now()
        }

        // TODO: Send SMS via provider (Twilio, AWS SNS, etc.)
        println("DEV OTP for $phone is: $otp")

        return sessionToken
    }
}
