package com.attendance.server.plugins

import org.junit.Assert.*
import org.junit.Test

class TokenPolicyTest {
    @Test fun `valid active accounts with a current token are accepted for supported roles`() {
        listOf("STUDENT", "TEACHER", "HOD", "ADMIN").forEach { role ->
            assertTrue(TokenPolicy.accepts(role, role, "ACTIVE", "ACTIVE", 4, 4))
        }
    }

    @Test fun `password or device change revokes prior tokens while new login succeeds`() {
        assertFalse(TokenPolicy.accepts("STUDENT", "STUDENT", "ACTIVE", "ACTIVE", 0, 1))
        assertTrue(TokenPolicy.accepts("STUDENT", "STUDENT", "ACTIVE", "ACTIVE", 1, 1))
        assertFalse(TokenPolicy.accepts("STUDENT", "STUDENT", "ACTIVE", "ACTIVE", 2, 1))
    }

    @Test fun `role changes missing roles and unsupported roles invalidate signed tokens`() {
        assertFalse(TokenPolicy.accepts("ADMIN", "STUDENT", "ACTIVE", "ACTIVE", 0, 0))
        assertFalse(TokenPolicy.accepts(null, "STUDENT", "ACTIVE", "ACTIVE", 0, 0))
        assertFalse(TokenPolicy.accepts("UNKNOWN", "UNKNOWN", "ACTIVE", "ACTIVE", 0, 0))
    }

    @Test fun `suspended pending or otherwise inactive accounts cannot reuse signed tokens`() {
        listOf("PENDING", "SUSPENDED", "REMOVED", "", "active").forEach { status ->
            assertFalse(TokenPolicy.accepts("STUDENT", "STUDENT", status, "ACTIVE", 0, 0))
            assertFalse(TokenPolicy.accepts("STUDENT", "STUDENT", "ACTIVE", status, 0, 0))
        }
    }
}
