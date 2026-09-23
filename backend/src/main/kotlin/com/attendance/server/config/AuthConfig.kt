package com.attendance.server.config

object AuthConfig {
    val secret: String by lazy { requireSecret(System.getenv("JWT_SECRET")) }
    val issuer: String = System.getenv("JWT_ISSUER") ?: "attendance-server"
    val audience: String = System.getenv("JWT_AUDIENCE") ?: "attendance-app"

    internal fun requireSecret(value: String?): String {
        require(value != null && value.isNotBlank() && value.length >= 32 && value != "dev-secret-change-in-production") {
            "JWT_SECRET must be explicitly configured with at least 32 random characters"
        }
        return value
    }
}
