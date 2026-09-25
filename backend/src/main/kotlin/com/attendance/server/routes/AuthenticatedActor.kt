package com.attendance.server.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

internal fun ApplicationCall.actorId(): UUID =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()?.let(UUID::fromString)
        ?: throw SecurityException("Authentication required")

internal fun publicError(error: Throwable): String = when (error) {
    is com.attendance.server.services.AuthRequestException -> error.message ?: "Check your account details."
    is SecurityException -> error.message ?: "Access denied"
    is IllegalArgumentException -> "Invalid request"
    else -> "The request could not be completed"
}

internal fun ApplicationCall.requireClassOwner(classId: String) {
    val actor = actorId()
    org.jetbrains.exposed.sql.transactions.transaction {
        com.attendance.server.services.AccessControl.requireClassOwner(actor, UUID.fromString(classId))
    }
}
