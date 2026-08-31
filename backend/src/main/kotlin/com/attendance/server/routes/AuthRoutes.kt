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
                val sessionToken = AuthService.registerStudent(request)
                call.respond(HttpStatusCode.Created, mapOf("sessionToken" to sessionToken))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }

        post("/teacher/register") {
            try {
                val request = call.receive<TeacherRegisterRequest>()
                val sessionToken = AuthService.registerTeacher(request)
                call.respond(HttpStatusCode.Created, mapOf("sessionToken" to sessionToken))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Registration failed")))
            }
        }
        
        post("/login") {
            handleLogin(call)
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
        
        post("/verify-otp") {
            handleVerifyOtp(call)
        }
        post("/student/verify-otp") {
            handleVerifyOtp(call)
        }
        post("/teacher/verify-otp") {
            handleVerifyOtp(call)
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
        } else {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "Login failed")))
        }
    }
}

private suspend fun handleVerifyOtp(call: ApplicationCall) {
    try {
        val request = call.receive<VerifyOtpRequest>()
        val response = AuthService.verifyOtpAndLogin(request)
        call.respond(HttpStatusCode.OK, response)
    } catch (e: Exception) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (e.message ?: "OTP Verification failed")))
    }
}
