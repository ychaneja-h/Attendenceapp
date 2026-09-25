package com.attendance.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.attendance.server.config.AuthConfig
import com.attendance.server.models.Users
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Application.configureSecurity() {
    val jwtSecret = AuthConfig.secret
    val jwtIssuer = AuthConfig.issuer
    val jwtAudience = AuthConfig.audience

    install(Authentication) {
        jwt {
            authHeader { call -> attendanceBearerHeader(call.request.headers.getAll(HttpHeaders.Authorization)) }
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                activePrincipal(credential)
            }
        }
        jwt("auth-jwt") {
            authHeader { call -> attendanceBearerHeader(call.request.headers.getAll(HttpHeaders.Authorization)) }
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                activePrincipal(credential)
            }
        }
    }
}

/** Older clients sent the same bearer twice; never choose between different credentials. */
internal fun attendanceBearerHeader(values: List<String>?): HttpAuthHeader.Single? {
    val headers = values?.flatMap { it.split(',') } ?: return null
    if (headers.isEmpty()) return null
    val tokens = headers.map { header ->
        Regex("^Bearer[ \\t]+([^\\s,]+)$", RegexOption.IGNORE_CASE)
            .matchEntire(header.trim())?.groupValues?.get(1) ?: return null
    }.distinct()
    return tokens.singleOrNull()?.let { HttpAuthHeader.Single("Bearer", it) }
}

private fun activePrincipal(credential: JWTCredential): JWTPrincipal? {
    val id = runCatching { UUID.fromString(credential.payload.getClaim("userId").asString()) }.getOrNull() ?: return null
    return transaction {
        val user = Users.selectAll().where { Users.id eq id }.singleOrNull() ?: return@transaction null
        if (TokenPolicy.accepts(credential.payload.getClaim("role").asString(), user[Users.role],
            user[Users.accountStatus], user[Users.registrationStatus],
            credential.payload.getClaim("authVersion").asInt() ?: 0, user[Users.authVersion])) JWTPrincipal(credential.payload)
        else null
    }
}

internal object TokenPolicy {
    fun accepts(tokenRole: String?, role: String, accountStatus: String, registrationStatus: String,
                tokenVersion: Int, accountVersion: Int): Boolean =
        tokenRole == role && role in setOf("STUDENT", "TEACHER", "HOD", "ADMIN") &&
            accountStatus == "ACTIVE" && registrationStatus == "ACTIVE" && tokenVersion == accountVersion
}
