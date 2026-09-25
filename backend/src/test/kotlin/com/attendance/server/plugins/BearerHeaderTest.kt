package com.attendance.server.plugins

import org.junit.Assert.*
import org.junit.Test

class BearerHeaderTest {
    @Test fun allowsAnIdenticalTokenFromLegacyRequestsAndProxyCombinedHeaders() {
        assertEquals("signed.jwt.value", attendanceBearerHeader(listOf("Bearer signed.jwt.value", "Bearer signed.jwt.value"))?.blob)
        assertEquals("signed.jwt.value", attendanceBearerHeader(listOf("Bearer signed.jwt.value, Bearer signed.jwt.value"))?.blob)
    }

    @Test fun rejectsConflictingOrMalformedCredentialsWithoutThrowing() {
        for (headers in listOf(null, emptyList(), listOf(""), listOf("Basic value"), listOf("Bearer"),
            listOf("Bearer first, Bearer second"), listOf("Bearer first,"), listOf("Bearer first extra"))) {
            assertNull(attendanceBearerHeader(headers))
        }
    }
}
