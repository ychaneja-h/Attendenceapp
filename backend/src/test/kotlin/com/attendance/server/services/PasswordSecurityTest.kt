package com.attendance.server.services

import at.favre.lib.crypto.bcrypt.BCrypt
import org.junit.Assert.*
import org.junit.Test

class PasswordSecurityTest {
    private val currentPassword = "correct-current-password"
    private val passwordHash = BCrypt.withDefaults().hashToString(4, currentPassword.toCharArray())

    @Test fun `password change requires the actual current password`() {
        AuthService.verifyCurrentPassword(currentPassword, passwordHash)
        listOf("", "incorrect-password", "x".repeat(73)).forEach { candidate ->
            assertThrows(SecurityException::class.java) { AuthService.verifyCurrentPassword(candidate, passwordHash) }
        }
    }

    @Test fun `new passwords must meet length and bcrypt byte limit`() {
        AuthService.validateNewPassword("abcdefgh")
        AuthService.validateNewPassword("x".repeat(72))
        AuthService.validateNewPassword("é".repeat(36))
        listOf("", "1234567", "x".repeat(73), "é".repeat(37)).forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { AuthService.validateNewPassword(candidate) }
        }
    }

    @Test fun `a bcrypt prefix cannot authorize an overlong current password`() {
        val maximumPassword = "x".repeat(72)
        val maximumHash = BCrypt.withDefaults().hashToString(4, maximumPassword.toCharArray())
        AuthService.verifyCurrentPassword(maximumPassword, maximumHash)
        assertThrows(SecurityException::class.java) {
            AuthService.verifyCurrentPassword(maximumPassword + "additional-input", maximumHash)
        }
    }
}
