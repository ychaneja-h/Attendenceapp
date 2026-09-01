package com.attendance.server.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.http.content.*
import com.attendance.server.services.UserService
import com.attendance.shared.dto.UpdateProfileRequest
import java.util.UUID

fun Route.userRoutes() {
    val userService = UserService()
    
    authenticate("auth-jwt") {
        route("/api/users") {
            get("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = UUID.fromString(userIdStr)
                    
                    val profile = userService.getProfile(userId)
                    call.respond(HttpStatusCode.OK, profile)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                }
            }
            
            put("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@put call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = UUID.fromString(userIdStr)
                    
                    val req = call.receive<UpdateProfileRequest>()
                    val updatedProfile = userService.updateProfile(userId, req)
                    call.respond(HttpStatusCode.OK, updatedProfile)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                }
            }
            get("/me/face") {
                try {
                    val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@get call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = java.util.UUID.fromString(userIdStr)

                    val user = org.jetbrains.exposed.sql.transactions.transaction {
                        com.attendance.server.models.Users.select { com.attendance.server.models.Users.id eq userId }.singleOrNull()
                    }
                    if (user != null) {
                        val embedding = user[com.attendance.server.models.Users.faceEmbedding]
                        call.respond(io.ktor.http.HttpStatusCode.OK, mapOf("faceEmbedding" to (embedding ?: "")))
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Failed")
                }
            }
            post("/me/photo") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = UUID.fromString(userIdStr)

                    val multipart = call.receiveMultipart()
                    var fileName: String? = null
                    
                    val uploadDir = java.io.File("uploads")
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs()
                    }

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalFileName = part.originalFileName ?: "photo.jpg"
                            val extension = java.io.File(originalFileName).extension.let { if (it.isEmpty()) "jpg" else it }
                            val newFileName = "${UUID.randomUUID()}.$extension"
                            val file = java.io.File(uploadDir, newFileName)
                            
                            part.streamProvider().use { input ->
                                file.outputStream().buffered().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            fileName = newFileName
                        }
                        part.dispose()
                    }

                    if (fileName != null) {
                        val photoUrl = "/uploads/$fileName"
                        val updatedProfile = userService.updatePhoto(userId, photoUrl)
                        call.respond(HttpStatusCode.OK, updatedProfile)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No file uploaded")
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown error")
                }
            }
            
            get("/me/devices") {
                call.respond(HttpStatusCode.NotImplemented, "Get devices - Phase 2")
            }
        }
    }
}


