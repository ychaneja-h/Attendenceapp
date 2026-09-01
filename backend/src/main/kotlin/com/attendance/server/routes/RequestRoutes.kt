package com.attendance.server.routes

import com.attendance.server.services.RequestService
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DeviceChangeReq(val newDeviceId: String)

@Serializable
data class FaceRegistrationReq(val faceEmbeddingJSON: String)

fun Route.requestRoutes() {
    val requestService = RequestService()

    authenticate("auth-jwt") {
        route("/api/requests") {
            post("/device") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val req = call.receive<DeviceChangeReq>()
                    requestService.createRequest(studentId, "DEVICE_CHANGE", req.newDeviceId)
                    call.respond(HttpStatusCode.Created, mapOf("status" to "success"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }

            post("/face") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val req = call.receive<FaceRegistrationReq>()
                    requestService.createRequest(studentId, "FACE_REGISTRATION", req.faceEmbeddingJSON)
                    call.respond(HttpStatusCode.Created, mapOf("status" to "success"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }

            get("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val studentId = UUID.fromString(principal!!.payload.getClaim("userId").asString())
                    val requests = requestService.getMyRequests(studentId)
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }

            get("/teacher") {
                try {
                    val requests = requestService.getPendingRequests()
                    call.respond(HttpStatusCode.OK, requests)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }

            post("/{id}/approve") {
                try {
                    val id = UUID.fromString(call.parameters["id"])
                    requestService.approveRequest(id)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "approved"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }

            post("/{id}/reject") {
                try {
                    val id = UUID.fromString(call.parameters["id"])
                    requestService.rejectRequest(id)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "rejected"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Failed")))
                }
            }
        }
    }
}

