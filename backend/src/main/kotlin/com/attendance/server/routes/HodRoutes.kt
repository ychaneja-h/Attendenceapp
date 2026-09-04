package com.attendance.server.routes

import com.attendance.server.services.HodService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.hodRoutes() {
    authenticate("auth-jwt") {
        route("/api/hod/teachers") {
            get("/pending") {
                val principal = call.principal<JWTPrincipal>()
                if (principal?.payload?.getClaim("role")?.asString() != "HOD") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val hodId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val teachers = HodService.getPendingTeachers(hodId)
                call.respond(HttpStatusCode.OK, teachers)
            }

            get("/active") {
                val principal = call.principal<JWTPrincipal>()
                if (principal?.payload?.getClaim("role")?.asString() != "HOD") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val hodId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val teachers = HodService.getActiveTeachers(hodId)
                call.respond(HttpStatusCode.OK, teachers)
            }

            post("/{id}/approve") {
                val principal = call.principal<JWTPrincipal>()
                if (principal?.payload?.getClaim("role")?.asString() != "HOD") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val hodId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val teacherIdStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                try {
                    val teacherId = UUID.fromString(teacherIdStr)
                    HodService.approveTeacher(hodId, teacherId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "Teacher approved"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to approve")))
                }
            }

            post("/{id}/reject") {
                val principal = call.principal<JWTPrincipal>()
                if (principal?.payload?.getClaim("role")?.asString() != "HOD") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val hodId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val teacherIdStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                try {
                    val teacherId = UUID.fromString(teacherIdStr)
                    HodService.rejectTeacher(hodId, teacherId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "Teacher rejected"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to reject")))
                }
            }

            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                if (principal?.payload?.getClaim("role")?.asString() != "HOD") {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                val hodId = UUID.fromString(principal.payload.getClaim("userId").asString())
                val teacherIdStr = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                try {
                    val teacherId = UUID.fromString(teacherIdStr)
                    HodService.deleteTeacher(hodId, teacherId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "Teacher deleted"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to delete")))
                }
            }
        }
    }
}

