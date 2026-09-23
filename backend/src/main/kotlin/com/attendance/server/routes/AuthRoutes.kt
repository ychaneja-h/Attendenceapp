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
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", publicError(e)))
            }
        }

        post("/teacher/register") {
            try {
                val request = call.receive<TeacherRegisterRequest>()
                val response = AuthService.registerTeacher(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", publicError(e)))
            }
        }

        post("/hod/register") {
            try {
                val request = call.receive<HodRegisterRequest>()
                val response = AuthService.registerHod(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", publicError(e)))
            }
        }

        post("/verify-staff-code") {
            try {
                val request = call.receive<com.attendance.shared.dto.VerifyStaffCodeRequest>()
                val passcode = request.passcode
                val officialCode = System.getenv("STAFF_MASTER_CODE")?.takeIf { it.length >= 16 }
                    ?: return@post call.respond(HttpStatusCode.ServiceUnavailable,
                        com.attendance.server.plugins.ErrorResponse("UNAVAILABLE", "Staff registration code is not configured. Contact the administrator."))
                
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
                call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("ERROR", publicError(e)))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("ERROR", publicError(e)))
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
        val msg = publicError(e)
        if (msg == "DEVICE_MISMATCH") {
            call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("DEVICE_MISMATCH", msg))
        } else if (msg == "PENDING_APPROVAL") {
            call.respond(HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("PENDING_APPROVAL", msg))
        } else {
            call.respond(HttpStatusCode.Unauthorized, com.attendance.server.plugins.ErrorResponse("ERROR", msg))
        }
    }
}









