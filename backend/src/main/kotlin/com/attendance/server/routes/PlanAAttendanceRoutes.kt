package com.attendance.server.routes

import com.attendance.server.services.PlanAAttendanceService
import com.attendance.shared.dto.PlanASyncRequest
import com.attendance.shared.dto.PlanATimeAnchorRequest
import com.attendance.shared.dto.PlanATimeAnchorConfirmRequest
import com.attendance.shared.dto.GenericResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.planAAttendanceRoutes() {
    val service = PlanAAttendanceService()
    authenticate {
        route("/api/attendance/plan-a") {
            post("/sync") {
                val actor = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                try { call.respond(service.sync(actor, call.receive<PlanASyncRequest>())) }
                catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (publicError(e)))) }
            }
            get("/sessions") {
                val actor = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                try { call.respond(service.sessions(actor, call.request.queryParameters["classId"])) }
                catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (publicError(e)))) }
            }
            post("/time-anchor") {
                val actor = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                try { call.respond(service.createTimeAnchor(actor, call.receive<PlanATimeAnchorRequest>())) }
                catch (e: IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (publicError(e)))) }
            }
            post("/time-anchor/confirm") {
                val actor = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                try {
                    service.confirmTimeAnchor(actor, call.receive<PlanATimeAnchorConfirmRequest>())
                    call.respond(GenericResponse(success = true))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (publicError(e))))
                }
            }
        }
    }
}
