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
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
            }
        }

        post("/teacher/register") {
            try {
                val request = call.receive<TeacherRegisterRequest>()
                val response = AuthService.registerTeacher(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
            }
        }

        post("/hod/register") {
            try {
                val request = call.receive<HodRegisterRequest>()
                val response = AuthService.registerHod(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
            }
        }

        post("/verify-staff-code") {
            try {
                val request = call.receive<com.attendance.shared.dto.VerifyStaffCodeRequest>()
                val passcode = request.passcode
                val officialCode = System.getenv("STAFF_MASTER_CODE") ?: "Yogesh@Rtu@admin"
                
                if (passcode == officialCode) {
                    call.respond(HttpStatusCode.OK, com.attendance.shared.dto.GenericResponse(success = true))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, com.attendance.server.plugins.ErrorResponse("ERROR", "Failed"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", "Failed"))
            }
        }

        post("/login") {
            handleLogin(call)
        }
                        post("/request-device-transfer") {
            try {
                val req = call.receive<com.attendance.shared.dto.DeviceTransferAuthReq>()
                val result = AuthService.requestDeviceTransferAuth(req)
                call.respond(HttpStatusCode.OK, result)
            } catch (e: SecurityException) {
                call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
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
            call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("ERROR", "Failed"))
        } else if (e.message == "PENDING_APPROVAL") {
            call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("ERROR", "Failed"))
        } else {
            call.respond(HttpStatusCode.Unauthorized, com.attendance.server.plugins.ErrorResponse("ERROR", e.message ?: "Failed"))
        }
    }
}








