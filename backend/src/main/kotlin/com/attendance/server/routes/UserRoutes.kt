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
import com.attendance.server.services.AuthService
import com.attendance.shared.dto.UpdateProfileRequest
import java.util.UUID
import org.jetbrains.exposed.sql.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@kotlinx.serialization.Serializable
data class FaceResponse(val faceEmbedding: String)

private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
private const val MAX_PHOTO_DIMENSION = 4096
private const val MAX_PHOTO_PIXELS = 16_000_000L
private const val SAVED_PHOTO_DIMENSION = 1024

private class PhotoUploadRejected(val status: HttpStatusCode, message: String) : IllegalArgumentException(message)

/** Decode and re-encode pixels so filenames, metadata and appended active content are never served. */
private fun normalizeProfilePhoto(bytes: ByteArray): ByteArray {
    fun startsWith(vararg signature: Int): Boolean = bytes.size >= signature.size &&
        signature.indices.all { (bytes[it].toInt() and 0xff) == signature[it] }
    val format = when {
        startsWith(0xff, 0xd8, 0xff) -> "jpeg"
        startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "png"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII)) -> "webp"
        else -> throw PhotoUploadRejected(HttpStatusCode.UnsupportedMediaType, "Upload a JPEG or PNG photo; other file types are not supported.")
    }
    val decoded = MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
        val readers = ImageIO.getImageReaders(input)
        if (!readers.hasNext()) {
            throw PhotoUploadRejected(HttpStatusCode.UnsupportedMediaType, "This photo format cannot be decoded. Convert it to JPEG or PNG and try again.")
        }
        val reader = readers.next()
        try {
            val readerFormat = reader.formatName.lowercase()
            if (readerFormat != format && !(format == "jpeg" && readerFormat == "jpg")) {
                throw PhotoUploadRejected(HttpStatusCode.UnsupportedMediaType, "The photo content does not match a supported image format.")
            }
            reader.setInput(input, true, true)
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (width !in 1..MAX_PHOTO_DIMENSION || height !in 1..MAX_PHOTO_DIMENSION || width.toLong() * height > MAX_PHOTO_PIXELS) {
                throw PhotoUploadRejected(HttpStatusCode.BadRequest, "Photo dimensions must be at most 4096 pixels per side and 16 megapixels.")
            }
            reader.read(0) ?: throw PhotoUploadRejected(HttpStatusCode.BadRequest, "The photo could not be decoded.")
        } catch (error: PhotoUploadRejected) {
            throw error
        } catch (error: Exception) {
            throw PhotoUploadRejected(HttpStatusCode.BadRequest, "The photo is damaged or invalid. Upload a valid JPEG or PNG photo.")
        } finally {
            reader.dispose()
        }
    }
    try {
        val scale = minOf(1.0, SAVED_PHOTO_DIMENSION.toDouble() / maxOf(decoded.width, decoded.height))
        val normalized = BufferedImage(maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), BufferedImage.TYPE_INT_ARGB)
        try {
            val graphics = normalized.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(decoded, 0, 0, normalized.width, normalized.height, null)
            } finally {
                graphics.dispose()
            }
            val encoded = ByteArrayOutputStream()
            MemoryCacheImageOutputStream(encoded).use { output ->
                check(ImageIO.write(normalized, "png", output)) { "PNG image encoder unavailable" }
            }
            return encoded.toByteArray().also {
                if (it.size > MAX_PHOTO_BYTES) throw PhotoUploadRejected(HttpStatusCode.PayloadTooLarge, "Photo must be smaller than 5 MiB after processing.")
            }
        } finally {
            normalized.flush()
        }
    } finally {
        decoded.flush()
    }
}

