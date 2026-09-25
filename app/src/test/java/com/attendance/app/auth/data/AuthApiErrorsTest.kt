package com.attendance.app.auth.data

import com.attendance.shared.dto.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AuthApiErrorsTest {
    @Test fun registrationShowsActionableMessageInsteadOfMachineCode() {
        assertEquals("Use a password with at least 8 characters.", authErrorMessage(
            """{"error":"PASSWORD_TOO_SHORT","message":"Use a password with at least 8 characters."}""", 400, "fallback"))
        assertEquals("Sign in instead.", authErrorMessage(
            """{"error":"ACCOUNT_EXISTS","message":"Sign in instead."}""", 409, "fallback"))
    }

    @Test fun malformedAndServerErrorsDoNotExposeRawResponseContent() {
        assertEquals("fallback", authErrorMessage("<html>proxy diagnostic</html>", 400, "fallback"))
        assertFalse(authErrorMessage("""{"message":"database-private-detail"}""", 500, "fallback").contains("database-private-detail"))
        assertEquals("fallback", authErrorMessage("""{"error":{},"message":[]} """, 400, "fallback"))
        assertFalse(authRequestFailure(IllegalStateException("private-response-token")).message!!.contains("private-response-token"))
        assertTrue(authRequestFailure(IOException("host details")).message!!.contains("internet connection"))
    }

    @Test fun existingDeviceAndApprovalFlowsKeepTheirMachineCodes() {
        for (code in listOf("DEVICE_MISMATCH", "PENDING_APPROVAL")) {
            assertEquals(code, authErrorMessage("""{"error":"$code","message":"Action required"}""", 403, "fallback"))
        }
    }

    @Test fun clientRegistrationRejectsShortPasswordsForAllRoles() {
        val student = StudentRegisterRequest("S1", "Student", "short", "CSE", "A", 1, "2026-2027", "device")
        assertEquals("PASSWORD_TOO_SHORT", RegistrationValidation.student(student)?.code)
        assertEquals("PASSWORD_TOO_SHORT", RegistrationValidation.teacher(TeacherRegisterRequest("T1", "Teacher", "short", "CSE"))?.code)
        assertEquals("PASSWORD_TOO_SHORT", RegistrationValidation.hod(HodRegisterRequest("HOD", "H1", "short", "CSE", "INVITE"))?.code)
    }

    @Test fun normalizationNeverChangesThePasswordOrDeviceIdentity() {
        val request = StudentRegisterRequest(" S1 ", " Student ", "  pass123  ", " CSE ", " A ", 1, " 2026-2027 ", "device")
        val normalized = request.normalizedForRegistration()
        assertEquals("S1", normalized.collegeId)
        assertEquals("Student", normalized.name)
        assertEquals(request.password, normalized.password)
        assertEquals(request.deviceFingerprint, normalized.deviceFingerprint)
        assertNull(RegistrationValidation.student(normalized))
    }

    @Test fun sharedPolicyChecksStorageLimitsAndUtf8PasswordBoundaries() {
        assertNull(RegistrationValidation.password("é".repeat(36)))
        assertEquals("PASSWORD_TOO_LONG", RegistrationValidation.password("é".repeat(37))?.code)
        val valid = StudentRegisterRequest("S1", "Student", "password123", "CSE", "A", 1, "2026-2027", "device")
        assertEquals("INVALID_SECTION", RegistrationValidation.student(valid.copy(section = "A".repeat(21)))?.code)
        assertEquals("INVALID_SEMESTER", RegistrationValidation.student(valid.copy(semester = 0))?.code)
        assertEquals("MISSING_DEVICE_ID", RegistrationValidation.student(valid.copy(deviceFingerprint = " "))?.code)
    }
}
