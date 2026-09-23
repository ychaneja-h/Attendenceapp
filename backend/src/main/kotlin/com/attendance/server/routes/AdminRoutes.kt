package com.attendance.server.routes

import com.attendance.server.services.AdminService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.adminRoutes() {
    authenticate("auth-jwt") {
        route("/api/admin") {
            // Role verification interceptor
            intercept(ApplicationCallPipeline.Call) {
                val principal = call.principal<JWTPrincipal>()
                val role = principal?.payload?.getClaim("role")?.asString()
                if (role != "ADMIN") {
                    call.respond(HttpStatusCode.Forbidden, com.attendance.shared.dto.GenericResponse(error = "Admin access required"))
                    finish()
                }
            }

            route("/hods") {
                get("/pending") {
                    try {
                        val hods = AdminService.getPendingHods()
                        call.respond(HttpStatusCode.OK, hods)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                    }
                }

                get("/active") {
                    try {
                        val hods = AdminService.getActiveHods()
                        call.respond(HttpStatusCode.OK, hods)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                    }
                }
                
                post("/{id}/approve") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.approveHod(id)
                        call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "HOD approved successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                    }
                }

                post("/{id}/reject") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.rejectHod(id)
                        call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "HOD rejected successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                    }
                }
                
                delete("/{id}") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.deleteHod(id)
                        call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(message = "HOD deleted successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, com.attendance.shared.dto.GenericResponse(error = (publicError(e))))
                    }
                }
            }
        }
    }
}

