package com.attendance.server.routes

import com.attendance.server.models.Users
import com.attendance.server.plugins.configureSerialization
import com.attendance.server.services.AuthRequestException
import com.attendance.server.services.AuthService
import com.attendance.shared.dto.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class AuthRegistrationIntegrationTest {
    @Before fun database() {
        Database.connect("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(Users) }
    }

    private fun seed(college: String, userRole: String = "STUDENT", departmentInvite: String? = null) = transaction {
        Users.insert {
            it[collegeId] = college; it[name] = "Original account"; it[passwordHash] = "unchanged-test-hash"
            it[role] = userRole; it[registrationStatus] = "ACTIVE"; it[accountStatus] = "ACTIVE"
            it[deviceId] = "original-device"; it[departmentCode] = departmentInvite
            it[createdAt] = Instant.now(); it[updatedAt] = Instant.now()
        }[Users.id]
    }

    @Test fun shortPasswordsReturnSpecificErrorsForEveryRegistrationRoute() = testApplication {
        application { configureSerialization(); routing { authRoutes() } }
        val requests = listOf(
            "student" to Json.encodeToString(StudentRegisterRequest("S1", "Student", "short", "CSE", "A", 1, "2026-2027", "device")),
            "teacher" to Json.encodeToString(TeacherRegisterRequest("T1", "Teacher", "short", "INVITE")),
            "hod" to Json.encodeToString(HodRegisterRequest("HOD", "H1", "short", "CSE", "INVITE"))
        )
        for ((role, body) in requests) {
            val response = client.post("/api/auth/$role/register") { contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(response.bodyAsText(), HttpStatusCode.BadRequest, response.status)
            val error = Json.decodeFromString<ErrorResponse>(response.bodyAsText())
            assertEquals("PASSWORD_TOO_SHORT", error.error)
            assertTrue(error.message.contains("at least 8 characters"))
        }
        transaction { assertEquals(0L, Users.selectAll().count()) }
    }

    @Test fun malformedPayloadReturnsSafeActionableError() = testApplication {
        application { configureSerialization(); routing { authRoutes() } }
        val response = client.post("/api/auth/student/register") {
            contentType(ContentType.Application.Json); setBody("""{"password":"private-test-input"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = Json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("INVALID_REGISTRATION_DATA", error.error)
        assertTrue(error.message.contains("required registration fields"))
        assertFalse(response.bodyAsText().contains("private-test-input"))
    }

    @Test fun duplicateCollegeIdIsConflictAndCannotOverwriteAccountOrDevice() = testApplication {
        val originalId = seed("EXISTING", "HOD", "INVITE")
        application { configureSerialization(); routing { authRoutes() } }
        val requests = listOf(
            "student" to Json.encodeToString(StudentRegisterRequest(" EXISTING ", "Replacement", "password123", "CSE", "A", 1, "2026-2027", "other-device")),
            "teacher" to Json.encodeToString(TeacherRegisterRequest("EXISTING", "Replacement", "password123", "INVITE")),
            "hod" to Json.encodeToString(HodRegisterRequest("Replacement", "EXISTING", "password123", "CSE", "OTHER"))
        )
        for ((role, body) in requests) {
            val response = client.post("/api/auth/$role/register") { contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(response.bodyAsText(), HttpStatusCode.Conflict, response.status)
            assertEquals("ACCOUNT_EXISTS", Json.decodeFromString<ErrorResponse>(response.bodyAsText()).error)
        }
        transaction {
            val row = Users.selectAll().single()
            assertEquals(originalId, row[Users.id]); assertEquals("Original account", row[Users.name])
            assertEquals("HOD", row[Users.role]); assertEquals("original-device", row[Users.deviceId])
            assertEquals("unchanged-test-hash", row[Users.passwordHash])
        }
    }

    @Test fun staffRegistrationRetainsApprovalRequirementAndReturnsNoAccessToken() {
        val hod = AuthService.registerHod(HodRegisterRequest(" Head ", " H1 ", "password123", " CSE ", " INVITE "))
        val teacher = AuthService.registerTeacher(TeacherRegisterRequest(" T1 ", " Teacher ", "password123", " INVITE "))
        for (response in listOf(hod, teacher)) {
            assertEquals("", response.accessToken); assertEquals("", response.refreshToken)
            val row = transaction { Users.selectAll().where { Users.id eq UUID.fromString(response.userId) }.single() }
            assertEquals("PENDING", row[Users.accountStatus])
            assertEquals("INVITE", row[Users.departmentCode])
        }
    }

    @Test fun invalidDepartmentCodeCreatesNoTeacherAccount() {
        val error = assertThrows(AuthRequestException::class.java) {
            AuthService.registerTeacher(TeacherRegisterRequest("T1", "Teacher", "password123", "MISSING"))
        }
        assertEquals("INVALID_DEPARTMENT_CODE", error.code)
        assertTrue(error.message!!.contains("Ask your HOD"))
        transaction { assertEquals(0L, Users.selectAll().count()) }
    }

    @Test fun unexpectedFailuresRemain500AndDoNotExposeInternalDetails() {
        val (status, body) = registrationFailure(IllegalStateException("SELECT secret FROM private_table"))
        assertEquals(HttpStatusCode.InternalServerError, status)
        assertEquals("REGISTRATION_UNAVAILABLE", body.error)
        assertFalse(body.message.contains("private_table"))
        assertEquals("Invalid request", publicError(IllegalArgumentException("private internal detail")))
        assertThrows(CancellationException::class.java) { registrationFailure(CancellationException("cancelled")) }
    }
}
