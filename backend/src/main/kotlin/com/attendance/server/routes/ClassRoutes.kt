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
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }
            
            get("/teacher") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val teacherId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val classes = ClassService.getTeacherClasses(teacherId)
                    call.respond(HttpStatusCode.OK, classes)
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
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
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }
            
            get("/student") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val classes = ClassService.getStudentClasses(studentId)
                    call.respond(HttpStatusCode.OK, classes)
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }

            get("/{id}/pending") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val requests = ClassService.getPendingRequests(classId, call.actorId())
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }

            get("/{id}/roster") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val requests = ClassService.getAllRequests(classId, call.actorId())
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }

            post("/approve/{reqId}") {
                try {
                    val reqId = UUID.fromString(call.parameters["reqId"])
                    ClassService.approveRequest(reqId, call.actorId())
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }

            post("/reject/{reqId}") {
                try {
                    val reqId = UUID.fromString(call.parameters["reqId"])
                    ClassService.rejectRequest(reqId, call.actorId())
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }
            
            post("/{id}/students/{studentId}/remove") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    val studentId = UUID.fromString(call.parameters["studentId"])
                    ClassService.removeStudent(classId, studentId, call.actorId())
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }
            
            delete("/{id}") {
                try {
                    val classId = UUID.fromString(call.parameters["id"])
                    ClassService.deleteClass(classId, call.actorId())
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } catch (e: Exception) {
                    if (e is SecurityException) throw e
                    call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                }
            }
            
            get("/{id}") {
                call.respond(HttpStatusCode.NotImplemented, "Get single class details - Phase 3")
            }
        }
    }
}

