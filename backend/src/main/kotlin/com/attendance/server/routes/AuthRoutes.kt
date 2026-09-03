package com.attendance.server.routes

import com.attendance.server.services.AuthService
import com.attendance.shared.dto.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Route.authRoutes() {
    route("/api/auth") {
        post("/student/register") {
            try {
                val request = call.receive<StudentRegisterRequest>()
                val response = AuthService.registerStudent(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }

        post("/teacher/register") {
            try {
                val request = call.receive<TeacherRegisterRequest>()
                val response = AuthService.registerTeacher(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }

        post("/hod/register") {
            try {
                val request = call.receive<HodRegisterRequest>()
                val response = AuthService.registerHod(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }

        post("/verify-staff-code") {
            try {
                val request = call.receive<com.attendance.shared.dto.VerifyStaffCodeRequest>()
                val passcode = request.passcode
                val officialCode = System.getenv("STAFF_MASTER_CODE") ?: "Yogesh@Rtu@admin"
                
                if (passcode == officialCode) {
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid passcode"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request"))
            }
        }

        post("/login") {
            handleLogin(call)
        }
                post("/reset-password") {
            try {
                val request = call.receive<ResetPasswordRequest>()
                AuthService.resetPassword(request)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password reset successful"))
            } catch (e: SecurityException) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to (e.message ?: "Face verification failed")))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Reset failed")))
            }
        }

        post("/student/login") {
            handleLogin(call)
        }
        post("/teacher/login") {
            handleLogin(call)
        }
        post("/hod/login") {
            handleLogin(call)
        }
        post("/admin/login") {
            handleLogin(call)
        }
    }
}

private suspend fun handleLogin(call: ApplicationCall) {
    try {
        val request = call.receive<LoginRequest>()
        val response = AuthService.initiateLogin(request)
        call.respond(HttpStatusCode.OK, response)
    } catch (e: Exception) {
        if (e.message == "DEVICE_MISMATCH") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "DEVICE_MISMATCH"))
        } else if (e.message == "PENDING_APPROVAL") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "PENDING_APPROVAL"))
        } else {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Login failed")))
        }
    }
}