fun Route.userRoutes() {
    val userService = UserService()
    
    authenticate("auth-jwt") {
        route("/api/users") {
                        post("/change-password") {
                try {
                    val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@post call.respond(io.ktor.http.HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = java.util.UUID.fromString(userIdStr)
                    
                    val request = call.receive<com.attendance.shared.dto.ChangePasswordRequest>()
                    AuthService.changePassword(userId, request)
                    call.respond(io.ktor.http.HttpStatusCode.OK, com.attendance.server.plugins.ErrorResponse("SUCCESS", "Password changed successfully"))
                } catch (e: SecurityException) {
                    call.respond(io.ktor.http.HttpStatusCode.Forbidden, com.attendance.server.plugins.ErrorResponse("FORBIDDEN", publicError(e)))
                } catch (e: Exception) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, com.attendance.server.plugins.ErrorResponse("BAD_REQUEST", publicError(e)))
                }
            }

            get("/me") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = UUID.fromString(userIdStr)
                    
                    val profile = userService.getProfile(userId)
                    call.respond(HttpStatusCode.OK, profile)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, publicError(e))
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
                    call.respond(HttpStatusCode.InternalServerError, publicError(e))
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
                        call.respond(io.ktor.http.HttpStatusCode.OK, FaceResponse(embedding ?: ""))
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.NotFound, "User not found")
                    }
                } catch (e: Exception) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, "Failed")
                }
            }
            post("/me/photo") {
                var savedFile: java.nio.file.Path? = null
                var photoCommitted = false
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    val userId = UUID.fromString(userIdStr)
                    val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_PHOTO_BYTES + 65_536L) {
                        throw PhotoUploadRejected(HttpStatusCode.PayloadTooLarge, "Photo must be no larger than 5 MiB.")
                    }
                    val multipart = call.receiveMultipart()
                    var photoBytes: ByteArray? = null
                    var partCount = 0
                    multipart.forEachPart { part ->
                        try {
                            if (++partCount > 4) throw PhotoUploadRejected(HttpStatusCode.BadRequest, "Upload exactly one photo.")
                            when (part) {
                                is PartData.FileItem -> {
                                    if (photoBytes != null) throw PhotoUploadRejected(HttpStatusCode.BadRequest, "Upload exactly one photo.")
                                    photoBytes = withContext(Dispatchers.IO) {
                                        part.streamProvider().use { it.readNBytes(MAX_PHOTO_BYTES + 1) }
                                    }.also {
                                        if (it.size > MAX_PHOTO_BYTES) throw PhotoUploadRejected(HttpStatusCode.PayloadTooLarge, "Photo must be no larger than 5 MiB.")
                                        if (it.isEmpty()) throw PhotoUploadRejected(HttpStatusCode.BadRequest, "The uploaded photo is empty.")
                                    }
                                }
                                is PartData.FormItem -> if (part.value.length > 4096) throw PhotoUploadRejected(HttpStatusCode.BadRequest, "Unexpected upload form data.")
                                else -> throw PhotoUploadRejected(HttpStatusCode.BadRequest, "Unsupported upload part.")
                            }
                        } finally {
                            part.dispose()
                        }
                    }
                    val bytes = photoBytes ?: throw PhotoUploadRejected(HttpStatusCode.BadRequest, "No file uploaded")
                    val fileName = "${UUID.randomUUID()}.png"
                    withContext(Dispatchers.IO) {
                        val normalized = normalizeProfilePhoto(bytes)
                        val uploadDir = java.nio.file.Path.of("uploads").toAbsolutePath().normalize()
                        Files.createDirectories(uploadDir)
                        val destination = uploadDir.resolve(fileName)
                        Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                            savedFile = destination
                            output.write(normalized)
                        }
                    }
                    val updatedProfile = userService.updatePhoto(userId, "/uploads/$fileName")
                    photoCommitted = true
                    call.respond(HttpStatusCode.OK, updatedProfile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PhotoUploadRejected) {
                    call.respond(e.status, e.message ?: "Invalid photo")
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, publicError(e))
                } finally {
                    if (!photoCommitted) savedFile?.let { runCatching { Files.deleteIfExists(it) } }
                }
            }
            
            get("/me/devices") {
                call.respond(HttpStatusCode.NotImplemented, "Get devices - Phase 2")
            }
        }
    }
}








