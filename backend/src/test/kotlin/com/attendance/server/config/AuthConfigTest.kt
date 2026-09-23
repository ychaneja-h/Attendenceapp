package com.attendance.server.config

import org.junit.Assert.*
import org.junit.Test

class AuthConfigTest {
    @Test fun `missing blank short and historical development secrets fail closed`() {
        listOf(null, "", " ".repeat(64), "x".repeat(31), "dev-secret-change-in-production").forEach { secret ->
            assertThrows(IllegalArgumentException::class.java) { AuthConfig.requireSecret(secret) }
        }
    }

    @Test fun `an explicitly configured long secret is preserved exactly`() {
        val secret = "test-only-secret-with-32-or-more-characters"
        assertEquals(secret, AuthConfig.requireSecret(secret))
    }
}
