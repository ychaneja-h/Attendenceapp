package com.attendance.server.services

import com.attendance.shared.dto.PlanAEvent
import kotlin.math.abs

/** Server-persisted anchor; client clock values never become authoritative by themselves. */
data class PlanAClockAnchor(
    val actorId: String,
    val bootId: String,
    val elapsedRealtimeMs: Long,
    val serverTimeMs: Long,
    val confirmed: Boolean
)

object PlanATimeResolver {
    const val MAX_ANCHOR_DISTANCE_MS = 30L * 24 * 60 * 60 * 1_000
    fun resolve(event: PlanAEvent, anchors: List<PlanAClockAnchor>): PlanAResolvedTime? {
        val ranges = anchors.filter { anchor ->
            anchor.confirmed && anchor.actorId == event.actorId && anchor.bootId == event.time.bootId &&
                abs(event.time.elapsedRealtimeMs - anchor.elapsedRealtimeMs) <= MAX_ANCHOR_DISTANCE_MS
        }.map { anchor ->
            val delta = event.time.elapsedRealtimeMs - anchor.elapsedRealtimeMs
            val center = anchor.serverTimeMs + delta
            // The app confirms only requests taking <=5s; allow 100 ppm oscillator drift as well.
            val uncertainty = 5_000L + abs(delta) / 10_000L
            PlanAResolvedTime(center - uncertainty, center + uncertainty)
        }
        if (ranges.isEmpty()) return null
        val earliest = ranges.maxOf { it.earliestMs }
        val latest = ranges.minOf { it.latestMs }
        return if (earliest <= latest) PlanAResolvedTime(earliest, latest) else null
    }
}
