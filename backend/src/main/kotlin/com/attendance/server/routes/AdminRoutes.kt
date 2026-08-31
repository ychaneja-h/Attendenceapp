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
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    finish()
                }
            }

            route("/hods") {
                get("/pending") {
                    try {
                        val hods = AdminService.getPendingHods()
                        call.respond(HttpStatusCode.OK, hods)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to get pending HODs")))
                    }
                }

                get("/active") {
                    try {
                        val hods = AdminService.getActiveHods()
                        call.respond(HttpStatusCode.OK, hods)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to get active HODs")))
                    }
                }
                
                post("/{id}/approve") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.approveHod(id)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "HOD approved successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to approve HOD")))
                    }
                }

                post("/{id}/reject") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.rejectHod(id)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "HOD rejected successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to reject HOD")))
                    }
                }
                
                delete("/{id}") {
                    try {
                        val id = UUID.fromString(call.parameters["id"])
                        AdminService.deleteHod(id)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "HOD deleted successfully"))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed to delete HOD")))
                    }
                }
            }
        }
    }
}
