package com.attendance.server.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** Separate append-only tables leave legacy attendance and exports intact. */
object PlanAEvents : Table("plan_a_events") {
    val id = varchar("id", 128)
    val actorId = uuid("actor_id").references(Users.id)
    val classId = uuid("class_id").references(Classes.id).index()
    val kind = varchar("kind", 40).default("").index()
    val sessionId = varchar("session_id", 64).nullable().index()
    val attemptId = varchar("attempt_id", 64).nullable().index()
    val payload = text("payload")
    val receivedAt = timestamp("received_at")
    override val primaryKey = PrimaryKey(id)
}

object PlanATimeAnchors : Table("plan_a_time_anchors") {
    val id = uuid("id")
    val actorId = uuid("actor_id").references(Users.id).index()
    val bootId = varchar("boot_id", 128)
    val elapsedRealtimeMs = long("elapsed_realtime_ms")
    val serverTimeMs = long("server_time_ms")
    val confirmed = bool("confirmed").default(false)
    override val primaryKey = PrimaryKey(id)
}
