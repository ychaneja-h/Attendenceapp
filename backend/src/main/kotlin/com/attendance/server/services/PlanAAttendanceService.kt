package com.attendance.server.services

import com.attendance.server.models.*
import com.attendance.shared.dto.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID


class PlanAAttendanceService {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val teacherKinds = setOf(PlanAEventKind.SESSION_START, PlanAEventKind.SESSION_STOP, PlanAEventKind.CHECK_RECEIVED, PlanAEventKind.OVERRIDE, PlanAEventKind.DELETE_SESSION)
    private val studentKinds = setOf(PlanAEventKind.ENTRY_FACE, PlanAEventKind.EXIT_FACE, PlanAEventKind.BROADCAST_SENT, PlanAEventKind.ATTEMPT_CLOSED)

    fun createTimeAnchor(actorId: String, request: PlanATimeAnchorRequest): PlanATimeAnchorResponse = transaction {
        val actor = UUID.fromString(actorId)
        require(request.bootId.length in 1..128 && request.elapsedRealtimeMs >= 0) { "Invalid device time evidence" }
        require(Users.selectAll().where { Users.id eq actor }.any()) { "Unknown user" }
        val response = PlanATimeAnchorResponse(UUID.randomUUID().toString(), request.bootId, request.elapsedRealtimeMs, System.currentTimeMillis())
        PlanATimeAnchors.insert {
            it[id] = UUID.fromString(response.anchorId)
            it[this.actorId] = actor
            it[bootId] = response.bootId
            it[elapsedRealtimeMs] = response.elapsedRealtimeMs
            it[serverTimeMs] = response.serverTimeMs
        }
        response
    }

    fun sync(actorId: String, request: PlanASyncRequest): PlanASyncResponse {
        require(request.events.size <= 500) { "Upload at most 500 events per batch" }
        val acknowledgements = request.events.map { event ->
            try {
                transaction {
                    validate(actorId, event)
                    val encoded = json.encodeToString(event)
                    val existing = PlanAEvents.selectAll().where { PlanAEvents.id eq event.id }.firstOrNull()
                    if (existing != null) {
                        require(existing[PlanAEvents.actorId].toString() == actorId && json.decodeFromString<PlanAEvent>(existing[PlanAEvents.payload]) == event) { "Event ID already exists with different evidence" }
                    } else {
                        PlanAEvents.insertIgnore {
                            it[id] = event.id
                            it[this.actorId] = UUID.fromString(actorId)
                            it[classId] = UUID.fromString(event.classId)
                            it[kind] = event.kind.name
                            it[sessionId] = event.sessionId
                            it[attemptId] = event.attemptId
                            it[payload] = encoded
                            it[receivedAt] = Instant.now()
                        }
                        val stored = PlanAEvents.selectAll().where { PlanAEvents.id eq event.id }.single()
                        require(json.decodeFromString<PlanAEvent>(stored[PlanAEvents.payload]) == event) { "Event ID conflict" }
                    }
                }
                PlanAEventAck(event.id, true)
            } catch (e: IllegalArgumentException) {
                PlanAEventAck(event.id, false, e.message ?: "Invalid attendance evidence")
            }
        }
        val view = snapshots(actorId)
        return PlanASyncResponse(acknowledgements, view.sessions, view.deletedSessionIds, System.currentTimeMillis(), view.deletedAttemptIds)
    }

    fun confirmTimeAnchor(actorId: String, request: PlanATimeAnchorConfirmRequest) = transaction {
        require(request.roundTripMs in 0..5_000) { "Time reference took too long; retry" }
        val anchor = UUID.fromString(request.anchorId)
        val actor = UUID.fromString(actorId)
        val row = PlanATimeAnchors.selectAll().where { (PlanATimeAnchors.id eq anchor) and (PlanATimeAnchors.actorId eq actor) }.firstOrNull()
            ?: throw IllegalArgumentException("Unknown time reference")
        require(System.currentTimeMillis() - row[PlanATimeAnchors.serverTimeMs] <= 60_000) { "Time reference confirmation expired" }
        PlanATimeAnchors.update({ PlanATimeAnchors.id eq anchor }) { it[confirmed] = true }
    }

    fun sessions(actorId: String, classId: String?): List<PlanASessionSnapshot> = snapshots(actorId, classId).sessions

