package com.attendance.server.services

import com.attendance.shared.dto.*
import org.junit.Assert.*
import org.junit.Test

class PlanATimeResolverTest {
    private val event = PlanAEvent("e", PlanAEventKind.ENTRY_FACE, "class", "student", PlanATimeEvidence(999_999, 10_000, "boot"))
    private val anchor = PlanAClockAnchor("student", "boot", 20_000, 1_000_000, true)

    @Test fun `later confirmed anchor retrospectively resolves an offline event`() {
        val resolved = PlanATimeResolver.resolve(event, listOf(anchor))!!
        assertEquals(984_999, resolved.earliestMs)
        assertEquals(995_001, resolved.latestMs)
    }
    @Test fun `earlier anchor resolves later same boot event`() {
        val resolved = PlanATimeResolver.resolve(event.copy(time = event.time.copy(elapsedRealtimeMs = 30_000)), listOf(anchor))!!
        assertEquals(1_004_999, resolved.earliestMs)
        assertEquals(1_015_001, resolved.latestMs)
    }
    @Test fun `unconfirmed slow reference or reboot or another actor is not trusted`() {
        assertNull(PlanATimeResolver.resolve(event, listOf(anchor.copy(confirmed = false))))
        assertNull(PlanATimeResolver.resolve(event, listOf(anchor.copy(bootId = "new-boot"))))
        assertNull(PlanATimeResolver.resolve(event, listOf(anchor.copy(actorId = "other"))))
    }
    @Test fun `references beyond maximum age cannot invent exact times`() {
        assertNull(PlanATimeResolver.resolve(event, listOf(anchor.copy(elapsedRealtimeMs = event.time.elapsedRealtimeMs + PlanATimeResolver.MAX_ANCHOR_DISTANCE_MS + 1))))
    }
    @Test fun `upload days later retains same boot timing with bounded drift`() {
        val delay = 3L * 24 * 60 * 60 * 1_000
        val resolved = PlanATimeResolver.resolve(event, listOf(anchor.copy(
            elapsedRealtimeMs = event.time.elapsedRealtimeMs + delay, serverTimeMs = 1_000_000 + delay)))!!
        assertEquals(1_000_000 - 5_000 - delay / 10_000, resolved.earliestMs)
        assertEquals(1_000_000 + 5_000 + delay / 10_000, resolved.latestMs)
    }
    @Test fun `inconsistent reference ranges remain uncertain`() {
        assertNull(PlanATimeResolver.resolve(event, listOf(anchor, anchor.copy(serverTimeMs = anchor.serverTimeMs + 120_000))))
    }
    @Test fun `changing wall clock cannot change resolved elapsed evidence`() {
        assertEquals(PlanATimeResolver.resolve(event, listOf(anchor)), PlanATimeResolver.resolve(event.copy(time = event.time.copy(wallTimeMs = 1)), listOf(anchor)))
    }
}
