package com.attendance.server.routes

import com.attendance.server.models.*
import com.attendance.server.plugins.configureSerialization
import com.attendance.server.plugins.configureStatusPages
import com.attendance.server.plugins.attendanceBearerHeader
import com.attendance.server.services.PlanAAttendanceService
import com.attendance.shared.dto.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PlanASyncIntegrationTest {
    private val teacher = UUID.randomUUID()
    private val student = UUID.randomUUID()
    private val classId = UUID.randomUUID()
    private val json = Json { ignoreUnknownKeys = true }
    private val service = PlanAAttendanceService()

    @Before fun database() {
        Database.connect("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(Users, Departments, AcademicYears, Classes, ClassMembers, PlanAEvents, PlanATimeAnchors)
            listOf(teacher to "TEACHER", student to "STUDENT").forEach { (userId, userRole) ->
                Users.insert {
                    it[id] = userId; it[collegeId] = userRole; it[name] = userRole
                    it[passwordHash] = "unused-test-hash"; it[role] = userRole
                    it[registrationStatus] = "ACTIVE"; it[createdAt] = Instant.now(); it[updatedAt] = Instant.now()
                }
            }
            val department = Departments.insert {
                it[name] = "Test department"; it[code] = "TEST"; it[createdAt] = Instant.now()
            }[Departments.id]
            val year = AcademicYears.insert {
                it[label] = "2026-27"; it[startDate] = Instant.now(); it[endDate] = Instant.now()
            }[AcademicYears.id]
            Classes.insert {
                it[id] = classId; it[teacherId] = teacher; it[name] = "Test class"; it[section] = "A"; it[semester] = 1
                it[departmentId] = department; it[academicYearId] = year; it[classCode] = "TEST123"
                it[bleClassId] = "12345678"; it[bleClassKey] = ByteArray(32); it[createdAt] = Instant.now()
            }
            ClassMembers.insert {
                it[this.classId] = this@PlanASyncIntegrationTest.classId; it[studentId] = student
                it[status] = "ACTIVE"; it[joinedAt] = Instant.now()
            }
        }
    }

    private fun start() = PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.SESSION_START,
        classId.toString(), teacher.toString(), PlanATimeEvidence(System.currentTimeMillis(), 1000, "test-boot"),
        sessionId = UUID.randomUUID().toString(), roster = listOf(PlanARosterStudent(student.toString(), "Student", "STUDENT")))

    @Test fun emptyHistorySyncReturnsJsonForTeacherAndStudent() = testApplication {
        application { testRoutes() }
        for (actor in listOf(teacher, student)) {
            val response = client.post("/api/attendance/plan-a/sync") {
                bearerAuth(token(actor)); contentType(ContentType.Application.Json); setBody("{\"events\":[]}")
            }
            assertEquals(response.bodyAsText(), HttpStatusCode.OK, response.status)
            assertTrue(json.decodeFromString<PlanASyncResponse>(response.bodyAsText()).sessions.isEmpty())
        }
    }

    @Test fun uploadingAndRefreshingSessionRetainsHistory() = testApplication {
        application { testRoutes() }
        val event = start()
        repeat(2) {
            val response = client.post("/api/attendance/plan-a/sync") {
                bearerAuth(token(teacher)); contentType(ContentType.Application.Json)
                setBody(json.encodeToString(PlanASyncRequest(listOf(event))))
            }
            assertEquals(response.bodyAsText(), HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<PlanASyncResponse>(response.bodyAsText())
            assertTrue(result.acknowledgements.single().accepted)
            assertEquals(event.sessionId, result.sessions.single().sessionId)
        }
        assertEquals(1, service.sessions(student.toString(), null).single().students.size)
    }

    @Test fun syncWorksWithTheAndroidDefaultAndExplicitAuthHeader() = testApplication {
        application { testRoutes() }
        val auth = token(teacher)
        val androidClient = client.config { defaultRequest { header(HttpHeaders.Authorization, "Bearer $auth") } }
        val response = androidClient.post("/api/attendance/plan-a/sync") {
            header(HttpHeaders.Authorization, "Bearer $auth")
            contentType(ContentType.Application.Json)
            setBody("{\"events\":[]}")
        }
        assertEquals(response.bodyAsText(), HttpStatusCode.OK, response.status)
    }

    @Test fun timeAnchorCanBeConfirmedAndUsedForHistory() {
        val anchor = service.createTimeAnchor(teacher.toString(), PlanATimeAnchorRequest("test-boot", 1000))
        service.confirmTimeAnchor(teacher.toString(), PlanATimeAnchorConfirmRequest(anchor.anchorId, 25))
        val result = service.sync(teacher.toString(), PlanASyncRequest(listOf(start().copy(
            time = PlanATimeEvidence(anchor.serverTimeMs, 1000, "test-boot", anchor.anchorId)))))
        assertTrue(result.acknowledgements.single().accepted)
        assertEquals(1, result.sessions.size)
    }

    @Test fun differentAuthorizationTokensAreRejected() = testApplication {
        application { testRoutes() }
        val response = client.post("/api/attendance/plan-a/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(teacher)}")
            header(HttpHeaders.Authorization, "Bearer ${token(student)}")
            contentType(ContentType.Application.Json)
            setBody("{\"events\":[]}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test fun anotherActorCannotUploadTeacherEvidence() {
        val result = service.sync(student.toString(), PlanASyncRequest(listOf(start())))
        assertFalse(result.acknowledgements.single().accepted)
        assertTrue(result.sessions.isEmpty())
    }

    private fun Application.testRoutes() {
        configureSerialization()
        configureStatusPages()
        install(Authentication) { jwt {
            authHeader { call -> attendanceBearerHeader(call.request.headers.getAll(HttpHeaders.Authorization)) }
            verifier(JWT.require(Algorithm.HMAC256(TEST_SECRET)).build())
            validate { JWTPrincipal(it.payload) }
        } }
        routing { planAAttendanceRoutes() }
    }
    private fun token(actor: UUID): String = JWT.create().withClaim("userId", actor.toString()).sign(Algorithm.HMAC256(TEST_SECRET))
    companion object { private const val TEST_SECRET = "isolated-history-regression-tests-only" }
}