    private fun validate(actorId: String, event: PlanAEvent) {
        require(event.actorId == actorId) { "Attendance evidence must belong to the authenticated user" }
        require(event.id.length in 1..128 && event.classId.isNotBlank()) { "Invalid event identifier" }
        require(event.time.elapsedRealtimeMs >= 0 && event.time.wallTimeMs > 0 && event.time.bootId.length in 1..128) { "Invalid event time" }
        require(event.attemptId?.matches(Regex("[0-9a-fA-F]{24}")) != false) { "Attempt ID must be a 12-byte hexadecimal nonce" }
        require(event.sessionId?.let { it.length in 1..64 } != false) { "Invalid session identifier" }
        require(event.reason?.let { it.length <= 2000 } != false) { "Reason too long" }
        require(event.expectedEventIds.size <= 100_000 && event.expectedEventIds.all { it.length in 1..128 }) { "Invalid evidence manifest" }
        val actor = UUID.fromString(actorId)
        val cid = UUID.fromString(event.classId)
        val user = Users.selectAll().where { Users.id eq actor }.firstOrNull() ?: throw IllegalArgumentException("Unknown user")
        val clazz = Classes.selectAll().where { Classes.id eq cid }.firstOrNull() ?: throw IllegalArgumentException("Unknown class")
        val teacher = clazz[Classes.teacherId] == actor && user[Users.role] in setOf("TEACHER", "HOD", "ADMIN")
        val members = ClassMembers.selectAll().where { ClassMembers.classId eq cid }.map { it[ClassMembers.studentId].toString() }.toSet()
        if (event.kind in teacherKinds) {
            require(teacher) { "Only the class owner may submit teacher evidence" }
            require(event.sessionId != null) { "Teacher evidence requires an exact session ID" }
            // Stop, receipt and tombstone batches may arrive before the session start.
            // Reserve ownership from the first event so out-of-order uploads cannot alias classes.
            val previousEvent = PlanAEvents.selectAll().where { PlanAEvents.sessionId eq event.sessionId }.firstOrNull()
            require(previousEvent == null || previousEvent[PlanAEvents.classId] == cid && previousEvent[PlanAEvents.actorId] == actor) { "Session belongs to another class" }
            val previousStart = PlanAEvents.selectAll().where { (PlanAEvents.sessionId eq event.sessionId) and (PlanAEvents.kind eq PlanAEventKind.SESSION_START.name) }
                .firstOrNull()?.let { json.decodeFromString<PlanAEvent>(it[PlanAEvents.payload]) }
            require(previousStart == null || previousStart.classId == event.classId && previousStart.actorId == actorId) { "Session belongs to another class" }
            if (event.kind == PlanAEventKind.SESSION_START) {
                require(previousStart == null || previousStart.id == event.id) { "A session can have only one start event" }
                require(event.roster.size <= 2000 && event.roster.map { it.studentId }.distinct().size == event.roster.size) { "Invalid roster" }
                require(event.roster.all { it.studentId in members && it.name.length in 1..200 && it.collegeId.length <= 50 }) { "Roster contains a student outside this class" }
            }
            if (event.kind == PlanAEventKind.CHECK_RECEIVED || event.kind == PlanAEventKind.OVERRIDE) {
                require(event.studentId != null && (event.studentId in members || previousStart?.roster?.any { it.studentId == event.studentId } == true)) { "Student is not in the class roster" }
            }
            if (event.kind == PlanAEventKind.CHECK_RECEIVED) require(event.attemptId != null && event.sequence != null && event.sequence in 0..65535) { "Reception requires attempt and sequence" }
            if (event.kind == PlanAEventKind.OVERRIDE) require(event.status in setOf("PRESENT", "ABSENT") && event.revision >= 0) { "Override must be Present or Absent with a persisted revision" }
        } else {
            require(event.kind in studentKinds && user[Users.role] == "STUDENT" && actorId in members) { "Only an enrolled student may submit student evidence" }
            require(event.studentId == null || event.studentId == actorId) { "Student evidence cannot identify another student" }
            require(event.attemptId != null && event.sessionId == null) { "Student evidence uses an attempt ID; the teacher determines its session" }
            if (event.kind == PlanAEventKind.BROADCAST_SENT) require(event.sequence != null && event.sequence in 0..65535) { "Broadcast requires a valid sequence" }
        }
    }

    private data class SnapshotView(val sessions: List<PlanASessionSnapshot>, val deletedSessionIds: List<String>, val deletedAttemptIds: List<String>)

    private fun snapshots(actorId: String, classId: String? = null): SnapshotView = transaction {
        val actor = UUID.fromString(actorId)
        val owned = Classes.selectAll().where { Classes.teacherId eq actor }.map { it[Classes.id] }.toSet()
        val joined = ClassMembers.selectAll().where { ClassMembers.studentId eq actor }.map { it[ClassMembers.classId] }.toSet()
        val allowed = owned + joined
        val selected = if (classId == null) allowed else setOf(UUID.fromString(classId)).also { require(it.all { id -> id in allowed }) { "Class access denied" } }
        if (selected.isEmpty()) return@transaction SnapshotView(emptyList(), emptyList(), emptyList())
        val classes = Classes.selectAll().where { Classes.id inList selected }.associate { it[Classes.id].toString() to it[Classes.name] }
        val events = PlanAEvents.selectAll().where { PlanAEvents.classId inList selected }.map { json.decodeFromString<PlanAEvent>(it[PlanAEvents.payload]) }
        val actors = events.map { UUID.fromString(it.actorId) }.distinct()
        val anchors = if (actors.isEmpty()) emptyList() else PlanATimeAnchors.selectAll().where { PlanATimeAnchors.actorId inList actors }.map { PlanAClockAnchor(it[PlanATimeAnchors.actorId].toString(), it[PlanATimeAnchors.bootId], it[PlanATimeAnchors.elapsedRealtimeMs], it[PlanATimeAnchors.serverTimeMs], it[PlanATimeAnchors.confirmed]) }
        val times = events.mapNotNull { event -> PlanATimeResolver.resolve(event, anchors)?.let { event.id to it } }.toMap()
        val now = System.currentTimeMillis()
        val views = events.filter { it.kind == PlanAEventKind.SESSION_START }.mapNotNull { start ->
            PlanAEvaluator.evaluate(start.sessionId!!, classes[start.classId] ?: "Class", events.filter { it.classId == start.classId }, times, now)?.let { snapshot ->
                if (UUID.fromString(start.classId) in owned) snapshot else snapshot.copy(students = snapshot.students.filter { it.studentId == actorId })
            }
        }.sortedByDescending { it.startedAtMs }
        val deleted = events.filter { it.kind == PlanAEventKind.DELETE_SESSION }.mapNotNull { it.sessionId }.distinct()
        SnapshotView(views, deleted, PlanAEvaluator.deletedAttemptIds(actorId, events, times, now))
    }

}
