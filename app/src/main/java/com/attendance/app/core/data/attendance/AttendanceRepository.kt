package com.attendance.app.core.data.attendance

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import androidx.work.*
import com.attendance.app.auth.data.TokenManager
import com.attendance.app.core.data.local.room.*
import com.attendance.app.core.data.sync.AttendanceSyncWorker
import com.attendance.app.core.network.Constants
import com.attendance.shared.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The phone is the durable source for evidence; the server is the source for reconciled results. */
class AttendanceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val httpClient: HttpClient
) {
    private val dao = database.planAAttendanceDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val syncMutex = Mutex()
    private val account = MutableStateFlow(tokenManager.getUserId())
    private val syncMessage = MutableStateFlow<String?>(null)
    private val serviceMessage = MutableStateFlow<String?>(null)
    private val tick = MutableStateFlow(0L)
    private val _state = MutableStateFlow(AttendanceState())
    private var initialization: Deferred<Unit>? = null
    val state: StateFlow<AttendanceState> = _state.asStateFlow()

    val bootId: String by lazy {
        val prefs = context.getSharedPreferences("plan_a_device", Context.MODE_PRIVATE)
        val installation = prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).commit()
        }
        val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        // BOOT_COUNT is available from our minimum API 24; kernel boot ID is a fallback.
        val boot = count?.toString() ?: runCatching {
            java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
        }.getOrElse { "unverified-${android.os.Process.myPid()}" }
        "$installation:$boot"
    }

    init {
        val records = combine(dao.observeEvents(), dao.observeState(), dao.observeSnapshots(),
            database.classDao().getAllClasses(), database.attendanceDao().getAllRecords()) {
                events, localState, snapshots, classes, legacy ->
            AttendanceInputs(events, localState, snapshots, classes, legacy)
        }
        val data = combine(records, database.studentDao().observeAllStudents()) { inputs, students -> inputs.copy(students = students) }
        scope.launch {
            combine(data, account, syncMessage, serviceMessage, tick) { inputs, userId, sync, service, _ ->
                if (userId == null) AttendanceState() else reduce(inputs, userId, sync, service)
            }.catch { syncMessage.value = "Unable to read saved attendance: ${it.message}" }
                .collect { _state.value = it }
        }
        scope.launch {
            while (isActive) { delay(15_000); tick.value++ }
        }
    }

    fun refresh() { account.value = tokenManager.getUserId(); tick.value++; requestSync() }

    fun requestSync() {
        if (tokenManager.getUserId() == null) return
        account.value = tokenManager.getUserId()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "plan-a-attendance-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<AttendanceSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        )
    }

    fun scheduleBackgroundSync() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "plan-a-attendance-refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AttendanceSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }

    fun reportServiceProblem(role: String, message: String?) {
        serviceMessage.value = message?.let { "$role: $it" }
    }

    suspend fun activeTeacherSession(): LocalTeacherSession? = readState(key("teacher"))
    suspend fun activeStudentAttempt(): LocalStudentAttempt? = readState(key("student"))
    suspend fun pendingFaceAction(): PendingFaceAction? = readState(key("face"))

    suspend fun setPendingFaceAction(classId: String, checkOut: Boolean) {
        putState(key("face"), PendingFaceAction(classId, checkOut))
    }

    suspend fun startTeacherSession(classId: String, className: String): LocalTeacherSession = writeMutex.withLock {
        val actor = requireActor()
        activeTeacherSession()?.let {
            require(it.classId == classId) { "End your current session before starting another class." }
            return@withLock it
        }
        val roster = database.studentDao().studentsForClass(classId)
            .filter { it.status.uppercase() !in setOf("REJECTED", "REMOVED", "PENDING") }
            .map { PlanARosterStudent(it.id, it.studentName, it.collegeId) }
        require(roster.isNotEmpty()) { "Open this class online once to save its approved student roster before starting offline." }
        val time = timeEvidence()
        val session = LocalTeacherSession(UUID.randomUUID().toString(), classId, className, actor,
            time.wallTimeMs, time.elapsedRealtimeMs, time.bootId, roster)
        database.withTransaction {
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.SESSION_START,
                classId, actor, time, sessionId = session.sessionId, roster = roster))
            putState(key("teacher", actor), session)
        }
        refresh()
        session
    }

    suspend fun endTeacherSession() = writeMutex.withLock {
        val session = activeTeacherSession() ?: return@withLock
        database.withTransaction {
            val prior = events(session.actorId).filter { it.sessionId == session.sessionId }
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.SESSION_STOP,
                session.classId, session.actorId, timeEvidence(), sessionId = session.sessionId,
                expectedEventIds = prior.map { it.id }))
            dao.deleteState(key("teacher", session.actorId))
        }
        refresh()
    }

    suspend fun recordTeacherReception(studentHash: ByteArray, attemptId: String, sequence: Int, rssi: Int, expectedSessionId: String? = null) = writeMutex.withLock {
        val session = activeTeacherSession() ?: return@withLock
        if (expectedSessionId != null && expectedSessionId != session.sessionId) return@withLock
        if (sequence !in 0..65535 || !attemptId.matches(Regex("[0-9a-f]{24}"))) return@withLock
        val matches = session.roster.filter { hash4(it.studentId).contentEquals(studentHash) }
        if (matches.size != 1) {
            reportServiceProblem("Teacher", "Unrecognized or ambiguous Bluetooth identity; refresh the class roster.")
            return@withLock
        }
        // Retransmissions of a 10-second burst cannot create extra checks.
        append(PlanAEvent("${session.sessionId}:$attemptId:$sequence", PlanAEventKind.CHECK_RECEIVED,
            session.classId, session.actorId, timeEvidence(), sessionId = session.sessionId,
            attemptId = attemptId, studentId = matches.single().studentId, sequence = sequence))
        // Deliberately no Internet dependency or WorkManager enqueue per radio advertisement.
    }

    suspend fun recordEntryFace(classId: String, verifiedAt: Long, verifiedElapsedMs: Long): LocalStudentAttempt = writeMutex.withLock {
        val student = requireActor()
        events(student).firstOrNull { it.kind == PlanAEventKind.ENTRY_FACE && it.classId == classId &&
            it.time.wallTimeMs == verifiedAt && it.time.elapsedRealtimeMs == verifiedElapsedMs
        }?.let { saved ->
            return@withLock activeStudentAttempt()?.takeIf { it.attemptId == saved.attemptId }
                ?: LocalStudentAttempt(requireNotNull(saved.attemptId), classId, student, verifiedAt,
                    verifiedElapsedMs, saved.time.bootId)
        }
        activeStudentAttempt()?.let {
            require(it.classId == classId) { "You are already checked into another class." }
            return@withLock it
        }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        val attempt = LocalStudentAttempt(nonce, classId, student, verifiedAt, verifiedElapsedMs, bootId)
        database.withTransaction {
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.ENTRY_FACE, classId,
                student, timeEvidence(verifiedAt, verifiedElapsedMs), attemptId = nonce, studentId = student))
            putState(key("student", student), attempt)
            dao.deleteState(key("face", student))
        }
        refresh()
        attempt
    }

    suspend fun recordStudentBroadcast(attemptId: String, sequence: Int) = writeMutex.withLock {
        val attempt = activeStudentAttempt() ?: return@withLock
        if (attempt.attemptId != attemptId || sequence !in 0..65535 || sequence <= attempt.lastBroadcastSequence) return@withLock
        val time = timeEvidence()
        database.withTransaction {
            append(PlanAEvent("$attemptId:sent:$sequence", PlanAEventKind.BROADCAST_SENT,
                attempt.classId, attempt.studentId, time, attemptId = attemptId,
                studentId = attempt.studentId, sequence = sequence))
            putState(key("student", attempt.studentId), attempt.copy(lastBroadcastSequence = sequence,
                lastBroadcastAtMs = time.wallTimeMs, broadcastCount = attempt.broadcastCount + 1))
        }
    }

    suspend fun recordExitFace(classId: String, verifiedAt: Long, verifiedElapsedMs: Long) = writeMutex.withLock {
        if (events(requireActor()).any { it.kind == PlanAEventKind.EXIT_FACE && it.classId == classId &&
            it.time.wallTimeMs == verifiedAt && it.time.elapsedRealtimeMs == verifiedElapsedMs
        }) return@withLock
        val attempt = activeStudentAttempt() ?: error("There is no active attendance attempt to leave.")
        require(attempt.classId == classId) { "Face checkout does not match your active class." }
        database.withTransaction {
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.EXIT_FACE, classId,
                attempt.studentId, timeEvidence(verifiedAt, verifiedElapsedMs), attemptId = attempt.attemptId,
                studentId = attempt.studentId))
            closeAttempt(attempt, null)
        }
        refresh()
    }

    suspend fun interruptStudentAttempt(reason: String) = writeMutex.withLock {
        val attempt = activeStudentAttempt() ?: return@withLock
        database.withTransaction { closeAttempt(attempt, reason) }
        refresh()
    }

    private suspend fun closeAttempt(attempt: LocalStudentAttempt, reason: String?) {
        val prior = events(attempt.studentId).filter { it.attemptId == attempt.attemptId }
        append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.ATTEMPT_CLOSED,
            attempt.classId, attempt.studentId, timeEvidence(), attemptId = attempt.attemptId,
            studentId = attempt.studentId, reason = reason, expectedEventIds = prior.map { it.id }))
        dao.deleteState(key("student", attempt.studentId))
        dao.deleteState(key("face", attempt.studentId))
    }

    /** Called on foreground launch; boot changes and explicit OS stops cannot be normal checkouts. */
    suspend fun recoverAfterInterruption(userRequestedStop: Boolean = false) {
        account.value = tokenManager.getUserId()
        val attempt = activeStudentAttempt()
        if (attempt != null && (attempt.bootId != bootId || userRequestedStop)) {
            interruptStudentAttempt("Face not scanned at leaving; attendance was interrupted.")
        }
        val session = activeTeacherSession()
        if (session != null && session.bootId != bootId) {
            reportServiceProblem("Teacher", "The phone restarted. End the interrupted session and review its checks.")
        }
        refresh()
    }

    fun initialize() {
        if (initialization != null) return
        scheduleBackgroundSync()
        initialization = scope.async {
            val actor = tokenManager.getUserId()
            if (actor != null && dao.state("legacyOwner") == null) putState("legacyOwner", actor)
            val attempt = activeStudentAttempt()
            val explicitStop = if (android.os.Build.VERSION.SDK_INT >= 30 && attempt != null) {
                runCatching {
                    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    manager.getHistoricalProcessExitReasons(null, 0, 1).any {
                        it.reason == android.app.ApplicationExitInfo.REASON_USER_REQUESTED && it.timestamp >= attempt.startedAtMs
                    }
                }.getOrDefault(false)
            } else false
            recoverAfterInterruption(explicitStop)
        }
    }

    /** Foreground recovery must finish before a service resumes saved attendance. */
    suspend fun awaitInitialization() { initialization?.await() }

    suspend fun overrideStudent(sessionId: String, studentId: String, status: String, reason: String = "") = writeMutex.withLock {
        require(status in setOf("PRESENT", "ABSENT")) { "Choose Present or Absent." }
        val session = state.value.sessions.find { it.sessionId == sessionId } ?: error("Session not found.")
        require(!session.legacy) { "Legacy records do not have a reliable session identity." }
        val actor = requireActor()
        val revision = maxOf((readState<Long>(key("revision", actor)) ?: 0L) + 1, System.currentTimeMillis())
        database.withTransaction {
            putState(key("revision", actor), revision)
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.OVERRIDE, session.classId,
                actor, timeEvidence(), sessionId = sessionId, studentId = studentId,
                status = status, reason = reason, revision = revision))
        }
        refresh()
    }

    suspend fun deleteSession(sessionId: String) = writeMutex.withLock {
        val session = state.value.sessions.find { it.sessionId == sessionId } ?: return@withLock
        require(activeTeacherSession()?.sessionId != sessionId) { "End this session before deleting its history." }
        val actor = requireActor()
        if (session.legacy) {
            // Hide only this legacy history group. Keep original records available for migration/recovery.
            putState(key("deleted:$sessionId", actor), true)
        } else {
            append(PlanAEvent(UUID.randomUUID().toString(), PlanAEventKind.DELETE_SESSION,
                session.classId, actor, timeEvidence(), sessionId = sessionId))
        }
        refresh()
    }

    /** Returns false on transient failure. Accepted IDs alone are acknowledged; rejected evidence is retained. */
    suspend fun synchronize(): Boolean = syncMutex.withLock {
        val actor = tokenManager.getUserId() ?: return@withLock true
        val token = tokenManager.getToken() ?: return@withLock true
        account.value = actor
        syncMessage.value = "Syncing saved attendance…"
        try {
            obtainTimeAnchor(actor, token)
            var rejected = false
            while (true) {
                val pending = dao.pendingEvents(actor)
                val response = httpClient.post("${Constants.BASE_URL}/attendance/plan-a/sync") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(PlanASyncRequest(pending.map { json.decodeFromString<PlanAEvent>(it.payload) }))
                }
                if (!response.status.isSuccess()) {
                    syncMessage.value = "Saved on this phone. Server sync unavailable (${response.status.value})."
                    return@withLock false
                }
                val result: PlanASyncResponse = response.body()
                val sentIds = pending.map { it.id }.toSet()
                database.withTransaction {
                    result.acknowledgements.filter { it.id in sentIds }.forEach { ack ->
                        if (ack.accepted) dao.acknowledge(ack.id) else {
                            rejected = true
                            dao.reject(ack.id, ack.message ?: "Server rejected this evidence; teacher review required.")
                        }
                    }
                    dao.putSnapshots(result.sessions.map { PlanASnapshotEntity(actor, it.sessionId, json.encodeToString(it)) })
                    result.deletedSessionIds.forEach { putState(key("deleted:$it", actor), true) }
                    result.deletedAttemptIds.forEach { putState(key("deleted-attempt:$it", actor), true) }
                    if (result.deletedSessionIds.isNotEmpty()) {
                        dao.snapshotsForSessions(actor, result.deletedSessionIds).forEach { saved ->
                            json.decodeFromString<PlanASessionSnapshot>(saved.payload).students
                                .filter { it.studentId == actor }.flatMap { it.attemptIds }.forEach {
                                    putState(key("deleted-attempt:$it", actor), true)
                                }
                        }
                        dao.deleteSnapshots(actor, result.deletedSessionIds)
                    }
                }
                val acknowledgedIds = result.acknowledgements.map { it.id }.toSet()
                if (sentIds.any { it !in acknowledgedIds }) {
                    syncMessage.value = "Saved evidence is awaiting server acknowledgement."
                    return@withLock false
                }
                if (dao.pendingEvents(actor).isEmpty()) break
            }
            syncMessage.value = if (rejected) "Some evidence needs teacher review; rejected records were retained." else "Attendance updated from server."
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            syncMessage.value = "Saved on this phone. Waiting for internet/server connection."
            false
        }
    }

    private suspend fun obtainTimeAnchor(actor: String, token: String) {
        // Failure to obtain an anchor never prevents evidence upload; timing then remains under review.
        try {
            val before = SystemClock.elapsedRealtime()
            val response = httpClient.post("${Constants.BASE_URL}/attendance/plan-a/time-anchor") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PlanATimeAnchorRequest(bootId, before))
            }
            val roundTrip = SystemClock.elapsedRealtime() - before
            if (!response.status.isSuccess() || roundTrip > 5_000) return
            val anchor: PlanATimeAnchorResponse = response.body()
            if (anchor.bootId != bootId || anchor.elapsedRealtimeMs != before) return
            val confirmed = httpClient.post("${Constants.BASE_URL}/attendance/plan-a/time-anchor/confirm") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PlanATimeAnchorConfirmRequest(anchor.anchorId, roundTrip))
            }
            if (confirmed.status.isSuccess()) putState(key("anchor", actor), anchor)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Evidence remains valid for later reconciliation / explicit timing review. */ }
    }

    private suspend fun timeEvidence(wall: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime()): PlanATimeEvidence {
        val anchor = readState<PlanATimeAnchorResponse>(key("anchor"))
        return PlanATimeEvidence(wall, elapsed, bootId, anchor?.takeIf {
            it.bootId == bootId && kotlin.math.abs(elapsed - it.elapsedRealtimeMs) <= it.maxAgeMs
        }?.anchorId)
    }

    private fun requireActor(): String = tokenManager.getUserId() ?: error("Sign in before taking attendance.")
    private fun key(name: String, actor: String = tokenManager.getUserId().orEmpty()) = "$actor:$name"
    private suspend inline fun <reified T> readState(key: String): T? = dao.state(key)?.let {
        runCatching { json.decodeFromString<T>(it.payload) }.getOrNull()
    }
    private suspend inline fun <reified T> putState(key: String, value: T) = dao.putState(PlanAStateEntity(key, json.encodeToString(value)))
    private suspend fun events(actor: String): List<PlanAEvent> = dao.eventsForActor(actor).map { json.decodeFromString(it.payload) }
    private suspend fun append(event: PlanAEvent) = dao.insertEvent(PlanAEventEntity(event.id, event.actorId,
        event.classId, event.sessionId, event.attemptId, event.kind.name, event.time.wallTimeMs, json.encodeToString(event)))

    private fun hash4(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).copyOf(4)

    private data class AttendanceInputs(
        val events: List<PlanAEventEntity>, val local: List<PlanAStateEntity>,
        val snapshots: List<PlanASnapshotEntity>, val classes: List<ClassEntity>,
        val legacy: List<AttendanceRecordEntity>, val students: List<StudentEntity> = emptyList()
    )

    private fun reduce(inputs: AttendanceInputs, actor: String, sync: String?, service: String?): AttendanceState {
        val local = inputs.local.associate { it.key to it.payload }
        fun <T> decodeState(name: String, decoder: (String) -> T): T? = local[key(name, actor)]?.let { runCatching { decoder(it) }.getOrNull() }
        val activeTeacher = decodeState("teacher") { json.decodeFromString<LocalTeacherSession>(it) }
        val activeStudent = decodeState("student") { json.decodeFromString<LocalStudentAttempt>(it) }
        val ownEntities = inputs.events.filter { it.actorId == actor }
        val unsyncedIds = ownEntities.filter { it.syncStatus != "SYNCED" }.map { it.id }.toSet()
        val ownEvents = ownEntities.mapNotNull { runCatching { json.decodeFromString<PlanAEvent>(it.payload) }.getOrNull() }
        val snapshots = inputs.snapshots.filter { it.ownerId == actor }.mapNotNull {
            runCatching { json.decodeFromString<PlanASessionSnapshot>(it.payload) }.getOrNull()
        }
        val deleted = ownEvents.filter { it.kind == PlanAEventKind.DELETE_SESSION }.mapNotNull { it.sessionId }.toSet()
        val classNames = inputs.classes.associate { it.id to it.name }
        val starts = ownEvents.filter { it.kind == PlanAEventKind.SESSION_START && it.sessionId != null }
        val sessions = (starts.mapNotNull { it.sessionId } + snapshots.map { it.sessionId }).distinct().mapNotNull { sessionId ->
            if (sessionId in deleted || local[key("deleted:$sessionId", actor)] == "true") return@mapNotNull null
            val all = ownEvents.filter { it.sessionId == sessionId }
            val start = starts.find { it.sessionId == sessionId }
            val stop = all.lastOrNull { it.kind == PlanAEventKind.SESSION_STOP }
            val server = snapshots.find { it.sessionId == sessionId }
            val pending = ownEntities.any { it.sessionId == sessionId && it.syncStatus != "SYNCED" && it.kind != "OVERRIDE" }
            val anyPending = ownEntities.any { it.sessionId == sessionId && it.syncStatus != "SYNCED" }
            val classId = start?.classId ?: server?.classId ?: return@mapNotNull null
            val endElapsed = stop?.time?.elapsedRealtimeMs ?: SystemClock.elapsedRealtime()
            val localTotal = if (start != null && (stop?.time?.bootId ?: bootId) == start.time.bootId)
                ((endElapsed - start.time.elapsedRealtimeMs).coerceAtLeast(0) / CHECK_INTERVAL_MS).toInt() else 0
            val total = if (activeTeacher?.sessionId == sessionId) localTotal else if (server != null && !pending) server.totalChecks else localTotal
            val roster = start?.roster ?: server?.students?.map { PlanARosterStudent(it.studentId, it.name, it.collegeId) }.orEmpty()
            val rows = roster.map { student ->
                val serverStudent = server?.students?.find { it.studentId == student.studentId }
                val observations = all.filter { it.kind == PlanAEventKind.CHECK_RECEIVED && it.studentId == student.studentId }
                val localRounds = observations.filter { (it.sequence ?: 0) > 0 && start != null && it.time.bootId == start.time.bootId }
                    .map { ((it.time.elapsedRealtimeMs - start!!.time.elapsedRealtimeMs) / CHECK_INTERVAL_MS).toInt() }
                    .filter { it in 1..total }.distinct().size
                // Once uploaded, the server may know a newer decision from another teacher device.
                val override = all.filter { it.kind == PlanAEventKind.OVERRIDE && it.studentId == student.studentId &&
                    (serverStudent == null || it.id in unsyncedIds) }.maxByOrNull { it.revision }
                val manualStatus = override?.status ?: serverStudent?.overrideStatus
                val manualReason = override?.reason ?: serverStudent?.overrideReason
                val useServer = serverStudent != null && !pending
                AttendanceStudentRow(student.studentId, student.name,
                    manualStatus ?: if (useServer) serverStudent!!.status else if (observations.isNotEmpty()) "PRESENT" else "ABSENT",
                    if (useServer) serverStudent!!.successfulChecks else localRounds,
                    total,
                    if (manualStatus != null) listOf("Teacher marked ${manualStatus.lowercase()}${manualReason?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
                    else if (useServer) serverStudent!!.reasons else listOf(if (observations.isNotEmpty()) "Provisional — waiting for student evidence and server reconciliation." else "No Bluetooth reception recorded."),
                    if (manualStatus != null) false else if (useServer) serverStudent!!.provisional else true,
                    if (useServer) serverStudent!!.entryFaceStatus else "PENDING_SYNC",
                    if (useServer) serverStudent!!.exitFaceStatus else "PENDING_SYNC",
                    manualStatus,
                    (observations.mapNotNull { it.attemptId } + serverStudent?.attemptIds.orEmpty()).distinct(),
                    observations.minOfOrNull { it.time.wallTimeMs }, observations.maxOfOrNull { it.time.wallTimeMs })
            }
            SessionHistoryItem(sessionId, classId, classNames[classId] ?: server?.className ?: "Class",
                start?.time?.wallTimeMs ?: server!!.startedAtMs, stop?.time?.wallTimeMs ?: server?.stoppedAtMs,
                total, rows, anyPending || server == null || rows.any { it.provisional },
                if (stop != null) "ENDED" else server?.status ?: "ACTIVE")
        }.toMutableList()
        // v1 grouped records cannot be safely reinterpreted as new protocol sessions.
        // Preserve them as explicitly labelled, read-only legacy history instead of deleting at midnight.
        val legacyOwner = local["legacyOwner"]?.let { runCatching { json.decodeFromString<String>(it) }.getOrNull() }
        if (legacyOwner == actor) {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
            inputs.legacy.groupBy { it.classId to dateFormat.format(java.util.Date(it.timestamp)) }.forEach { (group, records) ->
                val sessionId = "legacy:${group.first}:${group.second}"
                if (local[key("deleted:$sessionId", actor)] != "true") {
                    val rows = records.groupBy { it.studentId }.map { (studentId, evidence) ->
                        val last = evidence.maxBy { it.timestamp }
                        AttendanceStudentRow(studentId,
                            inputs.students.find { it.classId == group.first && it.id == studentId }?.studentName ?: studentId,
                            if (last.isPresent) "PRESENT" else "ABSENT", 0, 0,
                            listOf("Legacy record: original per-session checks and face evidence are unavailable."), false,
                            "LEGACY", "LEGACY", firstReceivedAtMs = evidence.minOf { it.timestamp }, lastReceivedAtMs = evidence.maxOf { it.timestamp })
                    }
                    sessions.add(SessionHistoryItem(sessionId, group.first, classNames[group.first] ?: "Legacy class",
                        records.minOf { it.timestamp }, records.maxOf { it.timestamp }, 0, rows, false, "LEGACY", true))
                }
            }
        }
        val studentResults = sessions.mapNotNull { session ->
            session.students.find { it.studentId == actor }?.let { row ->
                StudentClassResult(session.classId, session.sessionId, session.startedAtMs, row.status,
                    row.provisional, row.successfulChecks, row.totalChecks, row.reasons, row.entryFaceStatus, row.exitFaceStatus)
            }
        }.toMutableList()
        // An offline entry is shown immediately but never pretends to be a teacher-confirmed check.
        ownEvents.filter { it.kind == PlanAEventKind.ENTRY_FACE }.forEach { entry ->
            if (local[key("deleted-attempt:${entry.attemptId}", actor)] == "true") return@forEach
            val mapped = sessions.any { session -> session.students.any { entry.attemptId in it.attemptIds } }
            if (!mapped) {
                val attemptEvents = ownEvents.filter { it.attemptId == entry.attemptId }
                val closed = attemptEvents.lastOrNull { it.kind == PlanAEventKind.ATTEMPT_CLOSED }
                val exit = attemptEvents.any { it.kind == PlanAEventKind.EXIT_FACE }
                studentResults.add(StudentClassResult(entry.classId, "attempt:${entry.attemptId}", entry.time.wallTimeMs,
                    if (closed != null && !exit) "UNDER_REVIEW" else "PRESENT", true, 0, 0,
                    listOf(if (closed != null && !exit) "Face not scanned at leaving." else "Provisional entry; broadcasts are unconfirmed until the teacher's data syncs."),
                    "RECORDED_OFFLINE", if (exit) "RECORDED_OFFLINE" else "PENDING"))
            }
        }
        val rejection = ownEntities.firstOrNull { it.syncStatus == "REJECTED" }?.rejectionReason
        return AttendanceState(activeTeacher, activeStudent, sessions.sortedByDescending { it.startedAtMs }, studentResults.sortedByDescending { it.startedAtMs },
            rejection?.let { "Evidence needs review: $it" } ?: sync, service)
    }

    companion object { const val CHECK_INTERVAL_MS = 300_000L }
}
