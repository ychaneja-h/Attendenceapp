package com.attendance.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.attendance.server.config.AuthConfig
import com.attendance.server.models.Users
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
