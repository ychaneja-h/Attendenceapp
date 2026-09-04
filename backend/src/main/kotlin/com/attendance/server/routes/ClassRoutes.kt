package com.attendance.server.routes

import com.attendance.server.services.ClassService
import com.attendance.shared.dto.CreateClassRequest
import com.attendance.shared.dto.JoinClassRequest
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.util.UUID

fun Route.classRoutes() {
    authenticate {
        route("/api/classes") {
            
            // Teacher routes
            post {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val request = call.receive<CreateClassRequest>()
                    
                    val response = ClassService.createClass(teacherId, request)
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to create class")))
                }
            }
            
            get("/teacher") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val classes = ClassService.getTeacherClasses(teacherId)
                    call.respond(HttpStatusCode.OK, classes)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to fetch classes")))
                }
            }

            // Student routes
            post("/join") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val request = call.receive<JoinClassRequest>()
                    
                    val response = ClassService.joinClass(studentId, request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to join class")))
                }
            }
            
            get("/student") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val classes = ClassService.getStudentClasses(studentId)
                    call.respond(HttpStatusCode.OK, classes)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to fetch classes")))
                }
            }

            get("/{id}/pending") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val requests = ClassService.getPendingRequests(classId)
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to fetch pending requests")))
                }
            }

            get("/{id}/roster") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val requests = ClassService.getAllRequests(classId)
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to fetch roster")))
                }
            }

            post("/approve/{reqId}") {
                try {
                    val reqId = UUID.fromString(call.parameters["reqId"])
                    ClassService.approveRequest(reqId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to approve request")))
                }
            }

            post("/reject/{reqId}") {
                try {
                    val reqId = UUID.fromString(call.parameters["reqId"])
                    ClassService.rejectRequest(reqId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to reject request")))
                }
            }
            
            post("/{id}/students/{studentId}/remove") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val studentId = UUID.fromString(call.parameters["studentId"])
                    ClassService.removeStudent(classId, studentId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to remove student")))
                }
            }
            
            delete("/{id}") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    ClassService.deleteClass(classId)
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (e.message ?: "Failed to delete class")))
                }
            }
            
            get("/{id}") {
                call.respond(HttpStatusCode.NotImplemented, "Get single class details - Phase 3")
            }
        }
    }
}

