package com.attendance.server.routes

import com.attendance.server.services.AttendanceService
import com.attendance.server.services.SyncRecordRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.http.ContentType


fun Route.attendanceRoutes(attendanceService: AttendanceService) {
    route("/api/attendance") {
        authenticate {
            post("/start") {
                try {
                    val request = call.receive<StartSessionRequest>()
                    val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                    val teacherId = principal!!.payload.getClaim("userId").asString()
                    val sessionId = attendanceService.startSession(
                        request.classId, 
                        teacherId, 
                        request.checkIntervalMinutes, 
                        request.minSuccessfulChecks
                    )
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(sessionId = sessionId.toString()))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            
            post("/stop") {
                try {
                    val request = call.receive<Map<String, String>>()
                    val classId = request["classId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    attendanceService.stopSession(classId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }

            post("/session/{sessionId}/stop") {
                try {
                    val sessionId = call.parameters["sessionId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    attendanceService.stopSession(java.util.UUID.fromString(sessionId))
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }

            post("/sync") {
                try {
                    val records = call.receive<List<SyncRecordRequest>>()
                    val responses = attendanceService.syncRecords(records)
                    call.respond(HttpStatusCode.OK, responses)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }

            get("/class/{classId}/student/today") {
                try {
                    val classId = call.parameters["classId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                    val studentId = principal?.payload?.getClaim("userId")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    
                    val status = attendanceService.getStudentTodayStatus(java.util.UUID.fromString(classId), java.util.UUID.fromString(studentId))
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(status = status))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            
            get("/live/{classId}") {
                try {
                    val classId = call.parameters["classId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val response = attendanceService.getLiveSession(classId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            
            post("/override") {
                try {
                    val request = call.receive<OverrideRequest>()
                    attendanceService.overrideStatus(request.classId, request.studentId, request.status, request.reason)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            
            get("/reviews/{classId}") {
                try {
                    val classId = call.parameters["classId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val response = attendanceService.getReviews(classId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
        }
    }
    route("/api/reports") {
        authenticate {
            get("/class/{classId}/student/{studentId}/csv") {
                try {
                    val classId = call.parameters["classId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val studentId = call.parameters["studentId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val month = call.request.queryParameters["month"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val csvString = attendanceService.getStudentMonthReportCsv(java.util.UUID.fromString(classId), java.util.UUID.fromString(studentId), month)
                    call.respondText(csvString, ContentType.Text.CSV)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            get("/class/{classId}/csv") {
                try {
                    val classId = call.parameters["classId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val month = call.request.queryParameters["month"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val csvString = attendanceService.getClassMonthReportCsv(java.util.UUID.fromString(classId), month)
                    call.respondText(csvString, ContentType.Text.CSV)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
            get("/student/summary") {
                try {
                    val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                    val studentId = principal?.payload?.getClaim("userId")?.asString() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    val month = java.time.YearMonth.now().toString()
                    val summary = attendanceService.getStudentSummary(java.util.UUID.fromString(studentId), month)
                    call.respond(HttpStatusCode.OK, summary)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class StartSessionRequest(
    val classId: String,
    val checkIntervalMinutes: Int,
    val minSuccessfulChecks: Int
)

@kotlinx.serialization.Serializable
data class OverrideRequest(
    val classId: String,
    val studentId: String,
    val status: String,
    val reason: String
)





